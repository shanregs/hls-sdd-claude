import type { LeaveDraftBody, LeavePreview } from "../api/leaveApi";
import { MAX_LEAVE_TEXT } from "./ReasonDialog";

/**
 * The leave request being composed on Apply Leave (spec 020 data-model "Draft"). The app checks only what
 * needs no rules and no clock; every other rule (30 days back, 90 days long, overlap, locked months,
 * placement, half days) is the server's, shown through its preview and refusals.
 */
export interface LeaveDraft {
  leaveTypeId: string | null;
  firstDate: string | null;
  lastDate: string | null;
  halfDayStart: boolean;
  halfDayEnd: boolean;
  reason: string;
  /** The last preview; cleared by every change to the fields above. */
  preview: LeavePreview | null;
}

export const emptyDraft: LeaveDraft = {
  leaveTypeId: null,
  firstDate: null,
  lastDate: null,
  halfDayStart: false,
  halfDayEnd: false,
  reason: "",
  preview: null,
};

/** Changes a field and clears the preview, so a submit can only follow a preview of the current draft. */
export function editDraft(draft: LeaveDraft, change: Partial<Omit<LeaveDraft, "preview">>): LeaveDraft {
  return { ...draft, ...change, preview: null };
}

/** The type and both dates are chosen and the last date is not before the first: a preview can be asked for. */
export function canCheck(draft: LeaveDraft): boolean {
  return Boolean(draft.leaveTypeId && draft.firstDate && draft.lastDate && draft.lastDate >= draft.firstDate);
}

/** The only checks made in the app: a missing type, date or reason, and a last date before the first. */
export function appProblems(draft: LeaveDraft): string[] {
  const problems: string[] = [];
  if (!draft.leaveTypeId) problems.push("Choose a leave type.");
  if (!draft.firstDate || !draft.lastDate) problems.push("Choose the first and last date.");
  else if (draft.lastDate < draft.firstDate) problems.push("The last date cannot be before the first date.");
  if (draft.reason.trim() === "") problems.push("Give a reason.");
  return problems;
}

/** Submit is possible after a preview of this very draft that listed no problems. */
export function canSubmit(draft: LeaveDraft): boolean {
  return appProblems(draft).length === 0 && draft.preview !== null && draft.preview.problems.length === 0;
}

/** The request body for preview and submit. Call only when `canCheck` is true. */
export function toBody(draft: LeaveDraft): LeaveDraftBody {
  return {
    leaveTypeId: draft.leaveTypeId ?? "",
    firstDate: draft.firstDate ?? "",
    lastDate: draft.lastDate ?? "",
    halfDayStart: draft.halfDayStart,
    halfDayEnd: draft.halfDayEnd,
    reason: draft.reason.trim().slice(0, MAX_LEAVE_TEXT),
  };
}
