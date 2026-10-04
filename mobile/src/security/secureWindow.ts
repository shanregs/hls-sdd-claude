import { preventScreenCaptureAsync } from "expo-screen-capture";

/**
 * Marks the app window secure so the recent-apps thumbnail and screenshots are blank (spec FR-004,
 * research.md §4). Best effort: a failure leaves the app working.
 */
export async function enableSecureWindow(): Promise<void> {
  try {
    await preventScreenCaptureAsync();
  } catch {
    // Not supported on this device; nothing else to do.
  }
}
