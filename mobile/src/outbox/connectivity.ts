import NetInfo from "@react-native-community/netinfo";
import { AppState, type AppStateStatus } from "react-native";

/** Seconds between tries while claims wait (spec 036 research §7). */
export const RETRY_INTERVAL_MS = 60_000;
/** Triggers that arrive together (the app opens and the connection reports at once) make one run. */
export const COALESCE_MS = 200;

export interface Connectivity {
  /** Tell the triggers whether anything waits: the 60 second timer runs only then. */
  setWaiting: (waiting: boolean) => void;
  /** Ask for a run now (a new item, "Send now"). */
  poke: () => void;
  stop: () => void;
}

/**
 * What starts a run of the outbox engine (spec 036 research §7, §8): the app returning to the foreground, NetInfo
 * reporting a connection (a hint only: the truth is whether the request works), `poke()`, and a 60 second timer while
 * items wait. Triggers are coalesced, and the timer stops when nothing waits.
 */
export function startConnectivity(onTrigger: () => void): Connectivity {
  let pending: ReturnType<typeof setTimeout> | null = null;
  let timer: ReturnType<typeof setInterval> | null = null;
  let stopped = false;

  const poke = () => {
    if (stopped || pending) return;
    pending = setTimeout(() => {
      pending = null;
      if (!stopped) onTrigger();
    }, COALESCE_MS);
  };

  const appState = AppState.addEventListener("change", (status: AppStateStatus) => {
    if (status === "active") poke();
  });
  const unsubscribeNet = NetInfo.addEventListener((state) => {
    if (state.isConnected) poke();
  });

  return {
    poke,
    setWaiting: (waiting) => {
      if (waiting && !timer && !stopped) timer = setInterval(poke, RETRY_INTERVAL_MS);
      if (!waiting && timer) {
        clearInterval(timer);
        timer = null;
      }
    },
    stop: () => {
      stopped = true;
      if (pending) clearTimeout(pending);
      if (timer) clearInterval(timer);
      pending = null;
      timer = null;
      appState.remove();
      unsubscribeNet();
    },
  };
}
