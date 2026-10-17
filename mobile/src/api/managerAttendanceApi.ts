import { toBase64 } from "../formats/base64";
import { api, fetchBinary, upload, type UploadFile } from "./httpClient";

/**
 * Typed calls to the Manager's own attendance endpoints of spec 032, unchanged (spec 033 research §1). The shapes
 * copy the server's responses (specs/033-mobile-manager-field/data-model.md); the app computes none of the
 * statuses, flags or figures they carry.
 */
const BASE = "/api/v1/manager-attendance/me";

export type DayType = "OFFICE" | "FIELD";

/** A mark the server puts on a day; the Manager sees them in plain words. */
export type DayFlag = "LATE" | "NO_CHECK_OUT" | "ADDED_LATER" | "CORRECTED" | "UNLINKED";

export type MonthStatus =
  "WORKED" | "FIELD" | "LEAVE" | "HOLIDAY" | "WEEKLY_OFF" | "NOT_EMPLOYED" | "PENDING" | "MISSED";

export interface ActivityRef {
  kind: string;
  id: string;
}

/** Approved leave on a day: 1.0 or 0.5, and whether it is unpaid (Loss of Pay). */
export interface DayLeave {
  fraction: number;
  unpaid: boolean;
}

/** One Manager's day. The location is never part of this view. */
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
  flags: DayFlag[];
  activities: ActivityRef[];
}

/** A planned activity of the day. The server's record has no title. */
export interface PlannedActivity {
  kind: string;
  id: string;
  date: string;
  place: string | null;
  status: string | null;
}

export interface Today {
  date: string;
  workingDay: boolean;
  missedCutoff: string;
  lateAfter: string;
  day: ManagerDay | null;
  leave: DayLeave | null;
  missingCheckOutDates: string[];
  plannedActivities: PlannedActivity[];
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

export interface MonthDay {
  date: string;
  status: MonthStatus;
  id: string | null;
  dayType: DayType | null;
  purpose: string | null;
  checkInAt: string | null;
  checkOutAt: string | null;
  late: boolean;
  hasPhoto: boolean;
  leave: DayLeave | null;
  flags: DayFlag[];
  activities: ActivityRef[];
}

export interface ManagerMonth {
  month: string;
  locked: boolean;
  figures: MonthFigures | null;
  figuresUnavailable: string | null;
  days: MonthDay[];
}

/** A position as the phone reported it. */
export interface Position {
  latitude: number;
  longitude: number;
  accuracyMeters: number;
}

export interface CheckInBody extends Position {
  dayType: DayType;
  purpose?: string;
  activities: ActivityRef[];
}

export type RequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export interface Regularization {
  id: string;
  date: string;
  proposedTime: string;
  dayType: DayType;
  purpose: string | null;
  reason: string;
  status: RequestStatus;
  requestedAt: string;
  decidedAt: string | null;
  decisionReason: string | null;
}

export interface RegularizationBody {
  date: string;
  proposedTime: string;
  dayType: DayType;
  purpose?: string;
  reason: string;
}

export const getToday = () => api.get<Today>(`${BASE}/today`);

export const checkIn = (body: CheckInBody) => api.post<ManagerDay>(`${BASE}/check-in`, body);

export const checkOut = (body: Position) => api.post<ManagerDay>(`${BASE}/check-out`, body);

export const completeCheckOut = (date: string, body: { time: string; reason: string }) =>
  api.post<ManagerDay>(`${BASE}/days/${date}/complete-check-out`, body);

export const attachPhoto = (date: string, file: UploadFile) =>
  upload<ManagerDay>(`${BASE}/days/${date}/photo`, "file", file);

/** The Manager's own photo of a day, with what the server says about the file. */
export interface PhotoFile {
  /** Ready for an image view; held in memory only. */
  dataUri: string;
  contentType: string;
  sizeBytes: number;
  fileName: string | null;
}

export async function openPhoto(date: string): Promise<PhotoFile> {
  const answer = await fetchBinary(`${BASE}/days/${date}/photo`);
  return {
    dataUri: `data:${answer.contentType};base64,${toBase64(answer.bytes)}`,
    contentType: answer.contentType,
    sizeBytes: answer.sizeBytes,
    fileName: answer.fileName,
  };
}

export const getMonth = (yearMonth: string) => api.get<ManagerMonth>(`${BASE}/months/${yearMonth}`);

export const listRegularizations = () => api.get<Regularization[]>(`${BASE}/regularizations`);

export const requestRegularization = (body: RegularizationBody) =>
  api.post<Regularization>(`${BASE}/regularizations`, body);

export const cancelRegularization = (id: string) => api.delete<void>(`${BASE}/regularizations/${id}`);
