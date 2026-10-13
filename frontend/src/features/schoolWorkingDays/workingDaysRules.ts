/** Rules the School Working Days screen checks before sending (the server repeats them, spec 037 FR-003). */

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

/** Shown beside inline Save and in the delete confirmation (FR-028). */
export const FINALISED_NOTICE =
  "Months already finalised in payroll or attendance keep the figure they used.";

export function monthName(month: number): string {
  return MONTH_NAMES[month - 1] ?? String(month);
}

export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

/** The message to show for a typed figure, or null when it is acceptable (0 to the days in the month, half days). */
export function figureProblem(
  text: string,
  year: number,
  month: number,
): string | null {
  const days = daysInMonth(year, month);
  const range = `Enter 0 to ${days}, in half days.`;
  const trimmed = text.trim();
  if (trimmed === "" || !/^\d+(\.\d+)?$/.test(trimmed)) return range;
  const value = Number(trimmed);
  if (value < 0 || value > days || !Number.isInteger(value * 2)) return range;
  return null;
}

/** A figure as people read it: 26 or 25.5. */
export function formatFigure(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

/** DD/MM/YYYY HH:mm for an ISO timestamp (Constitution Principle IV). */
export function formatWhen(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const two = (n: number) => String(n).padStart(2, "0");
  return `${two(date.getDate())}/${two(date.getMonth() + 1)}/${date.getFullYear()} ${two(date.getHours())}:${two(date.getMinutes())}`;
}
