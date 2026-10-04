import * as Location from "expo-location";
import { registerClientHeadersProvider, type CallInfo } from "../api/httpClient";
import {
  DEFAULT_LOCATION_REUSE_SECONDS,
  DEFAULT_LOCATION_WAIT_SECONDS,
} from "../config/constants";

/**
 * Location at the moment of an API call, and only then (spec 018 FR-018 to FR-022, research.md §6).
 *
 * The gate runs before each call to the HLS API. It never reads location on a timer, while idle, or
 * in the background, and holds no watcher: a lookup starts only because a call is about to be made.
 * A failed or missing position never fails the call; it is reported to the server as a reason
 * (permission denied, services off, no fix in time, other).
 */
export interface LocationTimings {
  /** The longest a call waits for a fresh position before going ahead without one. */
  waitSeconds: number;
  /** How long a position may be shared by calls made together (a screen loading several requests). */
  reuseSeconds: number;
}

export type MissingReason = "PERMISSION_DENIED" | "SERVICES_OFF" | "NO_FIX" | "OTHER";

export interface Reading {
  latitude: number;
  longitude: number;
  accuracyMeters: number;
  /** When the device captured the position (epoch milliseconds), sent as `ts`. */
  capturedAt: number;
  /** When this app took the reading, used only to decide how long it may be reused. */
  takenAt: number;
}

export type CaptureResult = { kind: "fix"; reading: Reading } | { kind: "none"; reason: MissingReason };

/** The public app-configuration request is not audited, so it does not ask for a position. */
const APP_CONFIG_PATH = "/api/v1/mobile/app-config";

let timings: LocationTimings = {
  waitSeconds: DEFAULT_LOCATION_WAIT_SECONDS,
  reuseSeconds: DEFAULT_LOCATION_REUSE_SECONDS,
};
let lastReading: Reading | null = null;
let inFlight: Promise<CaptureResult> | null = null;

export function setLocationTimings(next: LocationTimings): void {
  timings = next;
}

/** Forgets any shared reading, for example on sign-out, so nothing carries over to the next user. */
export function resetLocationGate(): void {
  lastReading = null;
  inFlight = null;
}

async function lookUp(): Promise<CaptureResult> {
  const permission = await Location.getForegroundPermissionsAsync();
  if (!permission.granted) {
    return { kind: "none", reason: "PERMISSION_DENIED" };
  }
  if (!(await Location.hasServicesEnabledAsync())) {
    return { kind: "none", reason: "SERVICES_OFF" };
  }

  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<"timeout">((resolve) => {
    timer = setTimeout(() => resolve("timeout"), timings.waitSeconds * 1000);
  });
  try {
    // Balanced accuracy accepts an approximate position when that is all the user allowed.
    const outcome = await Promise.race([
      Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }),
      timeout,
    ]);
    if (outcome === "timeout") {
      return { kind: "none", reason: "NO_FIX" };
    }
    const reading: Reading = {
      latitude: outcome.coords.latitude,
      longitude: outcome.coords.longitude,
      accuracyMeters: outcome.coords.accuracy ?? 0,
      capturedAt: outcome.timestamp,
      takenAt: Date.now(),
    };
    lastReading = reading;
    return { kind: "fix", reading };
  } catch {
    return { kind: "none", reason: "OTHER" };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * The position for one call: a recent shared reading if there is one, otherwise a single fresh
 * lookup that every call made at the same time waits on together.
 */
export function captureForCall(): Promise<CaptureResult> {
  if (lastReading && Date.now() - lastReading.takenAt < timings.reuseSeconds * 1000) {
    return Promise.resolve({ kind: "fix", reading: lastReading });
  }
  if (!inFlight) {
    inFlight = lookUp().finally(() => {
      inFlight = null;
    });
  }
  return inFlight;
}

/** The `X-HLS-Location` header value (contracts/mobile-api.md). */
export function formatLocationHeader(reading: Reading): string {
  return (
    `lat=${reading.latitude.toFixed(6)};lng=${reading.longitude.toFixed(6)};` +
    `acc=${reading.accuracyMeters.toFixed(1)};ts=${Math.round(reading.capturedAt)}`
  );
}

export async function locationHeadersFor(call: CallInfo): Promise<Record<string, string>> {
  if (call.path === APP_CONFIG_PATH) return {};
  const result = await captureForCall();
  return result.kind === "fix"
    ? { "X-HLS-Location": formatLocationHeader(result.reading) }
    : { "X-HLS-Location-Status": result.reason };
}

/** Sends the location (or the reason there is none) with every call to the HLS API. */
export function installLocationHeaders(): () => void {
  return registerClientHeadersProvider(locationHeadersFor);
}
