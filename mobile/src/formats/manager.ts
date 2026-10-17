/** Formats for the Manager screens (spec 033). */

const BUSINESS_OFFSET_MINUTES = 5 * 60 + 30; // the business time zone, UTC+05:30
const pad = (n: number) => String(n).padStart(2, "0");

/** An instant from the server as 24-hour HH:mm in the business time zone, whatever the phone's own zone is. */
export function formatBusinessTime(instant: string): string {
  const t = Date.parse(instant);
  if (Number.isNaN(t)) return "";
  const shifted = new Date(t + BUSINESS_OFFSET_MINUTES * 60_000);
  return `${pad(shifted.getUTCHours())}:${pad(shifted.getUTCMinutes())}`;
}

/** A time of day from the server ("09:30:00" or "09:30") as HH:mm. */
export function formatClock(time: string): string {
  const match = /^(\d{1,2}):(\d{2})/.exec(time);
  return match ? `${pad(Number(match[1]))}:${match[2]}` : time;
}

/** A file size in plain words: "340 KB", "1.2 MB". */
export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** "image/jpeg" as "JPG", "image/png" as "PNG"; anything else as the part after the slash, upper case. */
export function formatImageType(contentType: string): string {
  const subtype = contentType.split("/")[1]?.split(";")[0]?.trim().toLowerCase() ?? "";
  if (subtype === "jpeg" || subtype === "jpg") return "JPG";
  return subtype ? subtype.toUpperCase() : "Image";
}

/** An instant from the server as DD/MM/YYYY HH:mm in the business time zone. */
export function formatBusinessDateTime(instant: string): string {
  const t = Date.parse(instant);
  if (Number.isNaN(t)) return "";
  const shifted = new Date(t + BUSINESS_OFFSET_MINUTES * 60_000);
  return `${pad(shifted.getUTCDate())}/${pad(shifted.getUTCMonth() + 1)}/${shifted.getUTCFullYear()} ${formatBusinessTime(instant)}`;
}
