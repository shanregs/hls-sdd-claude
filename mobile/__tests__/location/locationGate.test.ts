import * as Location from "expo-location";
import {
  captureForCall,
  formatLocationHeader,
  locationHeadersFor,
  resetLocationGate,
  setLocationTimings,
} from "../../src/location/LocationGate";

/**
 * Spec 018 T067 (FR-018 to FR-022): the position is taken only when a call is made, a missing one
 * never fails the call, calls made together share one reading, and the wait is bounded.
 */
const state = jest.requireMock("expo-location").__state as Record<string, unknown>;
const getCurrentPosition = Location.getCurrentPositionAsync as jest.Mock;
const call = { method: "GET" as const, path: "/api/v1/me/profile" };

beforeEach(() => {
  setLocationTimings({ waitSeconds: 0.1, reuseSeconds: 10 });
  resetLocationGate();
  jest.clearAllMocks();
});

afterEach(() => jest.restoreAllMocks());

describe("the header", () => {
  it("sends latitude, longitude, accuracy and capture time when a position is available", async () => {
    const headers = await locationHeadersFor(call);

    expect(Object.keys(headers)).toEqual(["X-HLS-Location"]);
    expect(headers["X-HLS-Location"]).toMatch(/^lat=12\.971599;lng=77\.594566;acc=18\.5;ts=\d{13}$/);
  });

  it("formats coordinates to six decimal places and accuracy to one", () => {
    const text = formatLocationHeader({
      latitude: 1.5,
      longitude: -2.25,
      accuracyMeters: 7,
      capturedAt: 1759581000123,
      takenAt: 0,
    });
    expect(text).toBe("lat=1.500000;lng=-2.250000;acc=7.0;ts=1759581000123");
  });

  it("asks for balanced accuracy, so an approximate-only permission still gives a position", async () => {
    await locationHeadersFor(call);

    expect(getCurrentPosition).toHaveBeenCalledWith({ accuracy: Location.Accuracy.Balanced });
  });
});

describe("when there is no position the call still goes ahead, with the reason", () => {
  it("reports PERMISSION_DENIED when the permission was denied", async () => {
    state.permission = "denied";

    expect(await locationHeadersFor(call)).toEqual({ "X-HLS-Location-Status": "PERMISSION_DENIED" });
    expect(getCurrentPosition).not.toHaveBeenCalled();
  });

  it("reports PERMISSION_DENIED when the permission has not been decided yet", async () => {
    state.permission = "undetermined";

    expect(await locationHeadersFor(call)).toEqual({ "X-HLS-Location-Status": "PERMISSION_DENIED" });
  });

  it("reports SERVICES_OFF when location is switched off on the phone", async () => {
    state.servicesEnabled = false;

    expect(await locationHeadersFor(call)).toEqual({ "X-HLS-Location-Status": "SERVICES_OFF" });
    expect(getCurrentPosition).not.toHaveBeenCalled();
  });

  it("reports NO_FIX and does not wait longer than the limit", async () => {
    state.neverResolves = true;
    const started = Date.now();

    const headers = await locationHeadersFor(call);

    expect(headers).toEqual({ "X-HLS-Location-Status": "NO_FIX" });
    expect(Date.now() - started).toBeLessThan(1000);
  });

  it("reports OTHER when the lookup fails", async () => {
    state.failWith = new Error("boom");

    expect(await locationHeadersFor(call)).toEqual({ "X-HLS-Location-Status": "OTHER" });
  });
});

describe("sharing and freshness", () => {
  it("lets calls made together share one lookup", async () => {
    state.delayMs = 20;

    const results = await Promise.all([1, 2, 3, 4, 5].map(() => locationHeadersFor(call)));

    expect(getCurrentPosition).toHaveBeenCalledTimes(1);
    expect(new Set(results.map((r) => r["X-HLS-Location"])).size).toBe(1);
  });

  it("reuses a reading made within the freshness limit", async () => {
    await locationHeadersFor(call);
    await locationHeadersFor(call);
    await locationHeadersFor(call);

    expect(getCurrentPosition).toHaveBeenCalledTimes(1);
  });

  it("takes a fresh reading once the freshness limit has passed", async () => {
    const real = Date.now();
    const now = jest.spyOn(Date, "now").mockReturnValue(real);
    await locationHeadersFor(call);

    now.mockReturnValue(real + 11_000);
    await locationHeadersFor(call);

    expect(getCurrentPosition).toHaveBeenCalledTimes(2);
  });

  it("never reuses a reading after the gate is reset, for example after sign-out", async () => {
    await locationHeadersFor(call);
    resetLocationGate();
    await locationHeadersFor(call);

    expect(getCurrentPosition).toHaveBeenCalledTimes(2);
  });

  it("does not remember a failure: the next call checks again", async () => {
    state.servicesEnabled = false;
    expect(await locationHeadersFor(call)).toEqual({ "X-HLS-Location-Status": "SERVICES_OFF" });

    state.servicesEnabled = true;

    expect((await locationHeadersFor(call))["X-HLS-Location"]).toBeDefined();
  });

  it("uses the wait and reuse values it was given by the server", async () => {
    setLocationTimings({ waitSeconds: 0.05, reuseSeconds: 0 });
    state.neverResolves = true;
    const started = Date.now();

    await locationHeadersFor(call);

    expect(Date.now() - started).toBeLessThan(500);
    state.neverResolves = false;
    await locationHeadersFor(call);
    await locationHeadersFor(call);
    // With a reuse window of 0 seconds nothing is shared: each call takes its own reading.
    expect(getCurrentPosition).toHaveBeenCalledTimes(3);
  });
});

describe("a permission changed while the app is running", () => {
  it("is followed by the very next call", async () => {
    expect((await locationHeadersFor(call))["X-HLS-Location"]).toBeDefined();

    state.permission = "denied";
    resetLocationGate();

    expect(await locationHeadersFor(call)).toEqual({ "X-HLS-Location-Status": "PERMISSION_DENIED" });
  });
});

describe("the public app-config request", () => {
  it("asks for no position at all", async () => {
    expect(await locationHeadersFor({ method: "GET", path: "/api/v1/mobile/app-config" })).toEqual({});
    expect(getCurrentPosition).not.toHaveBeenCalled();
    expect(Location.getForegroundPermissionsAsync).not.toHaveBeenCalled();
  });
});

describe("captureForCall", () => {
  it("returns the reading with the device's own capture time", async () => {
    const result = await captureForCall();

    expect(result.kind).toBe("fix");
    if (result.kind === "fix") {
      expect(result.reading.latitude).toBe(12.971599);
      expect(result.reading.capturedAt).toBeGreaterThan(0);
    }
  });
});
