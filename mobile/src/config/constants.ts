import Constants from "expo-constants";

/** Server base URL as the device sees it; set with EXPO_PUBLIC_API_BASE_URL (see .env.example). */
export const API_BASE_URL: string =
  (Constants.expoConfig?.extra?.apiBaseUrl as string | undefined) ??
  process.env.EXPO_PUBLIC_API_BASE_URL ??
  "http://10.0.2.2:8080";

/** Defaults used until, or if, GET /api/v1/mobile/app-config cannot be read (research.md §6). */
export const DEFAULT_LOCATION_WAIT_SECONDS = 4;
export const DEFAULT_LOCATION_REUSE_SECONDS = 10;

/** Refresh the access model when the app returns from the background after this long (FR-012). */
export const ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS = 5 * 60 * 1000;

/** How often the bell asks for the unread count while the app is in the foreground (spec 010 FR-008, spec 021 FR-002). */
export const NOTIFICATION_POLL_MS = 30_000;

/** Notifications per page on the list (spec 021 FR-004). */
export const NOTIFICATION_PAGE_SIZE = 25;

/** Give up on a request after this long and treat it as "no connection". */
export const REQUEST_TIMEOUT_MS = 20_000;
