import {
  getJson,
  queryString,
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

export interface GridCell {
  date: string;
  code: string | null;
  /** The mark's status category, so leave/absent and holiday marks can be coloured. */
  category?: StatusCategory | null;
  dayValue: number | null;
  setByKind: "SELF" | "SUPERVISOR" | null;
  state: DayState;
}

export interface GridRow {
  teacherId: string;
  name: string;
  status: string;
  school: { id: string; name: string } | null;
  manager: { id: string; name: string } | null;
  locked: boolean;
  rollup: RollupView;
  cells: GridCell[];
}

export interface GridResponse {
  month: string;
  days: number;
  content: GridRow[];
  page: number;
  size: number;
  totalElements: number;
}

export interface GridParams {
  month: string;
  query: string;
  page: number;
  size: number;
  zoneId?: string;
  schoolId?: string;
  managerId?: string;
  status?: string;
}

export interface HistoryEntry {
  action: "CREATED" | "CORRECTED" | "CLEARED";
  code: string | null;
  codeName: string | null;
  dayValue: number | null;
  schoolName: string | null;
  note: string | null;
  setByName: string;
  setByKind: "SELF" | "SUPERVISOR";
  setAt: string;
}

function gridQuery(params: GridParams): string {
  return queryString({
    month: params.month,
    query: params.query.trim(),
    page: params.page,
    size: params.size,
    zoneId: params.zoneId,
    schoolId: params.schoolId,
    managerId: params.managerId,
    status: params.status,
  });
}

export function getTeacherGrid(
  authFetch: AuthFetch,
  params: GridParams,
): Promise<ApiResult<GridResponse>> {
  return getJson(
    authFetch,
    `${BASE}/teacher-grid?${gridQuery(params)}`,
    "Could not load the attendance grid.",
  );
}

export function getTeacherMonth(
  authFetch: AuthFetch,
  teacherId: string,
  month: string,
): Promise<ApiResult<TeacherMonthView>> {
  return getJson(
    authFetch,
    `${BASE}/teachers/${teacherId}?month=${month}`,
    "Could not load this Teacher's attendance.",
  );
}

export function setTeacherMark(
  authFetch: AuthFetch,
  teacherId: string,
  date: string,
  body: MarkBody,
): Promise<ApiResult<MarkView>> {
  return sendJson(
    authFetch,
    "PUT",
    `${BASE}/teachers/${teacherId}/marks/${date}`,
    body,
  );
}

export function clearTeacherMark(
  authFetch: AuthFetch,
  teacherId: string,
  date: string,
): Promise<ApiResult<void>> {
  return sendJson(
    authFetch,
    "DELETE",
    `${BASE}/teachers/${teacherId}/marks/${date}`,
    undefined,
    false,
  );
}

export function getMarkHistory(
  authFetch: AuthFetch,
  teacherId: string,
  date: string,
): Promise<ApiResult<HistoryEntry[]>> {
  return getJson(
    authFetch,
    `${BASE}/teachers/${teacherId}/marks/${date}/history`,
    "Could not load the history.",
  );
}

export function getGrid(
  authFetch: AuthFetch,
  params: GridParams,
): Promise<ApiResult<GridResponse>> {
  return getJson(
    authFetch,
    `${BASE}/grid?${gridQuery(params)}`,
    "Could not load the attendance grid.",
  );
}

export interface UnmarkedTeacher {
  teacherId: string;
  name: string;
  dates: string[];
}

export type LockResult =
  | { ok: true; locked: number }
  | { ok: false; reason: string; unmarked: UnmarkedTeacher[] };

export interface MonthEvent {
  event: "LOCKED" | "REOPENED" | "RELOCKED";
  reason: string | null;
  actorUserId: string;
  occurredAt: string;
}

/** Locks the month for every placed Teacher; a refusal lists the unmarked Teachers and days. */
export async function lockMonth(
  authFetch: AuthFetch,
  month: string,
): Promise<LockResult> {
  try {
    const response = await authFetch(`${BASE}/months/${month}/lock`, {
      method: "POST",
    });
    if (response.ok) {
      const body = (await response.json()) as { locked: number };
      return { ok: true, locked: body.locked };
    }
    const body = (await response.json().catch(() => ({}))) as {
      reason?: string;
      unmarked?: UnmarkedTeacher[];
    };
    return {
      ok: false,
      reason: body.reason ?? "Could not lock the month.",
      unmarked: body.unmarked ?? [],
    };
  } catch {
    return { ok: false, reason: "Could not lock the month.", unmarked: [] };
  }
}

export function reopenMonth(
  authFetch: AuthFetch,
  teacherId: string,
  month: string,
  reason: string,
): Promise<ApiResult<{ state: string }>> {
  return sendJson(
    authFetch,
    "POST",
    `${BASE}/teachers/${teacherId}/months/${month}/reopen`,
    { reason },
  );
}

export function relockMonth(
  authFetch: AuthFetch,
  teacherId: string,
  month: string,
): Promise<ApiResult<RollupView>> {
  return sendJson(
    authFetch,
    "POST",
    `${BASE}/teachers/${teacherId}/months/${month}/relock`,
  );
}

export function getMonthEvents(
  authFetch: AuthFetch,
  teacherId: string,
  month: string,
): Promise<ApiResult<MonthEvent[]>> {
  return getJson(
    authFetch,
    `${BASE}/teachers/${teacherId}/months/${month}/events`,
    "Could not load the lock history.",
  );
}

export interface CsvFile {
  blob: Blob;
  filename: string;
}

/** Downloads the filtered month as CSV; the same filters as the grid, and the whole result, not a page. */
export async function exportCsv(
  authFetch: AuthFetch,
  params: Omit<GridParams, "page" | "size">,
): Promise<ApiResult<CsvFile>> {
  const failure = "Could not export the attendance.";
  try {
    const url = `${BASE}/export?${queryString({
      month: params.month,
      query: params.query.trim(),
      zoneId: params.zoneId,
      schoolId: params.schoolId,
      managerId: params.managerId,
      status: params.status,
    })}`;
    const response = await authFetch(url);
    if (!response.ok) {
      const body = (await response.json().catch(() => ({}))) as {
        reason?: string;
      };
      return {
        ok: false,
        reason: body.reason ?? failure,
        status: response.status,
      };
    }
    return {
      ok: true,
      data: {
        blob: await response.blob(),
        filename: `attendance-${params.month}.csv`,
      },
    };
  } catch {
    return { ok: false, reason: failure };
  }
}
