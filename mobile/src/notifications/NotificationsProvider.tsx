import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { AppState } from "react-native";
import { useAccessModel } from "../access/AccessModelProvider";
import { NOTIFICATIONS_ROUTE } from "../access/screenRegistry";
import { getUnreadCount } from "../api/notificationsApi";
import { NOTIFICATION_POLL_MS } from "../config/constants";

interface NotificationsContextValue {
  /** True when the server's navigation offers Notifications; otherwise there is no bell and no item. */
  enabled: boolean;
  /** The unread count from the server; null before the first answer or after a failed one (the bell shows no number). */
  count: number | null;
  /** True when the Notifications item's granted actions include DELETE (spec 021 FR-009). */
  canDelete: boolean;
  /** Asks the server for the count now. */
  refreshCount: () => void;
  /** Takes the count from a list response, which carries it. The later response wins. */
  setCount: (count: number) => void;
}

const NotificationsContext = createContext<NotificationsContextValue | null>(null);

/**
 * Holds the unread count for the bell. It asks the server when the shell opens, when the app returns to the
 * foreground, every 30 seconds while the app is in the foreground, and on demand; it never asks in the background
 * (spec 021 FR-002, SC-009). Everything is in memory: signing out unmounts it, so the next user starts empty.
 * It sits above the shell's screens so the count survives the full-screen sub-screens (research §5).
 */
export function NotificationsProvider({ children }: { children: ReactNode }) {
  const { model } = useAccessModel();
  const item = useMemo(
    () => model?.navigation.flatMap((section) => section.items).find((i) => i.route === NOTIFICATIONS_ROUTE),
    [model],
  );
  const enabled = item !== undefined;
  const canDelete = item?.actions.includes("DELETE") ?? false;

  const [count, setCountState] = useState<number | null>(null);
  const alive = useRef(true);
  const inFlight = useRef(false);
  const again = useRef(false);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const refreshCount = useCallback(() => {
    if (!enabled) return;
    // One request at a time; a request asked for meanwhile (after a read) runs once the current one ends.
    if (inFlight.current) {
      again.current = true;
      return;
    }
    inFlight.current = true;
    void (async () => {
      do {
        again.current = false;
        try {
          const n = await getUnreadCount();
          if (alive.current) setCountState(n);
        } catch {
          if (alive.current) setCountState(null);
        }
      } while (again.current);
      inFlight.current = false;
    })();
  }, [enabled]);

  useEffect(() => {
    if (!enabled) return undefined;
    let timer: ReturnType<typeof setInterval> | null = null;
    const start = () => {
      if (timer === null) timer = setInterval(refreshCount, NOTIFICATION_POLL_MS);
    };
    const stop = () => {
      if (timer !== null) {
        clearInterval(timer);
        timer = null;
      }
    };
    // Start unless the app is known to be in the background (the state can be "unknown" before the first change event).
    if (AppState.currentState !== "background" && AppState.currentState !== "inactive") {
      refreshCount();
      start();
    }
    const subscription = AppState.addEventListener("change", (next) => {
      if (next === "active") {
        refreshCount();
        start();
      } else {
        stop();
      }
    });
    return () => {
      stop();
      subscription.remove();
    };
  }, [enabled, refreshCount]);

  const setCount = useCallback((n: number) => setCountState(n), []);
  const value = useMemo<NotificationsContextValue>(
    () => ({ enabled, count: enabled ? count : null, canDelete, refreshCount, setCount }),
    [enabled, count, canDelete, refreshCount, setCount],
  );
  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>;
}

export function useNotifications(): NotificationsContextValue {
  const value = useContext(NotificationsContext);
  if (!value) throw new Error("useNotifications must be used inside NotificationsProvider");
  return value;
}
