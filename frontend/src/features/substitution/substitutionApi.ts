import {
  getJson,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Substitution API (spec 017a, contracts/substitution-api.md). Dates are "YYYY-MM-DD". */
export type RequirementStatus =
  "OPEN" | "ASSIGNED" | "MANAGER_COVER" | "CANCELLED";

export type InformMethod =
  "PHONE" | "IN_PERSON" | "EMAIL" | "MESSAGE" | "OTHER";

export const STATUS_LABELS: Record<RequirementStatus, string> = {
  OPEN: "Open",
  ASSIGNED: "Assigned",
  MANAGER_COVER: "Covered by Manager",
  CANCELLED: "Cancelled",
};

export const METHOD_LABELS: Record<InformMethod, string> = {
  PHONE: "Phone",
  IN_PERSON: "In person",
  EMAIL: "Email",
  MESSAGE: "Message",
  OTHER: "Other",
};

export interface Informed {
  at: string;
  byUserId: string;
  byName: string;
  contact: string;
  method: InformMethod;
  note: string | null;
}

export interface Requirement {
  id: string;
  teacherId: string;
  teacherName: string;
  schoolId: string;
  schoolName: string;
  dutyDate: string;
  dayValue: number;
  kind: "ABSENT" | "LEAVE";
  status: RequirementStatus;
  substituteTeacherId: string | null;
  substituteName: string | null;
  substituteNowUnavailable: boolean;
  coverName: string | null;
  informed: Informed | null;
  principalContact: string | null;
  note: string | null;
  version: number;
  /** Spec 040: the Manager cover was recorded on the duty day. */
  sameDay?: boolean;
}

/** Spec 040: one Manager cover entry, now or earlier; kept apart from Teacher credits. */
export interface ManagerCover {
  requirementId: string;
  date: string;
  schoolId: string;
  schoolName: string;
  replacedTeacherId: string;
  replacedTeacherName: string;
  managerName: string | null;
  recordedByName: string | null;
  dayValue: number;
  sameDay: boolean;
  state: "ACTIVE" | "ENDED";
  endedBy: "TEACHER_ASSIGNED" | "RELEASED" | "ABSENCE_WITHDRAWN" | null;
  label: string;
}

export interface HistoryEntry {
  action: string;
  fromStatus: string | null;
  toStatus: string;
  substituteName: string | null;
  actorName: string;
  note: string | null;
  at: string;
}

export interface Detail {
  requirement: Requirement;
  history: HistoryEntry[];
}

export interface Candidate {
  teacherId: string;
  name: string;
  available: boolean;
  reason: string | null;
  current: boolean;
}

export interface MyAssignment {
  id: string;
  schoolId: string;
  schoolName: string;
  dutyDate: string;
  dayValue: number;
  status: string;
}

export function listRequirements(
  authFetch: AuthFetch,
  params: {
    status: string;
    page: number;
    size: number;
    from?: string;
    to?: string;
    sameDay?: boolean;
  },
): Promise<ApiResult<PageOf<Requirement>>> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.sameDay) query.set("sameDay", "true");
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  query.set("page", String(params.page));
  query.set("size", String(params.size));
  return getJson(
    authFetch,
    `/api/v1/substitutions?${query.toString()}`,
    "Could not load the substitution requirements.",
  );
}

export function listManagerCovers(
  authFetch: AuthFetch,
  params: { sameDay?: boolean; page: number; size: number },
): Promise<ApiResult<PageOf<ManagerCover>>> {
  const query = new URLSearchParams();
  if (params.sameDay) query.set("sameDay", "true");
  query.set("page", String(params.page));
  query.set("size", String(params.size));
  return getJson(
    authFetch,
    `/api/v1/substitutions/manager-covers?${query.toString()}`,
    "Could not load the Manager covers.",
  );
}

export function getRequirement(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<Detail>> {
  return getJson(
    authFetch,
    `/api/v1/substitutions/${id}`,
    "Could not load this requirement.",
  );
}

export function getCandidates(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<Candidate[]>> {
  return getJson(
    authFetch,
    `/api/v1/substitutions/${id}/candidates`,
    "Could not load the Teachers.",
  );
}

export function assignSubstitute(
  authFetch: AuthFetch,
  id: string,
  substituteTeacherId: string,
  version: number,
): Promise<ApiResult<Requirement>> {
  return sendJson(authFetch, "POST", `/api/v1/substitutions/${id}/assign`, {
    substituteTeacherId,
    version,
  });
}

export function releaseRequirement(
  authFetch: AuthFetch,
  id: string,
  reason: string,
  version: number,
): Promise<ApiResult<Requirement>> {
  return sendJson(authFetch, "POST", `/api/v1/substitutions/${id}/release`, {
    reason,
    version,
  });
}

export function coverRequirement(
  authFetch: AuthFetch,
  id: string,
  note: string,
  version: number,
): Promise<ApiResult<Requirement>> {
  return sendJson(authFetch, "POST", `/api/v1/substitutions/${id}/cover`, {
    note,
    version,
  });
}

export function informSchool(
  authFetch: AuthFetch,
  id: string,
  body: {
    contact: string;
    method: InformMethod;
    note: string;
    version: number;
  },
): Promise<ApiResult<Requirement>> {
  return sendJson(
    authFetch,
    "POST",
    `/api/v1/substitutions/${id}/inform`,
    body,
  );
}

export function getMyAssignments(
  authFetch: AuthFetch,
): Promise<ApiResult<MyAssignment[]>> {
  return getJson(
    authFetch,
    "/api/v1/me/substitutions",
    "Could not load your substitutions.",
  );
}
