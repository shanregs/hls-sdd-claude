import type { EffectiveVisitStatus, VisitKind } from "../api/marketingApi";

const STATUS: Record<EffectiveVisitStatus, string> = {
  PLANNED: "Planned",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
  MISSED: "Missed",
};

const KIND: Record<VisitKind, string> = {
  VISIT: "Visit",
  CALL: "Call",
  PROPOSAL_MEETING: "Proposal meeting",
  FOLLOW_UP: "Follow-up",
};

/** The status the server gives a visit, in words (Missed is the server's, never worked out here). */
export function visitStatusText(status: EffectiveVisitStatus): string {
  return STATUS[status] ?? status;
}

export function visitKindText(kind: VisitKind): string {
  return KIND[kind] ?? kind;
}

/** The kinds a visit can be planned as, in the order the Manager is offered them. */
export const VISIT_KINDS: VisitKind[] = ["VISIT", "CALL", "PROPOSAL_MEETING", "FOLLOW_UP"];
