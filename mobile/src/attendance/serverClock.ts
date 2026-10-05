/**
 * The server's idea of "now", used only to choose the default month (spec 019 FR-008, research §3).
 * Every rule about what may be changed comes from the server's day entries, never from this clock.
 * The offset is learned from the HTTP `Date` header of responses the app already receives; with no
 * header seen it is zero, so the phone's own clock is used for the default month only.
 */
let offsetMs = 0;

/** Remembers how far the server clock is from the phone clock, from a `Date` response header. */
export function recordServerDate(header: string | null | undefined): void {
  if (!header) return;
  const serverTime = Date.parse(header);
  if (Number.isNaN(serverTime)) return;
  offsetMs = serverTime - Date.now();
}

export function serverNow(): Date {
  return new Date(Date.now() + offsetMs);
}

export function resetServerClock(): void {
  offsetMs = 0;
}
