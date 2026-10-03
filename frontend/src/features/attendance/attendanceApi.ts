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
