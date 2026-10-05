import { MONTH_NAMES } from "../formats/dates";

/**
 * Which months a month picker may offer, and which one is the default (spec 019 FR-002, FR-006).
 * The business time zone is India Standard Time, UTC+05:30 with no daylight saving.
 */
const BUSINESS_UTC_OFFSET_MINUTES = 5 * 60 + 30;

/** "current": the current year only. "history": the current and the previous year. */
export type RangeKind = "current" | "history";

/** Year and month (1-12) of `now` in the business time zone. */
export function businessYearMonth(now: Date): { year: number; month: number } {
  const shifted = new Date(now.getTime() + BUSINESS_UTC_OFFSET_MINUTES * 60_000);
  return { year: shifted.getUTCFullYear(), month: shifted.getUTCMonth() + 1 };
}

export function monthKey(year: number, month: number): string {
  return `${year}-${String(month).padStart(2, "0")}`;
}

/** The default month, "YYYY-MM", in the business time zone. */
export function currentMonth(now: Date): string {
  const { year, month } = businessYearMonth(now);
  return monthKey(year, month);
}

/** Every month the picker may offer, oldest first. */
export function allowedMonths(kind: RangeKind, now: Date): string[] {
  const { year } = businessYearMonth(now);
  const years = kind === "history" ? [year - 1, year] : [year];
  return years.flatMap((y) => MONTH_NAMES.map((_, index) => monthKey(y, index + 1)));
}

/** The month `delta` steps from `month`, or null when that would leave the allowed range. */
export function shiftMonth(month: string, delta: number, allowed: string[]): string | null {
  const index = allowed.indexOf(month);
  if (index < 0) return null;
  return allowed[index + delta] ?? null;
}
