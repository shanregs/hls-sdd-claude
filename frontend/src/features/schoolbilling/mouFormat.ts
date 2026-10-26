import type { ApprovalState, DocumentState } from "./mouApprovalApi";

const STATE_LABELS: Record<ApprovalState, string> = {
  NOT_APPLICABLE: "Not applicable",
  DRAFT: "Draft",
  PENDING: "Pending approval",
  APPROVED: "Approved",
  APPROVED_WITHOUT_DOCUMENT: "Approved without document",
  APPROVED_BEFORE_APPROVAL_STEP: "Approved (before approval step)",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

export function approvalLabel(state: ApprovalState): string {
  return STATE_LABELS[state];
}

export function documentLabel(state: DocumentState): string {
  return state === "ATTACHED" ? "Attached" : "No document";
}

/** An instant as DD/MM/YYYY HH:mm in Indian time, the way every screen reads dates. */
export function formatInstant(iso: string | null | undefined): string {
  if (!iso) {
    return "-";
  }
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kolkata",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(new Date(iso));
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return `${get("day")}/${get("month")}/${get("year")} ${get("hour")}:${get("minute")}`;
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(0)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const ACTION_LABELS: Record<string, string> = {
  DOCUMENT_ADDED: "Document added",
  DOCUMENT_REPLACED: "Document replaced",
  DOCUMENT_REMOVED: "Document removed",
  SUBMITTED: "Submitted for approval",
  RESUBMITTED: "Submitted again",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  RESET_TO_PENDING: "Sent back for approval (document replaced)",
  TERMS_AMENDED: "Terms corrected",
  LEGACY_MARKED: "Recorded before the approval step",
};

export function historyLabel(action: string): string {
  return ACTION_LABELS[action] ?? action;
}

export const ALLOWED_EXTENSIONS = ["pdf", "jpg", "jpeg", "png"];
export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
