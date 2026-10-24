import { AppState } from "react-native";
import { COALESCE_MS, RETRY_INTERVAL_MS, startConnectivity } from "../../src/outbox/connectivity";

const netinfo = () => jest.requireMock("@react-native-community/netinfo");

let appStateHandler: ((status: string) => void) | null;
let removed: jest.Mock;

beforeEach(() => {
  jest.useFakeTimers();
  removed = jest.fn();
  appStateHandler = null;
  jest.spyOn(AppState, "addEventListener").mockImplementation(((_type: string, handler: (status: string) => void) => {
    appStateHandler = handler;
    return { remove: removed };
  }) as never);
});

afterEach(() => {
  jest.useRealTimers();
  jest.restoreAllMocks();
});

describe("connectivity triggers", () => {
  it("runs when the app returns to the foreground, but not when it goes to the background", () => {
    const run = jest.fn();
    const c = startConnectivity(run);
    appStateHandler!("background");
    jest.advanceTimersByTime(COALESCE_MS);
    expect(run).not.toHaveBeenCalled();
    appStateHandler!("active");
    jest.advanceTimersByTime(COALESCE_MS);
    expect(run).toHaveBeenCalledTimes(1);
    c.stop();
  });

  it("runs when NetInfo reports a connection, not when it reports none", () => {
    const run = jest.fn();
    const c = startConnectivity(run);
    netinfo().__emit({ isConnected: false });
    jest.advanceTimersByTime(COALESCE_MS);
    expect(run).not.toHaveBeenCalled();
    netinfo().__emit({ isConnected: true });
    jest.advanceTimersByTime(COALESCE_MS);
    expect(run).toHaveBeenCalledTimes(1);
    c.stop();
  });

  it("asks to ignore the backoff when the app returns or a connection is reported, but not for the timer", () => {
    const run = jest.fn();
    const c = startConnectivity(run);
    appStateHandler!("active");
    jest.advanceTimersByTime(COALESCE_MS);
    expect(run).toHaveBeenLastCalledWith(true);
    c.setWaiting(true);
    jest.advanceTimersByTime(RETRY_INTERVAL_MS + COALESCE_MS);
    expect(run).toHaveBeenLastCalledWith(false);
    c.stop();
  });

  it("coalesces triggers that arrive together into one run", () => {
    const run = jest.fn();
    const c = startConnectivity(run);
    appStateHandler!("active");
    netinfo().__emit({ isConnected: true });
    c.poke();
    jest.advanceTimersByTime(COALESCE_MS);
    expect(run).toHaveBeenCalledTimes(1);
    c.stop();
  });

  it("retries every 60 seconds while items wait, and stops the timer when nothing waits", () => {
    const run = jest.fn();
    const c = startConnectivity(run);
    jest.advanceTimersByTime(RETRY_INTERVAL_MS * 2);
    expect(run).not.toHaveBeenCalled();
    c.setWaiting(true);
    jest.advanceTimersByTime(RETRY_INTERVAL_MS + COALESCE_MS);
    expect(run).toHaveBeenCalledTimes(1);
    jest.advanceTimersByTime(RETRY_INTERVAL_MS);
    expect(run).toHaveBeenCalledTimes(2);
    c.setWaiting(false);
    jest.advanceTimersByTime(RETRY_INTERVAL_MS * 3);
    expect(run).toHaveBeenCalledTimes(2);
    expect(jest.getTimerCount()).toBe(0);
    c.stop();
  });

  it("stops listening and never runs after stop", () => {
    const run = jest.fn();
    const c = startConnectivity(run);
    c.poke();
    c.stop();
    jest.advanceTimersByTime(COALESCE_MS * 2);
    expect(run).not.toHaveBeenCalled();
    expect(removed).toHaveBeenCalled();
    expect(netinfo().__listenerCount()).toBe(0);
  });
});
