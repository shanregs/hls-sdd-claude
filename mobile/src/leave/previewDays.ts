import type { AttendanceCalendar } from "../api/attendanceApi";
import type { LeavePreview } from "../api/leaveApi";
import { weeklyOffFor } from "../attendance/holidayModel";
import { WEEKDAY_CODES, formatLongDate, parseIsoDate, weekdayIndex } from "../formats/dates";

export type PreviewKind = "COUNTED" | "HOLIDAY" | "WEEKLY_OFF" | "NOT_COUNTED";

export interface PreviewDayEntry {
  date: string;
  kind: PreviewKind;
  /** Whole or half day for a counted date. */
  value?: number;
  /** Short text shown in the list. */
  label: string;
  /** Spoken text: weekday, date and what the day is. */
  spoken: string;
}

const pad = (n: number) => String(n).padStart(2, "0");

/** Every date from `first` to `last` inclusive, as "YYYY-MM-DD", with no time zone involved. */
export function datesInRange(first: string, last: string): string[] {
  const a = parseIsoDate(first);
  const b = parseIsoDate(last);
  if (!a || !b) return [];
  const out: string[] = [];
  const end = Date.UTC(b.year, b.month - 1, b.day);
  for (let t = Date.UTC(a.year, a.month - 1, a.day); t <= end; t += 86_400_000) {
    const d = new Date(t);
    out.push(`${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`);
  }
  return out;
}

/**
 * One entry per date of the range. A date the server counted is "Counted"; any other date is "Holiday" or
 * "Weekly off" when the Holiday Calendar says so, and otherwise "Not counted" (for example before the
 * placement). The working-day total is the server's and is never worked out here (research §3).
 */
export function previewDayList(
  first: string,
  last: string,
  preview: LeavePreview,
  calendar: AttendanceCalendar | null,
): PreviewDayEntry[] {
  const counted = new Map(preview.days.map((d) => [d.date, d.value]));
  const holidays = new Set((calendar?.nonWorkingDates ?? []).map((h) => h.date));
  const off = new Set(calendar ? weeklyOffFor(calendar) : []);
  return datesInRange(first, last).map((date) => {
    const when = formatLongDate(date);
    const value = counted.get(date);
    if (value !== undefined) {
      const size = value === 0.5 ? "half day" : "whole day";
      return { date, kind: "COUNTED", value, label: `Counted, ${size}`, spoken: `${when}, counted, ${size}` };
    }
    if (holidays.has(date)) return { date, kind: "HOLIDAY", label: "Holiday", spoken: `${when}, holiday, not counted` };
    if (off.has(WEEKDAY_CODES[weekdayIndex(date)])) {
      return { date, kind: "WEEKLY_OFF", label: "Weekly off", spoken: `${when}, weekly off, not counted` };
    }
    return { date, kind: "NOT_COUNTED", label: "Not counted", spoken: `${when}, not counted` };
  });
}
