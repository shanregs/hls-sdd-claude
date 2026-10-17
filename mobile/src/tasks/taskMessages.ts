import type { TaskRow, TaskStatus, TaskView } from "../api/tasksApi";

const STATUS: Record<TaskStatus, string> = {
  ASSIGNED: "Assigned",
  ACKNOWLEDGED: "Acknowledged",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
  VERIFIED: "Verified",
  CANCELLED: "Cancelled",
};

export function taskStatusText(status: TaskStatus): string {
  return STATUS[status] ?? status;
}

/** "5 days overdue" from the server's count, or null when the task is not overdue. */
export function overdueText(row: Pick<TaskRow, "overdue" | "daysOverdue">): string | null {
  if (!row.overdue) return null;
  return row.daysOverdue === 1 ? "1 day overdue" : `${row.daysOverdue} days overdue`;
}

/** "N more characters needed" while a text is shorter than the server's minimum; null once it is long enough. */
export function moreNeededText(have: number, need: number): string | null {
  const missing = need - have;
  if (missing <= 0) return null;
  return missing === 1 ? "1 more character needed" : `${missing} more characters needed`;
}

const EVENT: Record<string, string> = {
  CREATED: "Created",
  ACKNOWLEDGED: "Acknowledged",
  STARTED: "Started",
  OVERDUE_REASON: "Reason given for being overdue",
  COMPLETED: "Completed",
  VERIFIED: "Verified",
  SENT_BACK: "Sent back",
  ESCALATED: "Escalated",
  CANCELLED: "Cancelled",
  EVIDENCE_ADDED: "Evidence added",
  REASSIGNED: "Reassigned",
  EDITED: "Edited",
};

/** A history event in words, falling back to the event name the server wrote. */
export function historyEventText(event: string): string {
  return EVENT[event] ?? event.charAt(0) + event.slice(1).toLowerCase().replace(/_/g, " ");
}

export const TASK_VIEWS: { value: TaskView; label: string }[] = [
  { value: "today", label: "Today" },
  { value: "upcoming", label: "Upcoming" },
  { value: "overdue", label: "Overdue" },
  { value: "awaiting", label: "Awaiting verification" },
  { value: "done", label: "Done" },
  { value: "all", label: "All" },
];

/** The length rules of the server that need no data or clock, so the app can say how much more to write. */
export const MIN_OVERDUE_REASON = 5;
export const MIN_COMPLETION_NOTE = 10;
export const MIN_TITLE = 3;
export const MAX_TITLE = 120;
export const MAX_EVIDENCE = 5;
export const MAX_VISIT_FILES = 10;
