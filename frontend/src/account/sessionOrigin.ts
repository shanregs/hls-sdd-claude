import type { SessionRow } from "./sessionsApi";

/** "04/10/2026 14:31:23" in the viewer's time zone (dates are DD/MM/YYYY across the app). */
export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/**
 * Which client started the session, in words: the Android app with its version, or a browser and
 * operating system read from the user agent ("Chrome on Windows"). Anything unrecognised falls back
 * to the raw text so nothing is hidden.
 */
export function describeOrigin(
  session: Pick<SessionRow, "clientType" | "appVersion" | "deviceDescription">,
): string {
  if (session.clientType === "ANDROID") {
    return session.appVersion
      ? `Android app ${session.appVersion}`
      : "Android app";
  }
  const ua = session.deviceDescription ?? "";
  if (!ua) return "Unknown client";
  if (/^curl\//i.test(ua)) return "curl";
  const headless = /HeadlessChrome/i.test(ua) ? " (headless)" : "";
  const browser = /Edg\//.test(ua)
    ? "Edge"
    : /OPR\/|Opera/.test(ua)
      ? "Opera"
      : /Firefox\//.test(ua)
        ? "Firefox"
        : /Chrome\/|HeadlessChrome/.test(ua)
          ? "Chrome"
          : /Safari\//.test(ua)
            ? "Safari"
            : null;
  const os = /Windows/.test(ua)
    ? "Windows"
    : /Android/.test(ua)
      ? "Android"
      : /iPhone|iPad|iOS/.test(ua)
        ? "iOS"
        : /Mac OS X|Macintosh/.test(ua)
          ? "macOS"
          : /Linux/.test(ua)
            ? "Linux"
            : null;
  if (browser && os) return `${browser}${headless} on ${os}`;
  return browser ? `${browser}${headless}` : ua;
}
