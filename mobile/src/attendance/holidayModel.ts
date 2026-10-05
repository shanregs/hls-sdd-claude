import type { AttendanceCalendar, DayView, NonWorkingDate } from "../api/attendanceApi";
import { WEEKDAY_CODES, weekdayIndex } from "../formats/dates";

/** The weekly off day codes (MON..SUN) for a School: its override when the calendar has one, else the default. */
export function weeklyOffFor(calendar: AttendanceCalendar, schoolId?: string | null): string[] {
  const override = schoolId ? calendar.schoolOverrides.find((o) => o.schoolId === schoolId) : undefined;
  return override ? override.weeklyOff : calendar.defaultWeeklyOff;
}

/** The non-working dates of one month ("YYYY-MM"), in date order. */
export function holidaysOfMonth(calendar: AttendanceCalendar, month: string): NonWorkingDate[] {
  return calendar.nonWorkingDates.filter((h) => h.date.startsWith(`${month}-`)).sort(byDate);
}

/** Every non-working date of a year, in date order. */
export function holidaysOfYear(calendar: AttendanceCalendar, year: number): NonWorkingDate[] {
  return calendar.nonWorkingDates.filter((h) => h.date.startsWith(`${year}-`)).sort(byDate);
}

const byDate = (a: NonWorkingDate, b: NonWorkingDate) => a.date.localeCompare(b.date);

/** One entry per day of the month with state NON_WORKING, WEEKLY_OFF or UNMARKED (a plain working day). */
export function calendarDays(
  calendar: AttendanceCalendar,
  month: string,
  schoolId?: string | null,
): DayView[] {
  const [year, m] = month.split("-").map(Number);
  const length = new Date(Date.UTC(year, m, 0)).getUTCDate();
  const off = new Set(weeklyOffFor(calendar, schoolId));
  const holidays = new Set(holidaysOfMonth(calendar, month).map((h) => h.date));
  const days: DayView[] = [];
  for (let d = 1; d <= length; d += 1) {
    const date = `${month}-${String(d).padStart(2, "0")}`;
    let state: DayView["state"] = "UNMARKED";
    if (holidays.has(date)) state = "NON_WORKING";
    else if (off.has(WEEKDAY_CODES[weekdayIndex(date)])) state = "WEEKLY_OFF";
    days.push({ date, state, mark: null, editableBy: "NONE" });
  }
  return days;
}
