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

/** A signed amount with at most two decimals, not zero, within what a line can hold. */
export function amountProblem(text: string): string | null {
  const value = text.trim();
  if (value === "") return "The amount is required.";
  if (!/^-?\d+(\.\d+)?$/.test(value))
    return "Enter the amount as a number, for example 2000 or -500.50.";
  if (/\.\d{3,}$/.test(value))
    return "The amount can have at most two decimals.";
  if (Number(value) === 0) return "The amount cannot be zero.";
  if (Math.abs(Number(value)) > 9999999.99) return "The amount is too large.";
  return null;
}

export const MAX_REFERENCE = 100;

export const today = () => {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

/** The first problem with the entered details, or null; mirrors the server's rules so the message comes before the call. */
export function paymentProblem(
  paid: boolean,
  paidOn: string,
  mode: string,
  reference: string,
): string | null {
  if (!paid) return null;
  if (paidOn === "") return "The payment date is required.";
  if (paidOn > today()) return "The payment date cannot be in the future.";
  if (mode === "") return "Choose how it was paid.";
  if (reference.trim().length > MAX_REFERENCE)
    return `The reference can be at most ${MAX_REFERENCE} characters.`;
  return null;
}
