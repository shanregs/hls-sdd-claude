import { act } from "@testing-library/react-native";
import { AppState, type AppStateStatus } from "react-native";
import { NOTIFICATION_POLL_MS } from "../../src/config/constants";

export interface PollingControl {
  /** The bell's running 30-second timers; empty while the app is in the background. */
  timers: Map<number, () => void>;
  /** Timer ids that were cleared. */
  cleared: number[];
  /** Fires every running bell timer once, as if 30 seconds had passed. */
  tick: () => Promise<void>;
  /** Tells every AppState listener the app moved to this state. */
  appState: (state: AppStateStatus) => Promise<void>;
}

/**
 * Takes over only the bell's 30-second timer and the AppState listeners, so a test decides when a tick or a change of
 * foreground happens. Call in `beforeEach`; the spies are removed by `jest.restoreAllMocks()`.
 */
export function installPollingControl(): PollingControl {
  const handlers: ((state: AppStateStatus) => void)[] = [];
  const timers = new Map<number, () => void>();
  const cleared: number[] = [];
  let nextId = 9000;
  jest.spyOn(AppState, "addEventListener").mockImplementation(((_type: string, listener: (s: AppStateStatus) => void) => {
    handlers.push(listener);
    return { remove: () => undefined };
  }) as never);
  const realSet = global.setInterval;
  const realClear = global.clearInterval;
  jest.spyOn(global, "setInterval").mockImplementation(((fn: () => void, ms?: number) => {
    if (ms === NOTIFICATION_POLL_MS) {
      const id = nextId++;
      timers.set(id, fn);
      return id as never;
    }
    return realSet(fn, ms);
  }) as never);
  jest.spyOn(global, "clearInterval").mockImplementation(((id: never) => {
    if (timers.has(id)) {
      timers.delete(id);
      cleared.push(id);
      return;
    }
    realClear(id);
  }) as never);
  return {
    timers,
    cleared,
    tick: async () => {
      for (const fn of [...timers.values()]) await act(async () => fn());
    },
    appState: (state) => act(async () => handlers.forEach((h) => h(state))),
  };
}
