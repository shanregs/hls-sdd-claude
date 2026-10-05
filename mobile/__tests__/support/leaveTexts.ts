/** The exact texts spec 009's services produce (see leaveMessages.ts). */
export const APPLY_TEXTS = [
  "Choose a leave type.",
  "Choose the first and last date.",
  "The last date cannot be before the first date.",
  "Give a reason.",
  "The reason can be at most 500 characters.",
  "A request can cover at most 90 days. Split it into two.",
  "Leave can start at most 30 days in the past. Ask your Manager to record older days.",
  "You are not placed at a School on these dates, so you cannot apply for leave.",
  "None of these dates is a working day for you.",
  "A single working day can be a half day at the start or at the end, not both.",
  "These dates overlap your pending request from 2026-10-12 to 2026-10-14.",
  "Attendance is locked for a month in this range (month locked: 2026-09).",
  "These dates overlap another leave request of yours.",
];

export const CANCEL_TEXTS = [
  "This leave has already started. Ask your Manager to revoke it.",
  "This request was already cancelled.",
  "A rejected request cannot be cancelled.",
];

export const DECISION_TEXTS = [
  "This request was cancelled by the Teacher.",
  "This request was cancelled.",
  "This request was already approved.",
  "This request was already rejected.",
  "Only a pending request can be decided.",
  "Only an approved request can be revoked.",
  "A reason is required.",
  "The note can be at most 500 characters.",
  "None of these dates is a working day for you.",
  "A single working day can be a half day at the start or at the end, not both.",
  "This request now overlaps another live leave request of the Teacher.",
  "days set by a supervisor: 2026-01-15, 2026-01-16",
  "month locked: 2026-01",
  "Leave cannot be removed: month locked: 2026-01.",
  "This record was changed by someone else. Reload and try again.",
];

