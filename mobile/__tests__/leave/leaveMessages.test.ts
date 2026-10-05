import {
  APPLY_REFUSAL_COUNT,
  CANCEL_REFUSAL_COUNT,
  DECISION_REFUSAL_COUNT,
  isStaleLeaveRefusal,
  leaveRefusalText,
} from "../../src/leave/leaveMessages";
import { APPLY_TEXTS, CANCEL_TEXTS, DECISION_TEXTS } from "../support/leaveTexts";

describe("the real refusals of spec 009", () => {
  it("has the counted number of each kind", () => {
    expect(APPLY_TEXTS).toHaveLength(APPLY_REFUSAL_COUNT);
    expect(CANCEL_TEXTS).toHaveLength(CANCEL_REFUSAL_COUNT);
    expect(DECISION_TEXTS).toHaveLength(DECISION_REFUSAL_COUNT);
  });

  it.each([...APPLY_TEXTS, ...CANCEL_TEXTS, ...DECISION_TEXTS])("gives readable wording for: %s", (reason) => {
    const text = leaveRefusalText(reason);
    expect(text.length).toBeGreaterThan(10);
    expect(text).not.toMatch(/Exception|null|undefined/);
  });

  it("rewords the days a supervisor set, with dates as DD/MM/YYYY", () => {
    expect(leaveRefusalText("days set by a supervisor: 2026-01-15, 2026-01-16")).toBe(
      "Some of these days were marked by a supervisor (15/01/2026, 16/01/2026). Correct them first, then approve.",
    );
  });

  it("rewords a locked month with the month name", () => {
    expect(leaveRefusalText("month locked: 2026-01")).toBe("Attendance for January 2026 is locked.");
    expect(leaveRefusalText("Attendance is locked for a month in this range (month locked: 2026-09).")).toBe(
      "Attendance for September 2026 is locked.",
    );
    expect(leaveRefusalText("Leave cannot be removed: month locked: 2026-01.")).toBe(
      "Leave cannot be removed because attendance for January 2026 is locked.",
    );
  });

  it("rewords a stale version and recognises it", () => {
    const stale = "This record was changed by someone else. Reload and try again.";
    expect(isStaleLeaveRefusal(stale)).toBe(true);
    expect(isStaleLeaveRefusal("This request was already approved.")).toBe(false);
    expect(leaveRefusalText(stale)).toBe("This request was changed by someone else. It now shows the latest state.");
  });

  it("shows other texts as given and a blank one as a generic message", () => {
    expect(leaveRefusalText("Give a reason.")).toBe("Give a reason.");
    expect(leaveRefusalText("")).toMatch(/not accepted/);
    expect(leaveRefusalText(undefined)).toMatch(/not accepted/);
  });
});
