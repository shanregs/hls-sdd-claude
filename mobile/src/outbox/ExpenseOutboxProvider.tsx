import NetInfo from "@react-native-community/netinfo";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { KeyUnreadableError } from "./cipher";
import { startConnectivity, type Connectivity } from "./connectivity";
import { OutboxEngine } from "./outboxEngine";
import { listItems, removeItem, saveItem as storeSave, updateItem as storeUpdate } from "./outboxStore";
import type { OutboxItem } from "./types";

/** What happened to an item the user just saved and asked to send. */
export type SendOutcome =
  | { kind: "sent" }
  /** Saved, and not sent now (no connection, a server error, or still in a backoff): it will be sent later. */
  | { kind: "waiting"; item: OutboxItem }
  | { kind: "refused"; item: OutboxItem }
  | { kind: "confirm"; item: OutboxItem };

export interface OutboxContextValue {
  userId: string;
  items: OutboxItem[];
  /** Folders that could not be opened; the user can delete them. */
  unreadable: string[];
  /** The key to the saved claims cannot be read: new items cannot be saved. */
  keyProblem: boolean;
  /** Items not yet on the server (every state), for the count and the sign-out warning. */
  unsentCount: number;
  /** Items that will be tried again by themselves. */
  waitingCount: number;
  /** The item being sent now, if any. */
  sendingId: string | null;
  loaded: boolean;
  /** Writes the item (and its photos) first, then sends it and waits for the result. */
  saveAndSend: (item: OutboxItem, billSources?: Record<string, string>) => Promise<SendOutcome>;
  /** Writes the item and leaves it for the engine ("Saved. It will be sent when you are back online."). */
  save: (item: OutboxItem, billSources?: Record<string, string>) => Promise<void>;
  update: (item: OutboxItem, billSources?: Record<string, string>) => Promise<void>;
  remove: (itemId: string) => Promise<void>;
  /** Tries every waiting item now, ignoring any backoff. */
  sendNow: () => Promise<void>;
  /** Makes a refused item waiting again. */
  resend: (itemId: string) => Promise<void>;
  /** Confirms the duplicate warning of an item and sends it. */
  confirmDuplicate: (itemId: string) => Promise<SendOutcome>;
  reload: () => Promise<void>;
}

const OutboxContext = createContext<OutboxContextValue | null>(null);

/**
 * Mounts the outbox for the signed-in user (spec 036 research §3, §11): loads that user's items only, runs the engine on
 * the triggers (mounting, foreground, a connection, a new item, Send now, a timer), and keeps nothing for anyone else.
 */
export function ExpenseOutboxProvider({ userId, children }: { userId: string; children: ReactNode }) {
  const [items, setItems] = useState<OutboxItem[]>([]);
  const [unreadable, setUnreadable] = useState<string[]>([]);
  const [keyProblem, setKeyProblem] = useState(false);
  const [sendingId, setSendingId] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const mounted = useRef(true);
  const engineRef = useRef<OutboxEngine | null>(null);
  const connectivityRef = useRef<Connectivity | null>(null);

  const reload = useCallback(async () => {
    try {
      const listed = await listItems(userId);
      if (!mounted.current) return;
      setItems(listed.items);
      setUnreadable(listed.unreadable);
      setKeyProblem(false);
    } catch (error) {
      if (mounted.current && error instanceof KeyUnreadableError) setKeyProblem(true);
    } finally {
      if (mounted.current) setLoaded(true);
    }
  }, [userId]);

  useEffect(() => {
    mounted.current = true;
    const engine = new OutboxEngine(userId, {
      onChange: () => void reload(),
      onSending: (id) => {
        if (mounted.current) setSendingId(id);
      },
    });
    engineRef.current = engine;
    const connectivity = startConnectivity(() => void engine.run());
    connectivityRef.current = connectivity;
    void reload().then(() => {
      if (mounted.current) void engine.run();
    });
    return () => {
      mounted.current = false;
      connectivity.stop();
      engineRef.current = null;
      connectivityRef.current = null;
    };
  }, [userId, reload]);

  const waitingCount = useMemo(() => items.filter((i) => i.state === "WAITING").length, [items]);
  useEffect(() => {
    connectivityRef.current?.setWaiting(waitingCount > 0);
  }, [waitingCount]);

  /** Runs the engine until this item is gone or has been tried once, and reports what became of it. */
  const sendOne = useCallback(
    async (itemId: string): Promise<SendOutcome> => {
      const net = await NetInfo.fetch();
      const engine = engineRef.current;
      if (net.isConnected === false || !engine) {
        const waiting = (await listItems(userId)).items.find((i) => i.itemId === itemId);
        return waiting ? { kind: "waiting", item: waiting } : { kind: "sent" };
      }
      for (let attempt = 0; attempt < 4; attempt += 1) {
        await engine.run({ force: true });
        const current = (await listItems(userId)).items.find((i) => i.itemId === itemId);
        if (!current) return { kind: "sent" };
        if (current.state === "REFUSED") return { kind: "refused", item: current };
        if (current.state === "NEEDS_CONFIRMATION") return { kind: "confirm", item: current };
        if (current.attempts > 0) return { kind: "waiting", item: current };
      }
      const last = (await listItems(userId)).items.find((i) => i.itemId === itemId);
      return last ? { kind: "waiting", item: last } : { kind: "sent" };
    },
    [userId],
  );

  const save = useCallback(
    async (item: OutboxItem, billSources: Record<string, string> = {}) => {
      await storeSave(item, billSources);
      await reload();
    },
    [reload],
  );

  const saveAndSend = useCallback(
    async (item: OutboxItem, billSources: Record<string, string> = {}) => {
      await storeSave(item, billSources);
      await reload();
      return sendOne(item.itemId);
    },
    [reload, sendOne],
  );

  const update = useCallback(
    async (item: OutboxItem, billSources: Record<string, string> = {}) => {
      await storeUpdate(item, billSources);
      await reload();
    },
    [reload],
  );

  const remove = useCallback(
    async (itemId: string) => {
      await removeItem(userId, itemId);
      await reload();
    },
    [reload, userId],
  );

  const sendNow = useCallback(async () => {
    await engineRef.current?.run({ force: true });
    await reload();
  }, [reload]);

  const resend = useCallback(
    async (itemId: string) => {
      const item = items.find((i) => i.itemId === itemId);
      if (!item) return;
      await storeUpdate({ ...item, state: "WAITING", refusal: null, attempts: 0, nextTryAt: null });
      await reload();
      void engineRef.current?.run({ force: true });
    },
    [items, reload],
  );

  const confirmDuplicate = useCallback(
    async (itemId: string): Promise<SendOutcome> => {
      const item = items.find((i) => i.itemId === itemId);
      if (!item) return { kind: "sent" };
      await storeUpdate({ ...item, state: "WAITING", confirmDuplicate: true, refusal: null, attempts: 0, nextTryAt: null });
      await reload();
      return sendOne(itemId);
    },
    [items, reload, sendOne],
  );

  const value = useMemo<OutboxContextValue>(
    () => ({
      userId,
      items,
      unreadable,
      keyProblem,
      unsentCount: items.length + unreadable.length,
      waitingCount,
      sendingId,
      loaded,
      saveAndSend,
      save,
      update,
      remove,
      sendNow,
      resend,
      confirmDuplicate,
      reload,
    }),
    [userId, items, unreadable, keyProblem, waitingCount, sendingId, loaded, saveAndSend, save, update, remove, sendNow, resend, confirmDuplicate, reload],
  );

  return <OutboxContext.Provider value={value}>{children}</OutboxContext.Provider>;
}

export function useExpenseOutbox(): OutboxContextValue {
  const context = useContext(OutboxContext);
  if (!context) throw new Error("useExpenseOutbox must be used inside ExpenseOutboxProvider");
  return context;
}

/** The outbox when one is mounted, otherwise null: for screens that also work without it (the sign-out guard). */
export function useOptionalExpenseOutbox(): OutboxContextValue | null {
  return useContext(OutboxContext);
}
