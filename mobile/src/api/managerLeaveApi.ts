import { api } from "./httpClient";

/**
 * Typed calls to the Manager's own leave endpoints (spec 032, served by the leave module), unchanged (spec 033
 * research §1). There is no preview call and no `allowedActions`: a Pending request can be cancelled and the
 * server refuses anything else.
 */
const BASE = "/api/v1/manager-attendance/me/leave";

export type ManagerLeaveStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED" | "REVOKED";

export interface ManagerLeaveType {
  id: string;
  name: string;
  lossOfPay: boolean;
}

export interface ManagerLeave {
  id: string;
  leaveTypeId: string;
  leaveTypeName: string;
  lossOfPay: boolean;
  firstDate: string;
  lastDate: string;
  firstHalf: boolean;
  lastHalf: boolean;
  reason: string;
  status: ManagerLeaveStatus;
  requestedAt: string;
  decidedAt: string | null;
  decisionReason: string | null;
}

export interface ManagerLeaveBody {
  leaveTypeId: string;
  firstDate: string;
  lastDate: string;
  firstHalf: boolean;
  lastHalf: boolean;
  reason: string;
}

export const listManagerLeaveTypes = () => api.get<ManagerLeaveType[]>(`${BASE}/types`);

export const listManagerLeave = () => api.get<ManagerLeave[]>(BASE);

export const requestManagerLeave = (body: ManagerLeaveBody) => api.post<ManagerLeave>(BASE, body);

export const cancelManagerLeave = (id: string) => api.delete<void>(`${BASE}/${id}`);
