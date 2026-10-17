import { api, upload, type UploadFile } from "./httpClient";
import { toFileView } from "./marketingApi";

/**
 * Typed calls to the task endpoints of spec 025, unchanged (spec 034 research §2). A task carries an `actions` object that
 * says which steps are allowed; the app shows exactly those and computes nothing else.
 */
const BASE = "/api/v1/tasks";

export type TaskStatus = "ASSIGNED" | "ACKNOWLEDGED" | "IN_PROGRESS" | "COMPLETED" | "VERIFIED" | "CANCELLED";

export type TaskView = "today" | "upcoming" | "overdue" | "awaiting" | "done" | "all";

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

export interface TaskPage {
  content: TaskRow[];
  page: number;
  size: number;
  totalElements: number;
}

export interface TaskHistoryLine {
  event: string;
  actorUserId: string | null;
  actorName: string | null;
  at: string;
  before: string | null;
  after: string | null;
  note: string | null;
}

export interface TaskEvidence {
  fileId: string;
  name: string;
  contentType: string;
  sizeBytes: number;
  addedAt: string;
  addedBy: string | null;
}

/** The steps the server allows this caller to take now. Only the first five are used by the app. */
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
  createdBy: string | null;
  createdByName: string | null;
  escalateAfterDays: number;
  source: string | null;
  overdueReason: string | null;
  completionNote: string | null;
  completedAt: string | null;
  verifiedAt: string | null;
  verifiedByName: string | null;
  escalatedAt: string | null;
  cancelReason: string | null;
  parentTaskId: string | null;
  followUpTaskIds: string[];
  history: TaskHistoryLine[];
  evidence: TaskEvidence[];
  actions: TaskActions;
  version: number;
}

export interface TaskSummary {
  today: number;
  upcoming: number;
  overdue: number;
  awaiting: number;
  escalated: number;
  done: number;
}

export interface TaskTypeOption {
  value: string;
  label: string;
}

export interface Assignee {
  userId: string;
  name: string;
}

export interface CreateTaskBody {
  type: string;
  title: string;
  description?: string;
  assigneeUserId: string;
  dueOn: string;
  place?: string;
}

export const listTasks = (view: TaskView, page: number, size: number) =>
  api.get<TaskPage>(`${BASE}?view=${view}&page=${page}&size=${size}`);

export const getTaskSummary = () => api.get<TaskSummary>(`${BASE}/summary`);

export const getTask = (id: string) => api.get<TaskDetail>(`${BASE}/${id}`);

export const acknowledgeTask = (id: string) => api.post<TaskDetail>(`${BASE}/${id}/acknowledge`);

export const startTask = (id: string) => api.post<TaskDetail>(`${BASE}/${id}/start`);

export const giveOverdueReason = (id: string, body: { reason: string; followUpOn?: string }) =>
  api.post<TaskDetail>(`${BASE}/${id}/overdue-reason`, body);

export const completeTask = (id: string, body: { note: string }) =>
  api.post<TaskDetail>(`${BASE}/${id}/complete`, body);

export const addTaskEvidence = (id: string, file: UploadFile) =>
  upload<TaskDetail>(`${BASE}/${id}/evidence`, "file", file);

export const openTaskFile = (fileId: string) => toFileView(`${BASE}/files/${fileId}`);

export const listTaskTypes = () => api.get<TaskTypeOption[]>(`${BASE}/types`);

export const listAssignees = () => api.get<Assignee[]>(`${BASE}/assignees`);

export const createTask = (body: CreateTaskBody) => api.post<TaskDetail>(BASE, body);
