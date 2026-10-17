import * as Location from "expo-location";
import {
  captureFresh,
  locationHeadersFor,
  resetLocationGate,
  setLocationTimings,
} from "../../src/location/LocationGate";

/**
 * Spec 033 T005: a check-in or check-out takes its own reading at the tap, never one shared with other calls,
 * and the same reading goes in the audit header of that request.
 */
const state = jest.requireMock("expo-location").__state as Record<string, unknown>;
const getCurrentPosition = Location.getCurrentPositionAsync as jest.Mock;
const requestPermission = Location.requestForegroundPermissionsAsync as jest.Mock;
const call = { method: "POST" as const, path: "/api/v1/manager-attendance/me/check-in" };

beforeEach(() => {
  setLocationTimings({ waitSeconds: 0.1, reuseSeconds: 10 });
  resetLocationGate();
  jest.clearAllMocks();
});

afterEach(() => jest.restoreAllMocks());

describe("captureFresh", () => {
  it("does not reuse a reading taken a few seconds ago", async () => {
    await locationHeadersFor(call);
    expect(getCurrentPosition).toHaveBeenCalledTimes(1);
    state.position = { latitude: 13.0, longitude: 80.2, accuracy: 9 };

    const result = await captureFresh();

    expect(getCurrentPosition).toHaveBeenCalledTimes(2);
    expect(result).toMatchObject({ kind: "fix", reading: { latitude: 13.0, longitude: 80.2, accuracyMeters: 9 } });
  });

  it("gives the header of the same request the reading it took", async () => {
    state.position = { latitude: 13.0, longitude: 80.2, accuracy: 9 };
    await captureFresh();

    const headers = await locationHeadersFor(call);

    expect(headers["X-HLS-Location"]).toMatch(/^lat=13\.000000;lng=80\.200000;acc=9\.0;/);
    expect(getCurrentPosition).toHaveBeenCalledTimes(1);
  });

  it("reports a refused permission, switched-off services, no fix in time and any other failure", async () => {
    state.permission = "denied";
    state.canAskAgain = false;
    expect(await captureFresh()).toEqual({ kind: "none", reason: "PERMISSION_DENIED" });

    state.permission = "granted";
    state.servicesEnabled = false;
    expect(await captureFresh()).toEqual({ kind: "none", reason: "SERVICES_OFF" });

    state.servicesEnabled = true;
    state.neverResolves = true;
    expect(await captureFresh()).toEqual({ kind: "none", reason: "NO_FIX" });

    state.neverResolves = false;
    state.failWith = new Error("boom");
    expect(await captureFresh()).toEqual({ kind: "none", reason: "OTHER" });
  });

  it("asks for the permission only when told to and only while it can still be asked", async () => {
    state.permission = "undetermined";
    state.onRequestPermission = "granted";

    expect(await captureFresh()).toEqual({ kind: "none", reason: "PERMISSION_DENIED" });
    expect(requestPermission).not.toHaveBeenCalled();

    expect(await captureFresh({ askIfNeeded: true })).toMatchObject({ kind: "fix" });
    expect(requestPermission).toHaveBeenCalledTimes(1);

    state.permission = "denied";
    state.canAskAgain = false;
    requestPermission.mockClear();
    expect(await captureFresh({ askIfNeeded: true })).toEqual({ kind: "none", reason: "PERMISSION_DENIED" });
    expect(requestPermission).not.toHaveBeenCalled();
  });

  it("looks up nothing until it is called", () => {
    expect(getCurrentPosition).not.toHaveBeenCalled();
  });
});
