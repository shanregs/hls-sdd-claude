import { api } from "./httpClient";

/**
 * Typed calls to the leave endpoints of spec 009, unchanged (spec 020 research §1). The shapes copy the
 * server's responses (specs/020-mobile-leave/data-model.md); the app computes none of the figures, limits
 * or permissions they carry. What a button may do comes only from `allowedActions`.
 */
export type LeaveStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export type LeaveAction = "CANCEL" | "APPROVE" | "REJECT" | "REVOKE";

export interface LeaveType {
  id: string;
  code: string;
  name: string;
}

export interface LeaveRequest {
  id: string;
  teacherId: string;
  teacherName: string;
  schoolId: string;
  schoolName: string;
  leaveType: string;
  firstDate: string;
  lastDate: string;
  halfDayStart: boolean;
  halfDayEnd: boolean;
  workingDays: number;
  reason: string;
  status: LeaveStatus;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  cancelledBy: string | null;
  createdAt: string;
  version: number;
  allowedActions: LeaveAction[];
}

/** A counted day of a preview or of a request's detail; `value` is 1 (whole day) or 0.5 (half day). */
export interface PreviewDay {
  date: string;
  value: number;
}

export interface LeavePreview {
  workingDays: number;
  days: PreviewDay[];
  problems: string[];
}

export interface LeaveDraftBody {
  leaveTypeId: string;
  firstDate: string;
  lastDate: string;
  halfDayStart: boolean;
  halfDayEnd: boolean;
  reason: string;
}

export interface TeacherLeavePage {
  content: LeaveRequest[];
  page: number;
  size: number;
  totalElements: number;
}

export interface SupervisorLeavePage extends TeacherLeavePage {
  pendingCount: number;
}

export interface LeaveDetail {
  request: LeaveRequest;
  days: PreviewDay[];
  problems: string[];
}

export interface ListQuery {
  /** Left out for "all" (Teacher list) or for the server's default of Pending (supervisor list). */
  status?: LeaveStatus;
  page?: number;
  size?: number;
}

const ME = "/api/v1/me/leave";
const BASE = "/api/v1/leave";
const enc = encodeURIComponent;

function query({ status, page = 0, size = 25 }: ListQuery): string {
  const params = [`page=${page}`, `size=${size}`];
  if (status) params.unshift(`status=${enc(status)}`);
  return params.join("&");
}

// ---- Teacher ---------------------------------------------------------------------------------
export const listLeaveTypes = () => api.get<LeaveType[]>(`${ME}/types`);

export const previewLeave = (body: LeaveDraftBody) => api.post<LeavePreview>(`${ME}/preview`, body);

export const submitLeave = (body: LeaveDraftBody) => api.post<LeaveRequest>(ME, body);

export const listMyLeave = (q: ListQuery = {}) => api.get<TeacherLeavePage>(`${ME}?${query(q)}`);

export const cancelMyLeave = (id: string) => api.post<LeaveRequest>(`${ME}/${enc(id)}/cancel`);

// ---- Manager and Director --------------------------------------------------------------------
export const listLeave = (q: ListQuery = {}) => api.get<SupervisorLeavePage>(`${BASE}?${query(q)}`);

export const getLeave = (id: string) => api.get<LeaveDetail>(`${BASE}/${enc(id)}`);

export const approveLeave = (id: string, body: { note?: string; version: number }) =>
  api.post<LeaveRequest>(`${BASE}/${enc(id)}/approve`, body);

export const rejectLeave = (id: string, body: { reason: string; version: number }) =>
  api.post<LeaveRequest>(`${BASE}/${enc(id)}/reject`, body);

export const revokeLeave = (id: string, body: { reason: string; version: number }) =>
  api.post<LeaveRequest>(`${BASE}/${enc(id)}/revoke`, body);
