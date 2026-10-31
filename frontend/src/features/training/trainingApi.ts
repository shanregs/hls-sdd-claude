import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type PageOf,
  type AuthFetch,
} from "../common/masterDataApi";

/** Training Calendar (spec 024, contracts/training-api.md). Dates are ISO (yyyy-MM-dd), months yyyy-MM. */
const BASE = "/api/v1/training";

export const TRAINING_ROUTE = "/operations/training";
export const UPCOMING_TRAINING_ROUTE = "/my-attendance/training";

export type SessionType = "WEEKEND" | "MONTHLY";
export type SessionStatus =
  "DRAFT" | "PUBLISHED" | "HELD" | "CANCELLED" | "SKIPPED";

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
  return sendJson<SessionView>(
    authFetch,
    "PUT",
    `${BASE}/sessions/${id}`,
    body,
  );
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
  return sendJson(
    authFetch,
    "DELETE",
    `${BASE}/sessions/${id}`,
    undefined,
    false,
  );
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

// ------------------------------------------------------------------ patterns (US2)

export type RuleKind = "NTH_WEEKDAY" | "DAY_OF_MONTH";

export interface PatternBody {
  ruleKind: RuleKind;
  nth?: number | null;
  weekday?: number | null;
  dayOfMonth?: number | null;
  startsAt: string;
  endsAt: string;
  topic: string;
  trainerName?: string;
  trainerContact?: string;
  venueKind: string;
  venue?: string;
  description?: string;
  defaultGroup?: { kind: string; ref?: string | null } | null;
  startMonth?: string;
  fromMonth?: string;
  version?: number;
}

export interface PatternView {
  id: string;
  ruleKind: RuleKind;
  nth: number | null;
  weekday: number | null;
  dayOfMonth: number | null;
  startsAt: string;
  endsAt: string;
  topic: string;
  trainerName: string | null;
  trainerContact: string | null;
  venueKind: string;
  venue: string | null;
  description: string | null;
  defaultGroup: { kind: string; ref: string | null } | null;
  startMonth: string;
  endMonth: string | null;
  active: boolean;
  version: number;
  skips: { month: string; reason: string }[];
}

export interface PreviewRow {
  month: string;
  date: string;
  note: string | null;
  holidayNotice: boolean;
}

export function describeRule(p: {
  ruleKind: RuleKind;
  nth: number | null;
  weekday: number | null;
  dayOfMonth: number | null;
}): string {
  if (p.ruleKind === "DAY_OF_MONTH") return `Day ${p.dayOfMonth} of the month`;
  const nth =
    p.nth === 5
      ? "Last"
      : ["", "First", "Second", "Third", "Fourth"][p.nth ?? 0];
  const day = WEEKDAYS[(p.weekday ?? 1) - 1];
  return `${nth} ${day} of the month`;
}

export const WEEKDAYS = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday",
];

export function getPatterns(
  authFetch: AuthFetch,
): Promise<ApiResult<PatternView[]>> {
  return getJson<PatternView[]>(
    authFetch,
    `${BASE}/patterns`,
    "Could not load the patterns.",
  );
}

export function previewPattern(authFetch: AuthFetch, body: PatternBody) {
  return sendJson<PreviewRow[]>(
    authFetch,
    "POST",
    `${BASE}/patterns/preview`,
    body,
  );
}

export function createPattern(authFetch: AuthFetch, body: PatternBody) {
  return sendJson<PatternView>(authFetch, "POST", `${BASE}/patterns`, body);
}

export function updatePattern(
  authFetch: AuthFetch,
  id: string,
  body: PatternBody,
) {
  return sendJson<PatternView>(
    authFetch,
    "PUT",
    `${BASE}/patterns/${id}`,
    body,
  );
}

export function skipMonth(
  authFetch: AuthFetch,
  id: string,
  month: string,
  reason: string,
) {
  return sendJson<PatternView>(
    authFetch,
    "POST",
    `${BASE}/patterns/${id}/skips`,
    { month, reason },
  );
}

export function unskipMonth(authFetch: AuthFetch, id: string, month: string) {
  return sendJson<PatternView>(
    authFetch,
    "DELETE",
    `${BASE}/patterns/${id}/skips/${month}`,
  );
}

export function stopPattern(
  authFetch: AuthFetch,
  id: string,
  fromMonth: string,
  version: number,
) {
  return sendJson<PatternView>(
    authFetch,
    "POST",
    `${BASE}/patterns/${id}/stop`,
    { fromMonth, version },
  );
}

// ------------------------------------------------------------------ assignments (US3)

export interface AssignBody {
  personKind?: "TEACHER" | "MANAGER";
  ids?: string[];
  groupKind?: string;
  groupRef?: string | null;
  confirm?: boolean;
}

export interface PreviewPerson {
  kind: string;
  id: string;
  name: string;
  state: "NEW" | "ALREADY" | "REFUSED";
  reason: string | null;
}

export interface AssignPreview {
  newCount: number;
  alreadyCount: number;
  refusedCount: number;
  people: PreviewPerson[];
}

export interface AssignResult {
  assigned: number;
  alreadyAssigned: number;
  refused: number;
  refusedPeople: PreviewPerson[];
}

export interface AssignmentRow {
  id: string;
  personKind: "TEACHER" | "MANAGER";
  personId: string;
  name: string;
  source: string;
  groupKind: string | null;
  status: "REQUIRED" | "REMOVED";
  assignedAt: string;
  cannotAttendNote: string | null;
}

export const GROUP_LABELS: Record<string, string> = {
  SCHOOL: "A School",
  MANAGER: "A Manager's Teachers",
  ZONE: "A Zone",
  ALL_TEACHERS: "All Teachers",
  ALL_MANAGERS: "All Managers",
  ALL: "Everyone",
};

export function previewAssignment(
  authFetch: AuthFetch,
  id: string,
  body: AssignBody,
) {
  return sendJson<AssignPreview>(
    authFetch,
    "POST",
    `${BASE}/sessions/${id}/assignments/preview`,
    body,
  );
}

export function assignPeople(
  authFetch: AuthFetch,
  id: string,
  body: AssignBody,
) {
  return sendJson<AssignResult>(
    authFetch,
    "POST",
    `${BASE}/sessions/${id}/assignments`,
    {
      ...body,
      confirm: true,
    },
  );
}

export function getAssignments(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<AssignmentRow[]>> {
  return getJson<AssignmentRow[]>(
    authFetch,
    `${BASE}/sessions/${id}/assignments`,
    "Could not load the assigned people.",
  );
}

export function removeAssignment(
  authFetch: AuthFetch,
  id: string,
  assignmentId: string,
  reason: string,
) {
  return sendJson(
    authFetch,
    "DELETE",
    `${BASE}/sessions/${id}/assignments/${assignmentId}`,
    { reason },
    false,
  );
}

export function cannotAttend(authFetch: AuthFetch, id: string, note: string) {
  return sendJson(
    authFetch,
    "POST",
    `${BASE}/sessions/${id}/assignments/me/cannot-attend`,
    { note },
    false,
  );
}

// ------------------------------------------------------------------ attendance (US4)

export type Outcome = "PRESENT" | "ABSENT" | "EXCUSED";

export interface AttendanceRow {
  entryId: string | null;
  personKind: "TEACHER" | "MANAGER";
  personId: string;
  name: string;
  basis: string;
  assigned: boolean;
  registered: boolean;
  outcome: Outcome | null;
  dayValue: number | null;
  reason: string | null;
  certificateNote: string | null;
  internalRemark: string | null;
  onLeave: boolean;
  syncState: string | null;
  clashNote: string | null;
  version: number | null;
  history: {
    action: string;
    outcome: string;
    dayValue: number | null;
    reason: string | null;
    changedAt: string;
  }[];
}

export interface AttendanceInput {
  personKind: string;
  personId: string;
  outcome: Outcome;
  dayValue?: number | null;
  reason?: string;
  certificateNote?: string;
  internalRemark?: string;
  version?: number | null;
}

export interface RowResult {
  personId: string;
  personKind: string;
  status: "SAVED" | "SAVED_CLASH" | "REFUSED";
  code: string | null;
  message: string | null;
}

export interface SaveAttendanceResult {
  results: RowResult[];
  counts: SessionCounts;
}

export function getAttendance(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<AttendanceRow[]>> {
  return getJson<AttendanceRow[]>(
    authFetch,
    `${BASE}/sessions/${id}/attendance`,
    "Could not load the attendance.",
  );
}

export function saveAttendance(
  authFetch: AuthFetch,
  id: string,
  rows: AttendanceInput[],
) {
  return sendJson<SaveAttendanceResult>(
    authFetch,
    "PUT",
    `${BASE}/sessions/${id}/attendance`,
    { rows },
  );
}

export function addWalkIn(
  authFetch: AuthFetch,
  id: string,
  personKind: string,
  personId: string,
  dayValue: number,
) {
  return sendJson<SaveAttendanceResult>(
    authFetch,
    "POST",
    `${BASE}/sessions/${id}/walk-ins`,
    {
      personKind,
      personId,
      dayValue,
    },
  );
}

export function syncAttendance(
  authFetch: AuthFetch,
  id: string,
  entryId: string,
) {
  return sendJson<SaveAttendanceResult>(
    authFetch,
    "POST",
    `${BASE}/sessions/${id}/attendance/${entryId}/sync`,
    {},
  );
}

// ------------------------------------------------------------------ exceptions (US5)

export interface ExceptionRow {
  id: string;
  personKind: "TEACHER" | "MANAGER";
  personId: string;
  name: string;
  sessionId: string;
  topic: string;
  date: string;
  status: "OPEN" | "CLOSED" | "WITHDRAWN";
  closureKind: "EXCUSED" | "MADE_UP" | "ACKNOWLEDGED" | null;
  closureNote: string | null;
  makeupSessionId: string | null;
  closedBy: string | null;
  closedAt: string | null;
  raisedAt: string;
  version: number;
  repeat: boolean;
  countInWindow: number;
  managerId: string | null;
  managerName: string | null;
  zoneId: string | null;
  zoneName: string | null;
}

export interface ExceptionQuery {
  month?: string;
  zoneId?: string;
  managerId?: string;
  role?: string;
  status?: string;
  page?: number;
  size?: number;
}

export function getExceptions(
  authFetch: AuthFetch,
  query: ExceptionQuery,
): Promise<ApiResult<PageOf<ExceptionRow>>> {
  return getJson<PageOf<ExceptionRow>>(
    authFetch,
    `${BASE}/exceptions?${queryString({ ...query })}`,
    "Could not load the exceptions.",
  );
}

export function closeException(
  authFetch: AuthFetch,
  id: string,
  body: {
    kind: string;
    note?: string;
    makeupSessionId?: string;
    version: number;
  },
) {
  return sendJson<ExceptionRow>(
    authFetch,
    "POST",
    `${BASE}/exceptions/${id}/close`,
    body,
  );
}

export function exceptionsExportUrl(query: ExceptionQuery): string {
  return `${BASE}/exceptions/export?${queryString({ ...query })}`;
}

// ------------------------------------------------------------------ history (US6)

export interface HistoryEntry {
  type: "SESSION";
  sessionId: string;
  sessionType: SessionType;
  topic: string;
  date: string;
  sessionStatus: SessionStatus;
  role: string;
  outcome: Outcome | null;
  dayValue: number | null;
  reason: string | null;
  certificateNote: string | null;
  internalRemark: string | null;
  exceptionStatus: string | null;
  onLeave: boolean;
}

export interface InductionEntry {
  type: "INDUCTION";
  batchId: string;
  batchName: string;
  startsOn: string;
  endsOn: string;
  result: string | null;
  signedAt: string | null;
}

export interface TrainingHistory {
  person: { kind: string; id: string; name: string };
  year: number;
  entries: HistoryEntry[];
  induction: InductionEntry[];
  yearly: {
    expected: number;
    attended: number;
    expectedSessions: number;
    attendedSessions: number;
  };
}

export function getHistory(
  authFetch: AuthFetch,
  year: number,
  person?: { kind: string; id: string },
): Promise<ApiResult<TrainingHistory>> {
  const path = person
    ? `${BASE}/people/${person.kind}/${person.id}/history`
    : `${BASE}/me/history`;
  return getJson<TrainingHistory>(
    authFetch,
    `${path}?${queryString({ year })}`,
    "Could not load the training history.",
  );
}

// ------------------------------------------------------------------ upcoming (US7)

export interface UpcomingItem {
  label: "REQUIRED" | "REGISTERED" | "OPEN";
  session: SessionView;
}

export function getUpcoming(
  authFetch: AuthFetch,
): Promise<ApiResult<UpcomingItem[]>> {
  return getJson<UpcomingItem[]>(
    authFetch,
    `${BASE}/me/upcoming`,
    "Could not load your upcoming training.",
  );
}

// ------------------------------------------------------------------ people search (assign and walk-in pickers)

export interface FoundPerson {
  kind: "TEACHER" | "MANAGER";
  id: string;
  name: string;
}

export const PERSON_KIND_LABELS: Record<FoundPerson["kind"], string> = {
  TEACHER: "Teacher",
  MANAGER: "Manager",
};

/** Up to 20 people the caller may pick, by part of the name; a Manager only gets their own Teachers. */
export function searchPeople(
  authFetch: AuthFetch,
  query: string,
  kind?: FoundPerson["kind"],
): Promise<ApiResult<FoundPerson[]>> {
  return getJson<FoundPerson[]>(
    authFetch,
    `${BASE}/people?${queryString({ q: query.trim(), kind })}`,
    "Could not search for people.",
  );
}
