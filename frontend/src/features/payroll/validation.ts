/** Client-side checks that mirror the server's rules, so a mistake is named before the request (spec 013a). */

export const MAX_NOTE = 500;
export const MAX_AMOUNT_TEXT = "9,999,999.99";

/** The first day of the current month as yyyy-MM-dd (browser time), the earliest date a rule can start. */
export function firstOfThisMonthIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, "0");
  return `${now.getFullYear()}-${month}-01`;
}

function decimalPlaces(text: string): number {
  const dot = text.indexOf(".");
  return dot < 0 ? 0 : text.length - dot - 1;
}

/** The problem with a salary amount typed by the user, or null when it is acceptable. */
export function amountProblem(raw: string): string | null {
  const text = raw.trim();
  if (text === "") return "Enter the amount.";
  if (!/^-?\d+(\.\d+)?$/.test(text)) return "Enter the amount as a number.";
  const value = Number(text);
  if (!(value > 0)) return "The amount must be more than zero.";
  if (decimalPlaces(text) > 2) {
    return "The amount can have at most two decimal places.";
  }
  if (value > 9999999.99) {
    return `The amount can be at most ${MAX_AMOUNT_TEXT}.`;
  }
  return null;
}

export function effectiveDateProblem(
  iso: string,
  earliest: string = firstOfThisMonthIso(),
): string | null {
  if (!iso) return "Choose the effective date.";
  if (iso < earliest) {
    return "The effective date cannot be before the first day of this month.";
  }
  return null;
}

export function noteProblem(note: string): string | null {
  return note.length > MAX_NOTE
    ? `The note can be at most ${MAX_NOTE} characters.`
    : null;
}

/** The problem with a half-day fraction typed by the user, or null when it is acceptable. */
export function fractionProblem(raw: string): string | null {
  const text = raw.trim();
  if (text === "") return "Enter the half-day fraction.";
  if (!/^-?\d+(\.\d+)?$/.test(text)) {
    return "Enter the half-day fraction as a number.";
  }
  const value = Number(text);
  if (!(value > 0 && value < 1)) {
    return "The half-day fraction must be more than 0 and less than 1.";
  }
  if (decimalPlaces(text) > 3) {
    return "The half-day fraction can have at most three decimal places.";
  }
  return null;
}

/**
 * After a submit that showed field errors, moves the keyboard focus to the first invalid field so a keyboard or
 * screen-reader user lands on the first problem (its error text is the field's description).
 */
export function focusFirstInvalid(form: HTMLElement): void {
  setTimeout(() => {
    form.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, 0);
}
