import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import * as Location from "expo-location";
import { locationHeadersFor, resetLocationGate, setLocationTimings } from "../../src/location/LocationGate";

/**
 * Spec 018 T068 (FR-018, SC-007): location is read only because a call is being made. Nothing runs
 * on a timer, in the background, or while idle, and the code has no watcher or background task.
 */
function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.(ts|tsx)$/.test(name) ? [path] : [];
  });
}

const getCurrentPosition = Location.getCurrentPositionAsync as jest.Mock;

beforeEach(() => {
  setLocationTimings({ waitSeconds: 0.1, reuseSeconds: 10 });
  resetLocationGate();
  jest.clearAllMocks();
});

describe("no location without a call", () => {
  it("takes no reading when nothing asks for one, however long it waits", async () => {
    jest.useFakeTimers();
    try {
      await locationHeadersFor({ method: "GET", path: "/api/v1/me/profile" });
      expect(getCurrentPosition).toHaveBeenCalledTimes(1);

      await jest.advanceTimersByTimeAsync(10 * 60 * 1000);

      expect(getCurrentPosition).toHaveBeenCalledTimes(1);
      expect(jest.getTimerCount()).toBe(0);
    } finally {
      jest.useRealTimers();
    }
  });

  it("leaves no timer behind after a lookup that times out", async () => {
    jest.useFakeTimers();
    try {
      jest.requireMock("expo-location").__state.neverResolves = true;
      const pending = locationHeadersFor({ method: "GET", path: "/api/v1/me/profile" });
      await jest.advanceTimersByTimeAsync(200);

      expect(await pending).toEqual({ "X-HLS-Location-Status": "NO_FIX" });
      expect(jest.getTimerCount()).toBe(0);
    } finally {
      jest.useRealTimers();
    }
  });

  it("exposes no watcher, background updates or background permission in the location module", () => {
    const mocked = Location as unknown as Record<string, unknown>;
    expect(mocked.watchPositionAsync).toBeUndefined();
    expect(mocked.startLocationUpdatesAsync).toBeUndefined();
    expect(mocked.requestBackgroundPermissionsAsync).toBeUndefined();
  });
});

describe("the source code", () => {
  const files = sourceFiles(join(__dirname, "..", "..", "src"));
  const forbidden = /watchPositionAsync|startLocationUpdatesAsync|requestBackgroundPermissionsAsync|getBackgroundPermissionsAsync|expo-task-manager|startGeofencingAsync/;

  it.each(files.map((f) => [f.replace(/.*[\\/]src[\\/]/, "src/"), f]))("%s uses no watcher or background location", (_name, file) => {
    expect(readFileSync(file, "utf8")).not.toMatch(forbidden);
  });
});
