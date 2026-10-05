import { MONTH_NAMES, formatIsoDate } from "../formats/dates";

/**
 * Plain wording for the refusals the leave server can give (spec 020 research §6). The server answers
 * 400 or 409 with `{ "reason": "..." }`; most texts are already plain and are shown as given. Only the
 * terse ones are reworded: the days a supervisor set, a locked month and a stale version.
 *
 * Counts of the real refusals, from `LeaveRequestService`, `LeaveDecisionService`, `LeaveCounter` and
 * `LeaveAttendanceImpl` (see the refusal tests, which list each text):
 *  - applying: type, dates, last before first, reason, reason too long, over 90 days, started over 30 days
 *    ago, not placed, no working day, both half days, overlap, locked month, overlap at save (13);
 *  - cancelling: already started, already cancelled, a rejected request (3);
 *  - deciding: cancelled (by the Teacher, or not), already approved, already rejected, only a pending request
 *    can be decided, only an approved one can be revoked, reason required, note too long, no working day,
 *    both half days, now overlaps, days set by a supervisor, month locked, leave cannot be removed in a
 *    locked month, changed by someone else (15).
 */
export const APPLY_REFUSAL_COUNT = 13;
export const CANCEL_REFUSAL_COUNT = 3;
export const DECISION_REFUSAL_COUNT = 15;

const STALE = /changed by someone else|reload and try again/i;

/** True when the refusal means the request changed meanwhile, so it must be reloaded. */
export function isStaleLeaveRefusal(reason: string): boolean {
  return STALE.test(reason);
}

function monthName(month: string): string {
  const [year, m] = month.split("-");
  return `${MONTH_NAMES[Number(m) - 1] ?? month} ${year}`;
}

export function leaveRefusalText(reason: string | undefined): string {
  const text = (reason ?? "").trim();
  if (text === "") return "That change was not accepted. Please try again.";
  if (STALE.test(text)) return "This request was changed by someone else. It now shows the latest state.";

  const supervisor = /days set by a supervisor:\s*([0-9,\-\s]+)/i.exec(text);
  if (supervisor) {
    const dates = supervisor[1]
      .split(",")
      .map((d) => d.trim())
      .filter(Boolean)
      .map(formatIsoDate)
      .join(", ");
    return `Some of these days were marked by a supervisor (${dates}). Correct them first, then approve.`;
  }

  const locked = /month locked:\s*(\d{4}-\d{2})/i.exec(text);
  if (locked) {
    const name = monthName(locked[1]);
    return /cannot be removed/i.test(text)
      ? `Leave cannot be removed because attendance for ${name} is locked.`
      : `Attendance for ${name} is locked.`;
  }
  return text;
}
