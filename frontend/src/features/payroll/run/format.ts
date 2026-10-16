import { formatRupees } from "../../teachers/formatters";

/** A money string from the server (`"12500.00"`) shown in rupees with Indian digit grouping. Display only. */
export function money(amount: string | null | undefined): string {
  if (amount === null || amount === undefined) return "";
  return formatRupees(Number(amount));
}

const MONTHS = [
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

/** `2026-09` as "September 2026". */
export function monthLabel(month: string): string {
  const [year, number] = month.split("-");
  const name = MONTHS[Number(number) - 1];
  return name ? `${name} ${year}` : month;
}

/** The last `count` months that have ended, newest first, as `yyyy-MM`. */
export function endedMonths(count: number, now: Date = new Date()): string[] {
  const months: string[] = [];
  for (let back = 1; back <= count; back++) {
    const d = new Date(now.getFullYear(), now.getMonth() - back, 1);
    months.push(
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`,
    );
  }
  return months;
}

/** An ISO instant as DD/MM/YYYY HH:mm in the browser's zone. */
export function dateTime(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export const MAX_REASON = 500;

/** The reason a person must give for cancelling, returning or withdrawing: required, at most 500 characters. */
export function reasonProblem(reason: string): string | null {
  const text = reason.trim();
  if (text.length === 0) return "A reason is required.";
  if (text.length > MAX_REASON)
    return `The reason can be at most ${MAX_REASON} characters.`;
  return null;
}
