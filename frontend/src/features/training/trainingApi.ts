import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

/** Training Calendar (spec 024, contracts/training-api.md). Dates are ISO (yyyy-MM-dd), months yyyy-MM. */
const BASE = "/api/v1/training";

export const TRAINING_ROUTE = "/operations/training";
export const UPCOMING_TRAINING_ROUTE = "/my-attendance/training";

export type SessionType = "WEEKEND" | "MONTHLY";
export type SessionStatus =
  | "DRAFT"
  | "PUBLISHED"
  | "HELD"
  | "CANCELLED"
  | "SKIPPED";

export const TYPE_LABELS: Record<SessionType, string> = {
  WEEKEND: "Weekend refresher",
  MONTHLY: "Monthly training",
};

export const STATUS_LABELS: Record<SessionStatus, string> = {
  DRAFT: "Draft",
  PUBLISHED: "Published",
  HELD: "Held",
  CANCELLED: "Cancelled",
  SKIPPED: "Skipped",
};

export type SessionAction =
  | "EDIT"
  | "PUBLISH"
  | "MOVE"
  | "CANCEL"
  | "DELETE"
  | "ASSIGN"
  | "REGISTER_TEACHER"
  | "RECORD_ATTENDANCE"
  | "REGISTER"
  | "CANCEL_REGISTRATION"
  | "CANNOT_ATTEND";

export interface SessionCounts {
  expected: number;
  assigned: number;
  registeredOnly: number;
  attended: number;
  absent: number;
  excused: number;
  walkIns: number;
  noShows: number;
  onLeave: number;
  rate: number | null;
}

export interface EarlierDate {
  date: string;
  changedAt: string;
  reason: string | null;
}

export interface HistoryLine {
  field: string;
  before: string | null;
  after: string | null;
  reason: string | null;
  changedAt: string;
  changedBy: string | null;
}

export interface SessionView {
  id: string;
  type: SessionType;
  topic: string;
  date: string;
  startsAt: string;
  endsAt: string;
  trainerName: string | null;
  trainerContact: string | null;
  venueKind: string | null;
  venue: string | null;
  seatLimit: number | null;
  description: string | null;
  status: SessionStatus;
  patternId: string | null;
  patternMonth: string | null;
  handEdited: boolean;
  cancelReason: string | null;
  skipReason: string | null;
  version: number;
  registered: number;
  seatsLeft: number | null;
  counts: SessionCounts | null;
  actions: SessionAction[];
  holidayNotice: boolean;
  attendanceNotRecorded: boolean;
  groupNotFullyAssigned: number | null;
  earlierDates: EarlierDate[];
  history: HistoryLine[] | null;
  my: { status: "REQUIRED" | "REGISTERED" | null; open: boolean } | null;
}

export interface SessionBody {
  type: SessionType;
  topic: string;
  date: string;
  startsAt: string;
  endsAt: string;
  trainerName?: string;
  trainerContact?: string;
  venueKind?: string;
  venue?: string;
  seatLimit?: number | null;
  description?: string;
  publish?: boolean;
  reason?: string;
  version?: number;
}

export interface SessionQuery {
  from?: string;
  to?: string;
  type?: string;
  status?: string;
  trainer?: string;
  personId?: string;
}

export interface RegistrationResult {
  sessionId: string;
  teacherId: string;
  status: string;
  created: boolean;
  seatsLeft: number | null;
}

export interface RegistrationRow {
  teacherId: string;
  name: string;
  status: string;
  registeredAt: string;
  registeredBy: string | null;
  cancelledAt: string | null;
}

export function getSessions(
  authFetch: AuthFetch,
  query: SessionQuery,
): Promise<ApiResult<SessionView[]>> {
  return getJson<SessionView[]>(
    authFetch,
    `${BASE}/sessions?${queryString({ ...query })}`,
    "Could not load the training sessions.",
  );
}

export function getSession(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<SessionView>> {
  return getJson<SessionView>(
    authFetch,
    `${BASE}/sessions/${id}`,
    "Could not load this session.",
  );
}

export function createSession(authFetch: AuthFetch, body: SessionBody) {
  return sendJson<SessionView>(authFetch, "POST", `${BASE}/sessions`, body);
}

export function updateSession(
  authFetch: AuthFetch,
  id: string,
  body: SessionBody,
) {
  return sendJson<SessionView>(authFetch, "PUT", `${BASE}/sessions/${id}`, body);
}

export function publishSession(
  authFetch: AuthFetch,
  id: string,
  version: number,
) {
  return sendJson<SessionView>(
    authFetch,
    "POST",
    `${BASE}/sessions/${id}/publish`,
    { version },
  );
}

export function cancelSession(
  authFetch: AuthFetch,
  id: string,
  reason: string,
  version: number,
) {
  return sendJson<SessionView>(
    authFetch,
    "POST",
    `${BASE}/sessions/${id}/cancel`,
    { reason, version },
  );
}

export function deleteSession(authFetch: AuthFetch, id: string) {
  return sendJson(authFetch, "DELETE", `${BASE}/sessions/${id}`, undefined, false);
}

export function registerForSession(
  authFetch: AuthFetch,
  id: string,
  teacherId?: string,
) {
  return sendJson<RegistrationResult>(
    authFetch,
    "POST",
    `${BASE}/sessions/${id}/registrations`,
    teacherId ? { teacherId } : {},
  );
}

export function cancelRegistration(
  authFetch: AuthFetch,
  id: string,
  teacherId?: string,
) {
  return sendJson<RegistrationResult>(
    authFetch,
    "DELETE",
    `${BASE}/sessions/${id}/registrations/${teacherId ?? "me"}`,
  );
}

export function getRoster(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<RegistrationRow[]>> {
  return getJson<RegistrationRow[]>(
    authFetch,
    `${BASE}/sessions/${id}/registrations`,
    "Could not load the registrations.",
  );
}
