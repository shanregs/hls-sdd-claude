/**
 * Plain wording for the refusals the attendance server can give (spec 019 FR-004, FR-014). The server
 * answers 409 with `{ "reason": "..." }`; its text is already plain for most refusals, and is shown
 * as given. The ones below are reworded where a phone user needs to know what to do next.
 *
 * The refusals of a mark, from `MarkService` in spec 008:
 *  - a Teacher saving: future date, more than 3 days ago, locked month, no placement that date,
 *    Teacher exited, status no longer in use, day set by a Manager, changed meanwhile (8 reasons);
 *  - a Manager saving: the same minus the 3-day window and the Manager-set rule (6 reasons);
 *  - a Manager clearing: locked month, or no mark on that date.
 */
export const TEACHER_SAVE_REFUSAL_COUNT = 8;
export const MANAGER_SAVE_REFUSAL_COUNT = 6;

interface Rule {
  test: RegExp;
  text: string;
}

const RULES: Rule[] = [
  { test: /future/i, text: "You cannot mark a day in the future." },
  {
    test: /more than \d+ days ago/i,
    text: "This day is more than 3 days ago. Ask your Manager to record it.",
  },
  {
    test: /locked/i,
    text: "This month is locked, so attendance cannot change. It can change only after your Admin reopens it.",
  },
  {
    test: /needs a school placement/i,
    text: "You were not placed in a school on this date, so it cannot be marked.",
  },
  {
    test: /has exited/i,
    text: "This Teacher has left, so attendance cannot be marked on or after the exit date.",
  },
  {
    test: /no longer in use/i,
    text: "That status is no longer in use. Choose another status.",
  },
  {
    test: /set by your manager/i,
    text: "Your Manager set this day. Ask them to correct it.",
  },
  {
    test: /changed by someone else|reload and try again/i,
    text: "This day was changed by someone else while you were editing. It now shows the latest value.",
  },
  { test: /no mark on that date/i, text: "There is nothing to clear on that day." },
];

/** True when the refusal means the day was changed by someone else, so the month must be reloaded. */
export function isStaleRefusal(reason: string): boolean {
  return /changed by someone else|reload and try again/i.test(reason);
}

export function refusalText(reason: string | undefined): string {
  const text = (reason ?? "").trim();
  if (text === "") return "That change was not accepted. Please try again.";
  for (const rule of RULES) {
    if (rule.test.test(text)) return rule.text;
  }
  return text;
}
