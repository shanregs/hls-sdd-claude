import * as Crypto from "expo-crypto";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppState } from "react-native";
import { ApiError, NoConnectionError } from "../api/errors";
import { logSameDayTask, logSameDayVisit, type SameDayTaskBody, type SameDayVisitBody } from "../api/sameDayApi";
import {
  discard,
  enqueue,
  flushQueue,
  type FlushResult,
  type Outcome,
  type QueuedItem,
  type QueueStorage,
} from "./sameDayQueue";
import { encryptedFileStorage } from "./sameDayStorage";

/** The phone's own calendar day, YYYY-MM-DD: the day a same-day item was logged. */
export function loggedDay(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

/** Sends one queued item. A connection problem or a server fault is tried again later; anything else is a refusal. */
export async function sendQueued(item: QueuedItem): Promise<Outcome> {
  const options = { key: item.key, capturedOn: item.capturedOn };
  try {
    if (item.kind === "TASK") await logSameDayTask(item.body as unknown as SameDayTaskBody, options);
    else await logSameDayVisit(item.body as unknown as SameDayVisitBody, options);
    return { kind: "SENT" };
  } catch (failure) {
    if (failure instanceof NoConnectionError) return { kind: "RETRY" };
    if (failure instanceof ApiError && (failure.status === undefined || failure.status >= 500 || failure.status === 429)) {
      return { kind: "RETRY" };
    }
    return { kind: "REFUSED", reason: failure instanceof Error ? failure.message : "The server refused this." };
  }
}

export type LogResult = "SENT" | "QUEUED";

export interface SameDayLog {
  /** Items waiting to be sent, and items the server refused (kept for the person to see). */
  waiting: QueuedItem[];
  /** Sends now when connected; with no connection keeps it on the phone and answers QUEUED. Other failures are thrown. */
  logTask: (body: SameDayTaskBody, label: string) => Promise<LogResult>;
  logVisit: (body: SameDayVisitBody, label: string) => Promise<LogResult>;
  flush: () => Promise<FlushResult | null>;
  discardItem: (key: string) => Promise<void>;
}

/**
 * Logging a task or a visit for today, with or without a connection (spec 040 FR-017). The waiting items are tried again
 * when the app opens, comes back to the foreground, and whenever a new item is logged.
 */
export function useSameDayLog(ownerUserId: string | null, storageOverride?: QueueStorage): SameDayLog {
  const storage = useMemo(
    () => storageOverride ?? (ownerUserId ? encryptedFileStorage(ownerUserId) : null),
    [ownerUserId, storageOverride],
  );
  const [waiting, setWaiting] = useState<QueuedItem[]>([]);
  const busy = useRef(false);

  const reload = useCallback(async () => {
    if (storage) setWaiting(await storage.read());
  }, [storage]);

  const flush = useCallback(async (): Promise<FlushResult | null> => {
    if (!storage || busy.current) return null;
    busy.current = true;
    try {
      const result = await flushQueue(storage, sendQueued);
      await reload();
      return result;
    } finally {
      busy.current = false;
    }
  }, [storage, reload]);

  useEffect(() => {
    void flush();
    const subscription = AppState.addEventListener("change", (status) => {
      if (status === "active") void flush();
    });
    return () => subscription.remove();
  }, [flush]);

  const keep = useCallback(
    async (kind: QueuedItem["kind"], body: object, label: string): Promise<LogResult> => {
      if (!storage) throw new Error("Sign in to log this.");
      await enqueue(storage, {
        key: Crypto.randomUUID(),
        kind,
        capturedOn: loggedDay(),
        body: body as Record<string, unknown>,
        label,
        queuedAt: new Date().toISOString(),
      });
      await reload();
      return "QUEUED";
    },
    [storage, reload],
  );

  const logTask = useCallback(
    async (body: SameDayTaskBody, label: string): Promise<LogResult> => {
      try {
        await logSameDayTask(body);
        return "SENT";
      } catch (failure) {
        if (failure instanceof NoConnectionError) return keep("TASK", body, label);
        throw failure;
      }
    },
    [keep],
  );

  const logVisit = useCallback(
    async (body: SameDayVisitBody, label: string): Promise<LogResult> => {
      try {
        await logSameDayVisit(body);
        return "SENT";
      } catch (failure) {
        if (failure instanceof NoConnectionError) return keep("VISIT", body, label);
        throw failure;
      }
    },
    [keep],
  );

  const discardItem = useCallback(
    async (key: string) => {
      if (storage) await discard(storage, key);
      await reload();
    },
    [storage, reload],
  );

  return { waiting, logTask, logVisit, flush, discardItem };
}
