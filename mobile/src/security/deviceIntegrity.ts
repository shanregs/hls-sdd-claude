import JailMonkey from "jail-monkey";
import { registerClientHeadersProvider } from "../api/httpClient";

/** Best-effort check: a rooted or modified device is suspected, never proven (spec FR-028a). */
export function isDeviceRootedSuspected(): boolean {
  try {
    return JailMonkey.isJailBroken();
  } catch {
    return false;
  }
}

/** Sign-in calls that carry the device-integrity flag; every other call omits it. */
const SIGN_IN_PATHS = new Set(["/api/v1/auth/login", "/api/v1/auth/otp/verify"]);

/**
 * Reports "ROOTED_SUSPECTED" with sign-in calls only, when the check trips. The server stores it on
 * the Login History entry for Admin and System to see and never uses it to block anything.
 */
export function installDeviceIntegrityHeader(): () => void {
  return registerClientHeadersProvider(async ({ path }): Promise<Record<string, string>> =>
    SIGN_IN_PATHS.has(path) && isDeviceRootedSuspected() ? { "X-HLS-Device-Integrity": "ROOTED_SUSPECTED" } : {},
  );
}
