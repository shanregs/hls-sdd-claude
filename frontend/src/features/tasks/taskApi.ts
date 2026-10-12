import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Manager tasks (spec 025, contracts/tasks-api.md). */
export const TASKS_ROUTE = "/operations/tasks";

export type TaskStatus =
  | "ASSIGNED"
  | "ACKNOWLEDGED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "VERIFIED"
  | "CANCELLED";

export const STATUS_LABELS: Record<TaskStatus, string> = {
  ASSIGNED: "Assigned",
  ACKNOWLEDGED: "Acknowledged",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
  VERIFIED: "Verified",
  CANCELLED: "Cancelled",
};

export type TaskView =
  "today" | "upcoming" | "overdue" | "awaiting" | "done" | "all";

export interface TaskRow {
  id: string;
  type: string;
  typeLabel: string;
  title: string;
  status: TaskStatus;
  dueOn: string;
  assigneeUserId: string;
  assigneeName: string;
  assigneeActive: boolean;
  linkedType: string | null;
  linkedId: string | null;
  linkedLabel: string | null;
  place: string | null;
  overdue: boolean;
  daysOverdue: number;
  escalated: boolean;
}

export interface TaskSummary {
  today: number;
  upcoming: number;
  overdue: number;
  awaiting: number;
  escalated: number;
  done: number;
}

export interface HistoryLine {
  event: string;
  actorName: string;
  at: string;
  before: string | null;
  after: string | null;
  note: string | null;
}

export interface EvidenceFile {
  fileId: string;
  name: string;
  contentType: string;
  sizeBytes: number;
  addedAt: string;
}

export interface TaskActions {
  acknowledge: boolean;
  start: boolean;
  complete: boolean;
  overdueReason: boolean;
  addEvidence: boolean;
  verify: boolean;
  sendBack: boolean;
  edit: boolean;
  cancel: boolean;
}

export interface TaskDetail {
  task: TaskRow;
  description: string | null;
  createdByName: string | null;
  escalateAfterDays: number;
  source: string;
  overdueReason: string | null;
  completionNote: string | null;
  completedAt: string | null;
  verifiedAt: string | null;
  verifiedByName: string | null;
  escalatedAt: string | null;
  cancelReason: string | null;
  parentTaskId: string | null;
  followUpTaskIds: string[];
  history: HistoryLine[];
  evidence: EvidenceFile[];
  actions: TaskActions;
  version: number;
}

export interface TypeOption {
  value: string;
  label: string;
}

export interface Assignee {
  userId: string;
  name: string;
}

export interface NewTask {
  type: string;
  title: string;
  description: string | null;
  assigneeUserId: string;
  dueOn: string;
  escalateAfterDays: number | null;
}

const BASE = "/api/v1/tasks";

export function listTasks(
  authFetch: AuthFetch,
  filters: {
    view: TaskView;
    assigneeUserId?: string;
    type?: string;
    page?: number;
  },
): Promise<ApiResult<PageOf<TaskRow>>> {
  const query = queryString({ size: 50, ...filters });
  return getJson(authFetch, `${BASE}?${query}`, "Could not load the tasks.");
}

export function getSummary(
  authFetch: AuthFetch,
  assigneeUserId?: string,
): Promise<ApiResult<TaskSummary>> {
  const query = queryString({ assigneeUserId });
  return getJson(
    authFetch,
    `${BASE}/summary${query ? `?${query}` : ""}`,
    "Could not load the task counts.",
  );
}

export function getTypes(
  authFetch: AuthFetch,
): Promise<ApiResult<TypeOption[]>> {
  return getJson(authFetch, `${BASE}/types`, "Could not load the task types.");
}

export function getAssignees(
  authFetch: AuthFetch,
): Promise<ApiResult<Assignee[]>> {
  return getJson(
    authFetch,
    `${BASE}/assignees`,
    "Could not load the Managers.",
  );
}

export function getTask(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<TaskDetail>> {
  return getJson(authFetch, `${BASE}/${id}`, "Could not load this task.");
}

export function createTask(
  authFetch: AuthFetch,
  body: NewTask,
): Promise<ApiResult<TaskDetail>> {
  return sendJson(authFetch, "POST", BASE, body);
}

export function updateTask(
  authFetch: AuthFetch,
  id: string,
  body: {
    title?: string;
    assigneeUserId?: string;
    dueOn?: string;
    version: number;
  },
): Promise<ApiResult<TaskDetail>> {
  return sendJson(authFetch, "PUT", `${BASE}/${id}`, body);
}

export type TaskAction =
  | "acknowledge"
  | "start"
  | "complete"
  | "overdue-reason"
  | "verify"
  | "send-back"
  | "cancel";

export function actOnTask(
  authFetch: AuthFetch,
  id: string,
  action: TaskAction,
  body?: Record<string, unknown>,
): Promise<ApiResult<TaskDetail>> {
  return sendJson(authFetch, "POST", `${BASE}/${id}/${action}`, body ?? {});
}

export async function uploadEvidence(
  authFetch: AuthFetch,
  id: string,
  file: File,
): Promise<ApiResult<TaskDetail>> {
  try {
    const form = new FormData();
    form.append("file", file);
    const response = await authFetch(`${BASE}/${id}/evidence`, {
      method: "POST",
      body: form,
    });
    if (!response.ok) {
      let reason = GENERIC_FAILURE;
      try {
        reason =
          ((await response.json()) as { reason?: string }).reason ?? reason;
      } catch {
        // keep the generic reason
      }
      return { ok: false, reason, status: response.status };
    }
    return { ok: true, data: (await response.json()) as TaskDetail };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}
