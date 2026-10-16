import { describe, expect, it } from "vitest";
import type {
  YearMonthState,
  YearSaveResult,
  YearView,
} from "./schoolWorkingDaysApi";
import {
  applyResult,
  buildRequest,
  dirtyMonths,
  fillSameFigure,
  fillWithCalendarCounts,
  initialState,
  isDirty,
  localProblem,
  setTyped,
} from "./yearEntryState";

const YEAR = 2027;

function empty(month: number, derived = 24): YearMonthState {
  return {
    month,
    daysInMonth: new Date(YEAR, month, 0).getDate(),
    calendarDerived: derived,
    id: null,
    workingDays: null,
    version: null,
    updatedBy: null,
    updatedAt: null,
  };
}

function stored(month: number, days: number, version = 1): YearMonthState {
  return {
    ...empty(month),
    id: `id-${month}`,
    workingDays: days,
    version,
    updatedBy: "Asha R",
    updatedAt: "2027-01-04T10:15:00Z",
  };
}

function view(overrides: Record<number, YearMonthState> = {}): YearView {
  const months = Array.from(
    { length: 12 },
    (_, i) => overrides[i + 1] ?? empty(i + 1),
  );
  return {
    schoolId: "s1",
    schoolName: "Greenfield",
    zoneName: "North",
    year: YEAR,
    months,
  };
}

describe("yearEntryState", () => {
  it("starts with the stored figures in the fields and nothing changed", () => {
    const state = initialState(view({ 1: stored(1, 26), 2: stored(2, 24.5) }));
    expect(state.typed[1]).toBe("26");
    expect(state.typed[2]).toBe("24.5");
    expect(state.typed[3]).toBe("");
    expect(dirtyMonths(state)).toEqual([]);
    expect(buildRequest(state)).toEqual([]);
  });

  it("sends only the changed months, each with the version it was loaded with", () => {
    let state = initialState(view({ 1: stored(1, 26, 4) }));
    state = setTyped(state, 1, "25.5");
    state = setTyped(state, 3, "27");
    expect(dirtyMonths(state)).toEqual([1, 3]);
    expect(buildRequest(state)).toEqual([
      { month: 1, workingDays: 25.5, version: 4 },
      { month: 3, workingDays: 27, version: null },
    ]);
  });

  it("does not count a month typed back to its loaded value as changed", () => {
    let state = initialState(view({ 1: stored(1, 26) }));
    state = setTyped(state, 1, "25");
    expect(isDirty(state, 1)).toBe(true);
    state = setTyped(state, 1, "26.0");
    expect(isDirty(state, 1)).toBe(false);
  });

  it("never treats a blank field as a change, so it cannot delete a stored figure", () => {
    let state = initialState(view({ 1: stored(1, 26) }));
    state = setTyped(state, 1, "");
    state = setTyped(state, 2, "   ");
    expect(dirtyMonths(state)).toEqual([]);
    expect(buildRequest(state)).toEqual([]);
  });

  it("keeps zero as a figure", () => {
    const state = setTyped(initialState(view()), 12, "0");
    expect(buildRequest(state)).toEqual([
      { month: 12, workingDays: 0, version: null },
    ]);
  });

  it("holds back a changed month that cannot be a figure and still sends the others", () => {
    let state = initialState(view());
    state = setTyped(state, 4, "31"); // April has 30 days
    state = setTyped(state, 5, "25.3");
    state = setTyped(state, 6, "abc");
    state = setTyped(state, 7, "24");
    expect(localProblem(state, 4)).toMatch(/0 to 30/);
    expect(localProblem(state, 5)).not.toBeNull();
    expect(localProblem(state, 6)).not.toBeNull();
    expect(localProblem(state, 7)).toBeNull();
    expect(buildRequest(state).map((m) => m.month)).toEqual([7]);
  });

  describe("applying a Save answer", () => {
    const result: YearSaveResult = {
      created: 1,
      updated: 0,
      unchanged: 0,
      notSaved: 1,
      months: [
        {
          month: 2,
          status: "CREATED",
          current: stored(2, 24, 0),
        },
        {
          month: 3,
          status: "NOT_SAVED",
          reason: "STALE",
          message: "This figure was changed by someone else.",
          current: stored(3, 27, 3),
        },
      ],
    };

    it("shows stored figures and new versions, and keeps the typed text of a refused month", () => {
      let state = initialState(view({ 3: stored(3, 26, 2) }));
      state = setTyped(state, 2, "24");
      state = setTyped(state, 3, "25");
      state = applyResult(state, result);

      expect(state.loaded.months[1].version).toBe(0);
      expect(state.typed[2]).toBe("24");
      expect(state.typed[3]).toBe("25"); // the user's own entry is kept
      expect(state.loaded.months[2].workingDays).toBe(27); // the current stored figure
      expect(state.failed[3]).toEqual({
        reason: "STALE",
        message: "This figure was changed by someone else.",
      });
      expect(state.summary).toEqual({
        created: 1,
        updated: 0,
        unchanged: 0,
        notSaved: 1,
      });
      // a second Save sends only the still-dirty month, with its new version
      expect(buildRequest(state)).toEqual([
        { month: 3, workingDays: 25, version: 3 },
      ]);
    });

    it("clears a refusal when the user changes that month", () => {
      let state = initialState(view({ 3: stored(3, 26, 2) }));
      state = applyResult(setTyped(state, 3, "25"), result);
      expect(state.failed[3]).toBeDefined();
      state = setTyped(state, 3, "27");
      expect(state.failed[3]).toBeUndefined();
    });
  });

  describe("helpers", () => {
    it("fills only the empty, untyped months with the calendar counts", () => {
      let state = initialState(view({ 1: stored(1, 26), 2: stored(2, 24) }));
      state = setTyped(state, 5, "21");
      const filled = fillWithCalendarCounts(state);

      expect(filled.typed[1]).toBe("26");
      expect(filled.typed[2]).toBe("24");
      expect(filled.typed[5]).toBe("21");
      expect(filled.typed[3]).toBe("24");
      expect(filled.helperFilled).toContain(3);
      expect(filled.helperFilled).not.toContain(5);
      expect(filled.helperFilled).not.toContain(1);
      // saving is a separate step: the filled months are new figures to send
      expect(buildRequest(filled).map((m) => m.month)).toContain(3);
      expect(buildRequest(filled).map((m) => m.month)).not.toContain(1);
    });

    it("fills one figure into every empty month it is valid for and lists the others", () => {
      const state = initialState(view({ 1: stored(1, 26) }));
      const { state: filled, skipped } = fillSameFigure(state, "30");

      expect(skipped).toEqual([2]); // 30 does not fit February
      expect(filled.typed[2]).toBe("");
      expect(filled.typed[3]).toBe("30");
      expect(filled.typed[1]).toBe("26");
      expect(filled.helperFilled).not.toContain(2);
    });

    it("treats a helper-filled value as an ordinary typed value once edited", () => {
      let state = fillWithCalendarCounts(initialState(view()));
      expect(state.helperFilled).toContain(3);
      state = setTyped(state, 3, "20");
      expect(state.helperFilled).not.toContain(3);
      state = setTyped(state, 4, "");
      expect(state.helperFilled).not.toContain(4);
    });

    it("does not change the state it is given", () => {
      const state = initialState(view());
      const before = JSON.stringify(state);
      fillWithCalendarCounts(state);
      fillSameFigure(state, "20");
      expect(JSON.stringify(state)).toBe(before);
    });
  });
});
