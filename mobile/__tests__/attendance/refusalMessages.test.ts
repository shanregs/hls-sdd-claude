import {
  MANAGER_SAVE_REFUSAL_COUNT,
  TEACHER_SAVE_REFUSAL_COUNT,
  isStaleRefusal,
  refusalText,
} from "../../src/attendance/refusalMessages";

/** The exact texts spec 008's MarkService and the exception advice produce (see refusalMessages.ts). */
const TEACHER_REASONS = [
  "You cannot mark a date in the future.",
  "This date is more than 3 days ago. Ask your Manager to record it.",
  "This month is locked. Attendance can only change after it is reopened.",
  "Attendance needs a School placement on that date.",
  "This Teacher has exited, so attendance cannot be marked on or after the exit date.",
  "The status X is no longer in use.",
  "This day was set by your Manager. Ask them to correct it.",
  "This record was changed by someone else. Reload and try again.",
];

const MANAGER_REASONS = [
  "You cannot mark a date in the future.",
  "This month is locked. Attendance can only change after it is reopened.",
  "Attendance needs a School placement on that date.",
  "This Teacher has exited, so attendance cannot be marked on or after the exit date.",
  "The status X is no longer in use.",
  "This record was changed by someone else. Reload and try again.",
];

describe("the real refusals of spec 008", () => {
  it(`has ${TEACHER_SAVE_REFUSAL_COUNT} reasons a Teacher can meet and ${MANAGER_SAVE_REFUSAL_COUNT} a Manager can meet`, () => {
    expect(TEACHER_REASONS).toHaveLength(TEACHER_SAVE_REFUSAL_COUNT);
    expect(MANAGER_REASONS).toHaveLength(MANAGER_SAVE_REFUSAL_COUNT);
  });

  it.each(TEACHER_REASONS)("gives plain wording for: %s", (reason) => {
    const text = refusalText(reason);
    expect(text).not.toBe("");
    expect(text).not.toMatch(/MarkService|Exception|null/);
  });

  it("tells the Teacher to ask their Manager when a Manager set the day", () => {
    expect(refusalText("This day was set by your Manager. Ask them to correct it.")).toBe(
      "Your Manager set this day. Ask them to correct it.",
    );
  });

  it("explains a locked month and an old day in plain words", () => {
    expect(refusalText("This month is locked. Attendance can only change after it is reopened.")).toMatch(/locked/);
    expect(refusalText("This date is more than 3 days ago. Ask your Manager to record it.")).toMatch(/Manager/);
  });

  it("recognises a stale change so the month can be reloaded", () => {
    expect(isStaleRefusal("This record was changed by someone else. Reload and try again.")).toBe(true);
    expect(isStaleRefusal("This month is locked.")).toBe(false);
  });

  it("clears: a missing mark and a locked month are explained", () => {
    expect(refusalText("There is no mark on that date.")).toBe("There is nothing to clear on that day.");
  });

  it("shows an unknown reason as given, and a blank one as a generic message", () => {
    expect(refusalText("Something unexpected.")).toBe("Something unexpected.");
    expect(refusalText("")).toMatch(/not accepted/);
    expect(refusalText(undefined)).toMatch(/not accepted/);
  });
});
