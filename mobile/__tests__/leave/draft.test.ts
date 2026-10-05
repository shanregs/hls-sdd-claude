import {
  appProblems,
  canCheck,
  canSubmit,
  editDraft,
  emptyDraft,
  toBody,
  type LeaveDraft,
} from "../../src/leave/draft";
import { preview } from "../support/leaveFixtures";

const filled: LeaveDraft = {
  ...emptyDraft,
  leaveTypeId: "type-casual",
  firstDate: "2026-10-12",
  lastDate: "2026-10-14",
  reason: "Family function",
};

describe("leave draft", () => {
  it("lists only the four app-side problems for an empty draft", () => {
    expect(appProblems(emptyDraft)).toEqual([
      "Choose a leave type.",
      "Choose the first and last date.",
      "Give a reason.",
    ]);
  });

  it("flags a last date before the first date", () => {
    expect(appProblems({ ...filled, lastDate: "2026-10-11" })).toEqual(["The last date cannot be before the first date."]);
    expect(canCheck({ ...filled, lastDate: "2026-10-11" })).toBe(false);
  });

  it("checks nothing else: not 30 days back, not 90 days long, not half days", () => {
    expect(appProblems({ ...filled, firstDate: "2020-01-01", lastDate: "2020-06-30" })).toEqual([]);
    expect(appProblems({ ...filled, firstDate: "2026-10-12", lastDate: "2026-10-12", halfDayStart: true, halfDayEnd: true })).toEqual([]);
  });

  it("treats a blank reason as missing", () => {
    expect(appProblems({ ...filled, reason: "   " })).toEqual(["Give a reason."]);
  });

  it("can check with a type and valid dates, even with no reason", () => {
    expect(canCheck({ ...filled, reason: "" })).toBe(true);
    expect(canCheck({ ...filled, leaveTypeId: null })).toBe(false);
    expect(canCheck({ ...filled, firstDate: null })).toBe(false);
  });

  it("clears the preview on every edit", () => {
    const previewed: LeaveDraft = { ...filled, preview: preview() };
    for (const change of [
      { leaveTypeId: "type-sick" },
      { firstDate: "2026-10-13" },
      { lastDate: "2026-10-15" },
      { halfDayStart: true },
      { halfDayEnd: true },
      { reason: "Other" },
    ]) {
      expect(editDraft(previewed, change).preview).toBeNull();
    }
  });

  it("can be submitted only with a clean preview of the current draft", () => {
    expect(canSubmit(filled)).toBe(false);
    expect(canSubmit({ ...filled, preview: preview() })).toBe(true);
    expect(canSubmit({ ...filled, preview: preview({ problems: ["Give a reason."] }) })).toBe(false);
    expect(canSubmit({ ...filled, reason: "", preview: preview() })).toBe(false);
  });

  it("builds the request body with a trimmed reason of at most 500 characters", () => {
    expect(toBody({ ...filled, reason: "  Pongal travel  ", halfDayEnd: true })).toEqual({
      leaveTypeId: "type-casual",
      firstDate: "2026-10-12",
      lastDate: "2026-10-14",
      halfDayStart: false,
      halfDayEnd: true,
      reason: "Pongal travel",
    });
    expect(toBody({ ...filled, reason: "x".repeat(600) }).reason).toHaveLength(500);
  });
});
