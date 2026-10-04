import {
  getJson,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Leave API (spec 009, contracts/leave-api.md). Dates are "YYYY-MM-DD". */

export type LeaveStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export type LeaveAction = "CANCEL" | "APPROVE" | "REJECT" | "REVOKE";

export interface LeaveType {
  id: string;
  code: string;
  name: string;
}

export interface LeaveDraft {
  leaveTypeId: string;
  firstDate: string;
  lastDate: string;
  halfDayStart: boolean;
  halfDayEnd: boolean;
  reason: string;
}

export interface LeavePreview {
  workingDays: number;
  days: { date: string; value: number }[];
  problems: string[];
}

export interface LeaveRequestView {
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
  cancelledBy: "TEACHER" | "SUPERVISOR" | null;
  createdAt: string;
  version: number;
  allowedActions: LeaveAction[];
}

export function getLeaveTypes(
  authFetch: AuthFetch,
): Promise<ApiResult<LeaveType[]>> {
  return getJson(
    authFetch,
    "/api/v1/me/leave/types",
    "Could not load the leave types.",
  );
}

export function previewLeave(
  authFetch: AuthFetch,
  draft: LeaveDraft,
): Promise<ApiResult<LeavePreview>> {
  return sendJson(authFetch, "POST", "/api/v1/me/leave/preview", draft);
}

export function submitLeave(
  authFetch: AuthFetch,
  draft: LeaveDraft,
): Promise<ApiResult<LeaveRequestView>> {
  return sendJson(authFetch, "POST", "/api/v1/me/leave", draft);
}

export function getMyLeave(
  authFetch: AuthFetch,
  params: { status?: LeaveStatus | ""; page?: number; size?: number } = {},
): Promise<ApiResult<PageOf<LeaveRequestView>>> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 25));
  return getJson(
    authFetch,
    `/api/v1/me/leave?${query.toString()}`,
    "Could not load your leave requests.",
  );
}

export function cancelMyLeave(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<LeaveRequestView>> {
  return sendJson(authFetch, "POST", `/api/v1/me/leave/${id}/cancel`, {});
}

export interface LeaveListResponse extends PageOf<LeaveRequestView> {
  pendingCount: number;
}

export function getLeaveList(
  authFetch: AuthFetch,
  params: {
    status?: LeaveStatus | "";
    month?: string;
    page?: number;
    size?: number;
  },
): Promise<ApiResult<LeaveListResponse>> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.month) query.set("month", params.month);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 25));
  return getJson(
    authFetch,
    `/api/v1/leave?${query.toString()}`,
    "Could not load the leave requests.",
  );
}

export function decideLeave(
  authFetch: AuthFetch,
  id: string,
  action: "approve" | "reject" | "revoke",
  text: string,
  version: number,
): Promise<ApiResult<LeaveRequestView>> {
  const body =
    action === "approve" ? { note: text, version } : { reason: text, version };
  return sendJson(authFetch, "POST", `/api/v1/leave/${id}/${action}`, body);
}
