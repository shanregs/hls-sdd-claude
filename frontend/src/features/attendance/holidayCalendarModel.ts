import type { AttendanceCalendar } from "./attendanceApi";
import type { DayKind } from "./dayStyle";
import {
  dateKey,
  daysInMonth,
  monthKey,
  WEEKDAY_CODES,
  weekdayIndex,
} from "./monthUtils";

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
];

/** What the year, the month and the PDF all draw: the organization-wide off days and holidays. */
export type CalendarFacts = Pick<
  AttendanceCalendar,
  "defaultWeeklyOff" | "nonWorkingDates"
>;

/** The kind of a date: a holiday wins over a weekly off day, which wins over a plain day (FR-025). */
export function kindOf(date: string, facts: CalendarFacts): DayKind {
  if (facts.nonWorkingDates.some((d) => d.date === date)) return "holiday";
  if (facts.defaultWeeklyOff.includes(WEEKDAY_CODES[weekdayIndex(date)])) {
    return "off";
  }
  return "plain";
}

export function holidayName(
  date: string,
  facts: CalendarFacts,
): string | undefined {
  return facts.nonWorkingDates.find((d) => d.date === date)?.description;
}

/** The holidays whose date starts with {@code prefix} ("2026" or "2026-10"), oldest first. */
export function holidaysIn(prefix: string, facts: CalendarFacts) {
  return facts.nonWorkingDates
    .filter((d) => d.date.startsWith(prefix))
    .sort((a, b) => a.date.localeCompare(b.date));
}

/** A month as Monday-first weeks of "YYYY-MM-DD" dates, with null padding before the 1st and after the last. */
export function monthWeeks(month: string): (string | null)[][] {
  const count = daysInMonth(month);
  const lead = weekdayIndex(dateKey(month, 1));
  const cells: (string | null)[] = [
    ...Array<null>(lead).fill(null),
    ...Array.from({ length: count }, (_, i) => dateKey(month, i + 1)),
  ];
  while (cells.length % 7 !== 0) cells.push(null);
  const weeks: (string | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7));
  return weeks;
}

/** The twelve "YYYY-MM" keys of a year. */
export function monthsOfYear(year: number): string[] {
  return Array.from({ length: 12 }, (_, i) => monthKey(year, i + 1));
}
