import { api } from "./httpClient";

/**
 * Typed calls to the attendance endpoints of spec 008, unchanged (spec 019 research §1). The shapes
 * copy the server's responses (specs/019-mobile-attendance/data-model.md); the app computes none of
 * the figures, windows or permissions they carry.
 */
const BASE = "/api/v1/attendance";

export type StatusCategory = "WORKED" | "LEAVE" | "TRAINING" | "NON_WORKING";

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

export type DayState = "MARKED" | "UNMARKED" | "NOT_PLACED" | "WEEKLY_OFF" | "NON_WORKING" | "FUTURE";

/** Who may change the day for the person looking: the Teacher (SELF), a supervisor, or nobody. */
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

export interface Ref {
  id: string;
  name: string;
}

export interface TeacherGridRow {
  teacherId: string;
  name: string;
  status: string;
  school: Ref | null;
  manager: Ref | null;
  locked: boolean;
  rollup: RollupView;
}

export interface TeacherGridPage {
  month: string;
  days: number;
  content: TeacherGridRow[];
  page: number;
  size: number;
  totalElements: number;
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

export type MarkAction = "CREATED" | "CORRECTED" | "CLEARED";

/** One earlier value of a day, newest first (server `HistoryView`). */
export interface DayHistoryEntry {
  action: MarkAction;
  code: string | null;
  codeName: string | null;
  dayValue: number | null;
  schoolName: string | null;
  note: string | null;
  setByName: string;
  setByKind: "SELF" | "SUPERVISOR";
  setAt: string;
}

/** Whole day is 1, half day is 0.5: nothing else is offered (data-model.md validation rules). */
export type DayValue = 1 | 0.5;

export interface MarkRequest {
  statusCode: string;
  dayValue: DayValue;
  note?: string;
  version?: number;
}

const enc = encodeURIComponent;

// ---- Teacher self-service --------------------------------------------------------------------
export const getMyMonth = (month: string) => api.get<TeacherMonthView>(`${BASE}/me?month=${enc(month)}`);

export const saveMyMark = (date: string, body: MarkRequest) =>
  api.put<MarkView>(`${BASE}/me/marks/${enc(date)}`, body);

// ---- Shared reference data ---------------------------------------------------------------------
export const listStatusCodes = () => api.get<StatusCode[]>(`${BASE}/status-codes?activeOnly=true`);

export const getCalendar = () => api.get<AttendanceCalendar>(`${BASE}/calendar`);

// ---- Manager ------------------------------------------------------------------------------------
export interface TeacherGridQuery {
  month: string;
  query?: string;
  page?: number;
  size?: number;
}

export function getTeacherGrid({ month, query, page = 0, size = 25 }: TeacherGridQuery) {
  const params = [`month=${enc(month)}`, `page=${page}`, `size=${size}`];
  if (query && query.trim() !== "") params.push(`query=${enc(query.trim())}`);
  return api.get<TeacherGridPage>(`${BASE}/teacher-grid?${params.join("&")}`);
}

export const getTeacherMonth = (teacherId: string, month: string) =>
  api.get<TeacherMonthView>(`${BASE}/teachers/${enc(teacherId)}?month=${enc(month)}`);

export const saveTeacherMark = (teacherId: string, date: string, body: MarkRequest) =>
  api.put<MarkView>(`${BASE}/teachers/${enc(teacherId)}/marks/${enc(date)}`, body);

export const clearTeacherMark = (teacherId: string, date: string) =>
  api.delete<void>(`${BASE}/teachers/${enc(teacherId)}/marks/${enc(date)}`);

export const getDayHistory = (teacherId: string, date: string) =>
  api.get<DayHistoryEntry[]>(`${BASE}/teachers/${enc(teacherId)}/marks/${enc(date)}/history`);
