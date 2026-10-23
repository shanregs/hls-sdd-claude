import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Teacher transfers and replacements (spec 027, contracts/transfer-api.md). Dates are ISO strings on the wire. */
export const TRANSFERS_ROUTE = "/operations/transfers";

const BASE = "/api/v1/transfers";

export type MovementKind = "TRANSFER" | "REPLACEMENT";
export type MovementStatus =
  | "PENDING"
  | "APPROVED"
  | "APPLIED"
  | "REJECTED"
  | "CANCELLED";
export type MovementAction = "APPROVE" | "REJECT" | "CANCEL" | "CHANGE";

export const KIND_LABELS: Record<MovementKind, string> = {
  TRANSFER: "Transfer",
  REPLACEMENT: "Replacement",
};

export const STATUS_LABELS: Record<MovementStatus, string> = {
  PENDING: "Pending",
  APPROVED: "Approved",
  APPLIED: "Applied",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

export const REASON_LABELS: Record<string, string> = {
  SCHOOL_REQUEST: "School request",
  TEACHER_REQUEST: "Teacher request",
  MANAGER_REBALANCING: "Manager rebalancing",
  AFTER_REVIEW: "After a review",
  OTHER: "Other",
};

/** Reasons that need a written note (spec 027 FR-003). */
export const NOTE_REQUIRED = ["SCHOOL_REQUEST", "OTHER"];

export interface Ref {
  id: string;
  name: string | null;
}

export interface CheckLine {
  ok: boolean;
  text: string;
}

export interface HistoryEntry {
  action: string;
  fromStatus: string | null;
  toStatus: string | null;
  actor: string | null;
  note: string | null;
  at: string;
}

/** The request object of contracts/transfer-api.md. */
export interface MovementRequest {
  id: string;
  kind: MovementKind;
  status: MovementStatus;
  statusNote: string | null;
  teacher: Ref;
  fromSchool: Ref;
  toSchool: Ref;
  position: { id: string; number: number | null } | null;
  replacementTeacher: Ref | null;
  outgoingDestinationSchool: Ref | null;
  effectiveOn: string;
  reasonCategory: string;
  note: string | null;
  requestedBy: Ref;
  requestedAt: string;
  decidedBy: Ref | null;
  decidedAt: string | null;
  decisionReason: string | null;
  version: number;
  actions: MovementAction[];
  checks: CheckLine[];
  history: HistoryEntry[];
}

export interface TransferBody {
  teacherId: string;
  toSchoolId: string;
  positionId?: string | null;
  effectiveOn: string;
  reasonCategory: string;
  note?: string | null;
}

export interface ReplacementBody {
  teacherId: string;
  replacementTeacherId: string;
  effectiveOn: string;
  outgoingDestinationSchoolId?: string | null;
  reasonCategory: string;
  note?: string | null;
}

export type CheckBody = {
  kind: MovementKind;
  teacherId: string;
  toSchoolId?: string | null;
  positionId?: string | null;
  replacementTeacherId?: string | null;
  outgoingDestinationSchoolId?: string | null;
  effectiveOn: string;
};

export interface CheckResult {
  ok: boolean;
  problems: string[];
}

export interface Vacancy {
  positionId: string;
  number: number;
  salary: number | null;
}

export interface Candidate {
  id: string;
  name: string;
  status: string;
}

export interface MovementLine {
  teacher: Ref;
  school: Ref;
  positionNumber: number | null;
  from: string | null;
  to: string | null;
  kind: "INITIAL" | "DIRECT" | MovementKind;
  requestId: string | null;
  reason: string | null;
  requestedBy: string | null;
  approvedBy: string | null;
  otherTeacher: Ref | null;
  otherSchool: Ref | null;
}

export interface MovementFilters {
  status?: string;
  schoolId?: string;
  from?: string;
  to?: string;
  page?: number;
}

export function listRequests(
  authFetch: AuthFetch,
  filters: MovementFilters,
): Promise<ApiResult<PageOf<MovementRequest>>> {
  return getJson(
    authFetch,
    `${BASE}?${queryString({ size: 50, ...filters })}`,
    "Could not load the requests.",
  );
}

export function getRequest(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<MovementRequest>> {
  return getJson(authFetch, `${BASE}/${id}`, "Could not load this request.");
}

export function raiseTransfer(
  authFetch: AuthFetch,
  body: TransferBody,
): Promise<ApiResult<MovementRequest>> {
  return sendJson(authFetch, "POST", `${BASE}/transfers`, body);
}

export function raiseReplacement(
  authFetch: AuthFetch,
  body: ReplacementBody,
): Promise<ApiResult<MovementRequest>> {
  return sendJson(authFetch, "POST", `${BASE}/replacements`, body);
}

export function checkRequest(
  authFetch: AuthFetch,
  body: CheckBody,
): Promise<ApiResult<CheckResult>> {
  return sendJson(authFetch, "POST", `${BASE}/check`, body);
}

export function listVacancies(
  authFetch: AuthFetch,
  schoolId: string,
  on: string,
): Promise<ApiResult<Vacancy[]>> {
  return getJson(
    authFetch,
    `${BASE}/vacancies?${queryString({ schoolId, on })}`,
    "Could not load the vacant positions.",
  );
}

export function listCandidates(
  authFetch: AuthFetch,
  schoolId: string,
  on: string,
): Promise<ApiResult<Candidate[]>> {
  return getJson(
    authFetch,
    `${BASE}/candidates?${queryString({ schoolId, on })}`,
    "Could not load the Teachers who can replace.",
  );
}

export async function changeRequest(
  authFetch: AuthFetch,
  id: string,
  body: {
    version: number;
    effectiveOn?: string | null;
    positionId?: string | null;
    outgoingDestinationSchoolId?: string | null;
    note?: string | null;
  },
): Promise<ApiResult<MovementRequest>> {
  try {
    const response = await authFetch(`${BASE}/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      return await failureOf(response);
    }
    return { ok: true, data: (await response.json()) as MovementRequest };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

async function failureOf(response: Response): Promise<ApiResult<never>> {
  try {
    const body = (await response.json()) as {
      reason?: string;
      problems?: string[];
    };
    const reason =
      body.problems && body.problems.length > 0
        ? body.problems.join(" ")
        : (body.reason ?? GENERIC_FAILURE);
    return { ok: false, reason, status: response.status };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE, status: response.status };
  }
}

export function approveRequest(
  authFetch: AuthFetch,
  id: string,
  version: number,
): Promise<ApiResult<MovementRequest>> {
  return sendJson(authFetch, "POST", `${BASE}/${id}/approve`, { version });
}

export function rejectRequest(
  authFetch: AuthFetch,
  id: string,
  version: number,
  reason: string,
): Promise<ApiResult<MovementRequest>> {
  return sendJson(authFetch, "POST", `${BASE}/${id}/reject`, {
    version,
    reason,
  });
}

export function cancelRequest(
  authFetch: AuthFetch,
  id: string,
  version: number,
  reason: string,
): Promise<ApiResult<MovementRequest>> {
  return sendJson(authFetch, "POST", `${BASE}/${id}/cancel`, {
    version,
    reason,
  });
}

export function teacherMovements(
  authFetch: AuthFetch,
  teacherId: string,
): Promise<ApiResult<MovementLine[]>> {
  return getJson(
    authFetch,
    `${BASE}/teachers/${teacherId}/movements`,
    "Could not load the movement history.",
  );
}

export function schoolMovements(
  authFetch: AuthFetch,
  schoolId: string,
): Promise<ApiResult<MovementLine[]>> {
  return getJson(
    authFetch,
    `${BASE}/schools/${schoolId}/movements`,
    "Could not load the movement history.",
  );
}
