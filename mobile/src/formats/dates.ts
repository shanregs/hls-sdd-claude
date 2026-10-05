/** Dates and times as DD/MM/YYYY and 24-hour HH:mm, the HLS convention (Constitution, Additional Constraints). */
const pad = (n: number) => String(n).padStart(2, "0");

export function formatDate(value: string | Date): string {
  const d = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(d.getTime())) return "";
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
}

export function formatDateTime(value: string | Date): string {
  const d = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(d.getTime())) return "";
  return `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export const MONTH_NAMES = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
] as const;

/** Sunday-first: the week layout of every month view in the app (Sunday is column 1). */
export const WEEKDAY_LONG = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"] as const;
export const WEEKDAY_SHORT = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"] as const;
/** The three-letter weekday codes the attendance calendar uses for weekly offs, Sunday first. */
export const WEEKDAY_CODES = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"] as const;

/** "2026-10" becomes "October 2026"; anything unreadable becomes an empty string. */
export function formatMonth(month: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(month);
  if (!match) return "";
  const name = MONTH_NAMES[Number(match[2]) - 1];
  return name ? `${name} ${match[1]}` : "";
}

/** Parses "YYYY-MM-DD" as a calendar date without involving any time zone. */
export function parseIsoDate(date: string): { year: number; month: number; day: number } | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  if (!match) return null;
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
}

/** Sunday = 0 .. Saturday = 6 for a "YYYY-MM-DD" date. */
export function weekdayIndex(date: string): number {
  const parts = parseIsoDate(date);
  if (!parts) return 0;
  return new Date(Date.UTC(parts.year, parts.month - 1, parts.day)).getUTCDay();
}

/** "2026-10-06" becomes "Monday 6 October 2026". */
export function formatLongDate(date: string): string {
  const parts = parseIsoDate(date);
  if (!parts) return "";
  return `${WEEKDAY_LONG[weekdayIndex(date)]} ${parts.day} ${MONTH_NAMES[parts.month - 1]} ${parts.year}`;
}

/** "2026-10-06" becomes "06/10/2026" (DD/MM/YYYY), independent of the phone's time zone. */
export function formatIsoDate(date: string): string {
  const parts = parseIsoDate(date);
  if (!parts) return "";
  return `${pad(parts.day)}/${pad(parts.month)}/${parts.year}`;
}
