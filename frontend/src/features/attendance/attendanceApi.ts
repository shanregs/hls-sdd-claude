import {
  getJson,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

export type StatusCategory = "WORKED" | "LEAVE" | "TRAINING" | "NON_WORKING";

export const CATEGORY_LABELS: Record<StatusCategory, string> = {
  WORKED: "Worked",
  LEAVE: "Leave",
  TRAINING: "Training",
  NON_WORKING: "Non-working",
};

export interface StatusCode {
  id: string;
  shortCode: string;
  name: string;
  category: StatusCategory;
  weight: number;
  active: boolean;
  system: boolean;
  inUse: boolean;
  version: number;
}

export interface SchoolOverride {
  schoolId: string;
  schoolName: string;
  weeklyOff: string[];
}

export interface NonWorkingDate {
  date: string;
  description: string;
}

export interface AttendanceCalendar {
  defaultWeeklyOff: string[];
  defaultVersion: number;
  schoolOverrides: SchoolOverride[];
  nonWorkingDates: NonWorkingDate[];
}

/** Any API result; lets dialogs accept different save functions. */
export type ApiResultLike = ApiResult<unknown>;

const BASE = "/api/v1/attendance";

export function listStatusCodes(
  authFetch: AuthFetch,
  activeOnly = true,
): Promise<ApiResult<StatusCode[]>> {
  return getJson(
    authFetch,
    `${BASE}/status-codes?activeOnly=${activeOnly}`,
    "Could not load status codes.",
  );
}

export function createStatusCode(
  authFetch: AuthFetch,
  body: {
    shortCode: string;
    name: string;
    category: StatusCategory;
    weight: number;
  },
): Promise<ApiResult<StatusCode>> {
  return sendJson(authFetch, "POST", `${BASE}/status-codes`, body);
}

export function updateStatusCode(
  authFetch: AuthFetch,
  code: StatusCode,
  changes: { name: string; weight: number; active: boolean },
): Promise<ApiResult<StatusCode>> {
  return sendJson(authFetch, "PUT", `${BASE}/status-codes/${code.id}`, {
    ...changes,
    version: code.version,
  });
}

export function getCalendar(
  authFetch: AuthFetch,
): Promise<ApiResult<AttendanceCalendar>> {
  return getJson(authFetch, `${BASE}/calendar`, "Could not load the calendar.");
}

export function saveDefaultWeeklyOff(
  authFetch: AuthFetch,
  weeklyOff: string[],
  version: number,
): Promise<ApiResult<AttendanceCalendar>> {
  return sendJson(authFetch, "PUT", `${BASE}/calendar/default`, {
    weeklyOff,
    version,
  });
}

export function saveSchoolOverride(
  authFetch: AuthFetch,
  schoolId: string,
  weeklyOff: string[],
): Promise<ApiResult<AttendanceCalendar>> {
  return sendJson(authFetch, "PUT", `${BASE}/calendar/schools/${schoolId}`, {
    weeklyOff,
  });
}

export function removeSchoolOverride(
  authFetch: AuthFetch,
  schoolId: string,
): Promise<ApiResult<AttendanceCalendar>> {
  return sendJson(authFetch, "DELETE", `${BASE}/calendar/schools/${schoolId}`);
}

export function addNonWorkingDate(
  authFetch: AuthFetch,
  onDate: string,
  description: string,
): Promise<ApiResult<AttendanceCalendar>> {
  return sendJson(authFetch, "POST", `${BASE}/calendar/non-working-dates`, {
    onDate,
    description,
  });
}

export function removeNonWorkingDate(
  authFetch: AuthFetch,
  onDate: string,
): Promise<ApiResult<AttendanceCalendar>> {
  return sendJson(
    authFetch,
    "DELETE",
    `${BASE}/calendar/non-working-dates/${onDate}`,
  );
}

export type DayState =
  | "MARKED"
  | "UNMARKED"
  | "NOT_PLACED"
  | "WEEKLY_OFF"
  | "NON_WORKING"
  | "FUTURE";

export type EditableBy = "SELF" | "SUPERVISOR" | "NONE";

export interface MarkView {
  date: string;
  code: string;
  codeName: string;
  category: StatusCategory;
  dayValue: number;
  schoolId: string;
  schoolName: string;
  setByKind: "SELF" | "SUPERVISOR";
  setByUserId: string;
  setByName: string;
  setAt: string;
  note: string | null;
  version: number;
}

export interface DayView {
  date: string;
  state: DayState;
  mark: MarkView | null;
  editableBy: EditableBy;
}

export interface RollupView {
  workingDays: number;
  daysWorked: number;
  daysLeave: number;
  trainingAvailable: number;
  trainingAttended: number;
  unmarked: number;
  weightedTotal: number;
  locked: boolean;
  frozen: boolean;
}

export interface TeacherMonthView {
  teacherId: string;
  name: string;
  month: string;
  locked: boolean;
  state: "OPEN" | "LOCKED";
  rollup: RollupView;
  days: DayView[];
}

export interface MarkBody {
  statusCode: string;
  dayValue: number;
  note: string;
  version?: number;
}

export function getMyMonth(
  authFetch: AuthFetch,
  month: string,
): Promise<ApiResult<TeacherMonthView>> {
  return getJson(
    authFetch,
    `${BASE}/me?month=${month}`,
    "Could not load your attendance.",
  );
}

export function saveMyMark(
  authFetch: AuthFetch,
  date: string,
  body: MarkBody,
): Promise<ApiResult<MarkView>> {
  return sendJson(authFetch, "PUT", `${BASE}/me/marks/${date}`, body);
}
