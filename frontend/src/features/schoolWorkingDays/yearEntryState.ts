import type {
  YearMonthReason,
  YearMonthRequest,
  YearMonthState,
  YearSaveResult,
  YearView,
} from "./schoolWorkingDaysApi";
import { figureProblem, formatFigure } from "./workingDaysRules";

/** A month the server did not save, kept beside the user's typed text until they change that month. */
export interface FailedMonth {
  reason: YearMonthReason;
  message: string;
}

/** The year screen's state (spec 038). Pure data: nothing here calls the server or the browser. */
export interface YearEntryState {
  loaded: YearView;
  /** The text in each month's field, by month number. */
  typed: Record<number, string>;
  /** Months whose text was filled by a helper and not yet saved or edited. */
  helperFilled: number[];
  /** Months the last Save refused, by month number. */
  failed: Record<number, FailedMonth>;
  /** What the last Save came to, for the summary line. */
  summary: Pick<
    YearSaveResult,
    "created" | "updated" | "unchanged" | "notSaved"
  > | null;
}

function textOf(month: YearMonthState): string {
  return month.workingDays === null ? "" : formatFigure(month.workingDays);
}

export function initialState(view: YearView): YearEntryState {
  const typed: Record<number, string> = {};
  for (const m of view.months) typed[m.month] = textOf(m);
  return { loaded: view, typed, helperFilled: [], failed: {}, summary: null };
}

function monthOf(state: YearEntryState, month: number): YearMonthState {
  const found = state.loaded.months.find((m) => m.month === month);
  if (!found) throw new Error(`No month ${month}`);
  return found;
}

/** The user typed in a month: that month is no longer helper-filled and its earlier refusal no longer applies. */
export function setTyped(
  state: YearEntryState,
  month: number,
  text: string,
): YearEntryState {
  const failed = { ...state.failed };
  delete failed[month];
  return {
    ...state,
    typed: { ...state.typed, [month]: text },
    helperFilled: state.helperFilled.filter((m) => m !== month),
    failed,
  };
}

/**
 * True when the text differs from what was loaded. A blank field is never a change: on a month with a stored figure it
 * means "nothing entered" and never deletes (FR-008). A month typed back to its loaded value is not a change either.
 */
export function isDirty(state: YearEntryState, month: number): boolean {
  const text = (state.typed[month] ?? "").trim();
  if (text === "") return false;
  const stored = monthOf(state, month).workingDays;
  if (stored === null) return true;
  const value = Number(text);
  return Number.isNaN(value) || value !== stored;
}

export function dirtyMonths(state: YearEntryState): number[] {
  return state.loaded.months
    .map((m) => m.month)
    .filter((month) => isDirty(state, month));
}

/** The message for a changed month whose text cannot be a figure, so it is not sent (the server repeats the rule). */
export function localProblem(
  state: YearEntryState,
  month: number,
): string | null {
  if (!isDirty(state, month)) return null;
  return figureProblem(state.typed[month], state.loaded.year, month);
}

/** The months to send: changed, and acceptable as figures. Each carries the version it was loaded with. */
export function buildRequest(state: YearEntryState): YearMonthRequest[] {
  return dirtyMonths(state)
    .filter((month) => localProblem(state, month) === null)
    .map((month) => ({
      month,
      workingDays: Number(state.typed[month].trim()),
      version: monthOf(state, month).version,
    }));
}

/**
 * Takes in a Save answer: every returned month replaces what was loaded for it. A refused month keeps the user's
 * typed text and records its reason; every other month shows its stored figure.
 */
export function applyResult(
  state: YearEntryState,
  result: YearSaveResult,
): YearEntryState {
  const byMonth = new Map(result.months.map((r) => [r.month, r]));
  const loaded: YearView = {
    ...state.loaded,
    months: state.loaded.months.map((m) => byMonth.get(m.month)?.current ?? m),
  };
  const typed = { ...state.typed };
  const failed: Record<number, FailedMonth> = {};
  for (const r of result.months) {
    if (r.status === "NOT_SAVED") {
      failed[r.month] = {
        reason: (r.reason ?? "INVALID_FIGURE") as YearMonthReason,
        message: r.message ?? "This month was not saved.",
      };
    } else {
      typed[r.month] = textOf(r.current);
    }
  }
  return {
    loaded,
    typed,
    helperFilled: state.helperFilled.filter((m) => failed[m] !== undefined),
    failed,
    summary: {
      created: result.created,
      updated: result.updated,
      unchanged: result.unchanged,
      notSaved: result.notSaved,
    },
  };
}

/** Months that have no stored figure and nothing typed: the only ones a helper may fill. */
/** True when at least one month has neither a stored figure nor typed text, so the two fill helpers have work to do. */
export function hasEmptyMonths(state: YearEntryState): boolean {
  return emptyMonths(state).length > 0;
}

function emptyMonths(state: YearEntryState): number[] {
  return state.loaded.months
    .filter(
      (m) =>
        m.workingDays === null && (state.typed[m.month] ?? "").trim() === "",
    )
    .map((m) => m.month);
}

/** "Use calendar counts": fills each empty month with its hint for the user to review. Saves nothing. */
export function fillWithCalendarCounts(state: YearEntryState): YearEntryState {
  const months = emptyMonths(state);
  const typed = { ...state.typed };
  for (const month of months) {
    typed[month] = formatFigure(monthOf(state, month).calendarDerived);
  }
  return {
    ...state,
    typed,
    helperFilled: [...new Set([...state.helperFilled, ...months])],
  };
}

/**
 * "Same figure for all empty months": fills each empty month the figure is valid for and lists the ones it is not
 * (30 in February). Saves nothing and never touches a stored figure or typed text. With `replaceAll` it fills every
 * month the figure fits, replacing stored figures and typed text too (the page offers that for future years only).
 */
export function fillSameFigure(
  state: YearEntryState,
  text: string,
  replaceAll = false,
): { state: YearEntryState; skipped: number[] } {
  const typed = { ...state.typed };
  const filled: number[] = [];
  const skipped: number[] = [];
  const months = replaceAll
    ? state.loaded.months.map((m) => m.month)
    : emptyMonths(state);
  for (const month of months) {
    if (figureProblem(text, state.loaded.year, month) === null) {
      typed[month] = text.trim();
      filled.push(month);
    } else {
      skipped.push(month);
    }
  }
  return {
    state: {
      ...state,
      typed,
      helperFilled: [...new Set([...state.helperFilled, ...filled])],
    },
    skipped,
  };
}
