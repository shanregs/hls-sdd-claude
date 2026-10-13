import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

/** Manager attendance (spec 032, contracts/manager-attendance-api.md). Times are ISO strings, fractions numbers. */
const BASE = "/api/v1/manager-attendance";

export type DayType = "OFFICE" | "FIELD";

export interface ManagerLocation {
  latitude: number;
  longitude: number;
  accuracyMeters?: number | null;
}

export interface ActivityRef {
  kind: string;
  id: string;
}

export interface PlannedActivity {
  kind: string;
  id: string;
  date: string;
  place: string | null;
  status: string | null;
}

export interface DayLeave {
  fraction: number;
  unpaid: boolean;
}

export interface ManagerDay {
  id: string;
  date: string;
  dayType: DayType | null;
  purpose: string | null;
  checkInAt: string | null;
  late: boolean;
  checkOutAt: string | null;
  hasPhoto: boolean;
  leave: DayLeave | null;
  flags: string[];
}

export interface TodayStatus {
  date: string;
  workingDay: boolean;
  missedCutoff: string;
  lateAfter: string;
  day: ManagerDay | null;
  leave: DayLeave | null;
  missingCheckOutDates: string[];
  plannedActivities: PlannedActivity[];
}

export interface CheckInBody extends ManagerLocation {
  dayType: DayType;
  purpose?: string | null;
  activities?: ActivityRef[];
}

export interface Place {
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
}

export interface HistoryEntry {
  id: string;
  action: string;
  changedBy: string;
  changedByName: string;
  changedAt: string;
  reason: string | null;
  beforeJson: string | null;
  afterJson: string | null;
}

/** One date of a month. A date with no record has only `date` and `status` filled in. */
export interface MonthDay {
  date: string;
  status: string;
  id: string | null;
  dayType: DayType | null;
  purpose: string | null;
  checkInAt: string | null;
  checkOutAt: string | null;
  late: boolean;
  hasPhoto: boolean;
  leave: DayLeave | null;
  flags: string[];
  activities: ActivityRef[];
  checkInPlace: Place | null;
  checkOutPlace: Place | null;
  /** Present only on an Admin or Director's view of one Manager. */
  history: HistoryEntry[] | null;
}

export interface MonthFigures {
  workingDays: number;
  daysWorked: number;
  leavePaid: number;
  leaveUnpaid: number;
  unpaid: number;
  fieldDays: number;
  pending: string[];
}

export interface MonthView {
  month: string;
  managerId: string;
  managerName: string;
  locked: boolean;
  figures: MonthFigures | null;
  figuresUnavailable: string | null;
  days: MonthDay[];
}

export interface GridRow {
  managerId: string;
  name: string;
  employeeId: string | null;
  locked: boolean;
  figures: MonthFigures | null;
  figuresUnavailable: string | null;
  /** One status per date of the month, in date order. */
  statuses: string[];
}

export interface Grid {
  month: string;
  firstDate: string;
  daysInMonth: number;
  managers: GridRow[];
}

export interface Regularization {
  id: string;
  managerId: string;
  managerName: string | null;
  date: string;
  proposedTime: string;
  dayType: DayType;
  purpose: string | null;
  reason: string;
  status: string;
  decisionReason: string | null;
}

export interface RegularizationBody {
  date: string;
  proposedTime: string;
  dayType: DayType;
  purpose: string | null;
  reason: string;
}

export interface AttendanceSettings {
  missedCutoff: string;
  lateAfter: string;
  version: number;
}

export interface LeaveType {
  id: string;
  name: string;
  lossOfPay: boolean;
}

export interface LeaveRequestBody {
  leaveTypeId: string;
  firstDate: string;
  lastDate: string;
  firstHalf: boolean;
  lastHalf: boolean;
  reason: string;
}

export interface ManagerLeaveRequest extends LeaveRequestBody {
  managerName: string | null;
  leaveTypeName: string | null;
  lossOfPay: boolean;
  requestedAt: string;
  id: string;
  managerId: string;
  status: string;
  decisionReason: string | null;
}

// ------------------------------------------------------------------ the Manager's own day

export const getToday = (a: AuthFetch): Promise<ApiResult<TodayStatus>> =>
  getJson(a, `${BASE}/me/today`, "Could not load today.");

export const checkIn = (
  a: AuthFetch,
  body: CheckInBody,
): Promise<ApiResult<ManagerDay>> =>
  sendJson(a, "POST", `${BASE}/me/check-in`, body);

export const checkOut = (
  a: AuthFetch,
  body: ManagerLocation,
): Promise<ApiResult<ManagerDay>> =>
  sendJson(a, "POST", `${BASE}/me/check-out`, body);

export const completeOwnCheckOut = (
  a: AuthFetch,
  date: string,
  body: { time: string; reason: string },
): Promise<ApiResult<ManagerDay>> =>
  sendJson(a, "POST", `${BASE}/me/days/${date}/complete-check-out`, body);

export async function uploadPhoto(
  a: AuthFetch,
  date: string,
  file: File,
): Promise<ApiResult<ManagerDay>> {
  const form = new FormData();
  form.append("file", file);
  try {
    const response = await a(`${BASE}/me/days/${date}/photo`, {
      method: "POST",
      body: form,
    });
    if (!response.ok) {
      const body = (await response.json().catch(() => ({}))) as {
        reason?: string;
      };
      return {
        ok: false,
        reason: body.reason ?? "The photo could not be added.",
        status: response.status,
      };
    }
    return { ok: true, data: (await response.json()) as ManagerDay };
  } catch {
    return { ok: false, reason: "The photo could not be added." };
  }
}

export const getMyMonth = (
  a: AuthFetch,
  month: string,
): Promise<ApiResult<MonthView>> =>
  getJson(a, `${BASE}/me/months/${month}`, "Could not load the month.");

export const listMyRegularizations = (
  a: AuthFetch,
): Promise<ApiResult<Regularization[]>> =>
  getJson(a, `${BASE}/me/regularizations`, "Could not load your requests.");

export const requestRegularization = (
  a: AuthFetch,
  body: RegularizationBody,
): Promise<ApiResult<Regularization>> =>
  sendJson(a, "POST", `${BASE}/me/regularizations`, body);

export const cancelRegularization = (
  a: AuthFetch,
  id: string,
): Promise<ApiResult> =>
  sendJson(a, "DELETE", `${BASE}/me/regularizations/${id}`, undefined, false);

// ------------------------------------------------------------------ Manager leave (own)

export const listLeaveTypes = (a: AuthFetch): Promise<ApiResult<LeaveType[]>> =>
  getJson(a, `${BASE}/me/leave/types`, "Could not load leave types.");

export const listMyLeave = (
  a: AuthFetch,
): Promise<ApiResult<ManagerLeaveRequest[]>> =>
  getJson(a, `${BASE}/me/leave`, "Could not load your leave.");

export const requestLeave = (
  a: AuthFetch,
  body: LeaveRequestBody,
): Promise<ApiResult<ManagerLeaveRequest>> =>
  sendJson(a, "POST", `${BASE}/me/leave`, body);

export const cancelLeave = (a: AuthFetch, id: string): Promise<ApiResult> =>
  sendJson(a, "DELETE", `${BASE}/me/leave/${id}`, undefined, false);

// ------------------------------------------------------------------ Admin and Director

export const getGrid = (
  a: AuthFetch,
  params: { month: string; zoneId?: string; q?: string },
): Promise<ApiResult<Grid>> =>
  getJson(a, `${BASE}/grid?${queryString(params)}`, "Could not load the grid.");

export const getMissed = (
  a: AuthFetch,
  date?: string,
): Promise<ApiResult<unknown>> =>
  getJson(
    a,
    `${BASE}/missed?${queryString({ date })}`,
    "Could not load the list.",
  );

export const getOnLeave = (
  a: AuthFetch,
  date?: string,
): Promise<ApiResult<unknown>> =>
  getJson(
    a,
    `${BASE}/on-leave?${queryString({ date })}`,
    "Could not load the list.",
  );

export const getManagerMonth = (
  a: AuthFetch,
  managerId: string,
  month: string,
): Promise<ApiResult<MonthView>> =>
  getJson(
    a,
    `${BASE}/managers/${managerId}/months/${month}`,
    "Could not load the month.",
  );

export const correctDay = (
  a: AuthFetch,
  managerId: string,
  date: string,
  body: Record<string, unknown>,
): Promise<ApiResult<ManagerDay>> =>
  sendJson(a, "PUT", `${BASE}/managers/${managerId}/days/${date}`, body);

export const completeCheckOutFor = (
  a: AuthFetch,
  managerId: string,
  date: string,
  body: { time: string; reason: string },
): Promise<ApiResult<ManagerDay>> =>
  sendJson(
    a,
    "POST",
    `${BASE}/managers/${managerId}/days/${date}/complete-check-out`,
    body,
  );

export const lockMonth = (
  a: AuthFetch,
  month: string,
  managerId?: string,
): Promise<ApiResult<unknown>> =>
  sendJson(
    a,
    "POST",
    `${BASE}/months/${month}/lock`,
    managerId ? { managerId } : {},
  );

export const reopenMonth = (
  a: AuthFetch,
  month: string,
  managerId: string,
  reason: string,
): Promise<ApiResult<unknown>> =>
  sendJson(a, "POST", `${BASE}/months/${month}/reopen`, { managerId, reason });

export const getSettings = (
  a: AuthFetch,
): Promise<ApiResult<AttendanceSettings>> =>
  getJson(a, `${BASE}/settings`, "Could not load the settings.");

export const saveSettings = (
  a: AuthFetch,
  body: AttendanceSettings,
): Promise<ApiResult<AttendanceSettings>> =>
  sendJson(a, "PUT", `${BASE}/settings`, body);

export const listLeaveDecisions = (
  a: AuthFetch,
  params: { status?: string; managerId?: string },
): Promise<ApiResult<ManagerLeaveRequest[]>> =>
  getJson(a, `${BASE}/leave?${queryString(params)}`, "Could not load leave.");

export const decideLeave = (
  a: AuthFetch,
  id: string,
  action: "approve" | "reject" | "revoke",
  reason?: string,
): Promise<ApiResult<ManagerLeaveRequest>> =>
  sendJson(a, "POST", `${BASE}/leave/${id}/${action}`, { reason });

export const listRegularizations = (
  a: AuthFetch,
  params: { status?: string; managerId?: string },
): Promise<ApiResult<Regularization[]>> =>
  getJson(
    a,
    `${BASE}/regularizations?${queryString(params)}`,
    "Could not load requests.",
  );

export const decideRegularization = (
  a: AuthFetch,
  id: string,
  action: "approve" | "reject",
  reason?: string,
): Promise<ApiResult<Regularization>> =>
  sendJson(a, "POST", `${BASE}/regularizations/${id}/${action}`, { reason });
