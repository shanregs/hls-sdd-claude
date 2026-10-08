import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

export type TeacherStatus = "IN_TRAINING" | "ACTIVE" | "ON_LEAVE" | "EXITED";

export const STATUS_LABELS: Record<TeacherStatus, string> = {
  IN_TRAINING: "In training",
  ACTIVE: "Active",
  ON_LEAVE: "On leave",
  EXITED: "Exited",
};

export interface PlacementRow {
  schoolId: string;
  schoolName: string;
  startsOn: string;
  endsOn: string | null;
  status: "ACTIVE" | "CANCELLED" | "CORRECTED";
  /** The position of the School's contract the Teacher fills; null while not mapped to one. */
  positionNumber: number | null;
}

/** Employment details of a Teacher (spec 005a); null on My Profile and absent before the feature. */
export interface TeacherEmployment {
  employeeId: string | null;
  designation: { id: string; name: string; retired: boolean } | null;
  /** DESIGNATION when none is recorded. */
  missing: string[];
}

export interface TeacherSummary {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  status: TeacherStatus;
  statusEffectiveOn: string;
  allowedNextStatuses: TeacherStatus[];
  userId: string | null;
  version: number;
  school: { id: string; name: string } | null;
  manager: { id: string; displayName: string } | null;
  pendingPlacement: {
    schoolId: string;
    schoolName: string;
    startsOn: string;
  } | null;
  placements: PlacementRow[] | null;
  employment?: TeacherEmployment | null;
}

export interface TeacherContact {
  name: string;
  phone: string;
  email: string;
  address: string;
}

export interface TeacherListParams {
  query: string;
  status: "" | TeacherStatus;
  missingDesignation?: boolean;
  page: number;
  size: number;
}

export function listTeachers(
  authFetch: AuthFetch,
  params: TeacherListParams,
): Promise<ApiResult<PageOf<TeacherSummary>>> {
  return getJson(
    authFetch,
    `/api/v1/teachers?${queryString({
      query: params.query.trim(),
      status: params.status,
      missingDesignation: params.missingDesignation || undefined,
      page: params.page,
      size: params.size,
    })}`,
    "Could not load teachers.",
  );
}

export function getTeacher(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<TeacherSummary>> {
  return getJson(
    authFetch,
    `/api/v1/teachers/${id}`,
    "Could not load this teacher.",
  );
}

export function createTeacher(
  authFetch: AuthFetch,
  contact: TeacherContact,
  status: TeacherStatus,
): Promise<ApiResult<TeacherSummary>> {
  return sendJson(authFetch, "POST", "/api/v1/teachers", {
    ...contact,
    status,
  });
}

export function updateTeacher(
  authFetch: AuthFetch,
  teacher: TeacherSummary,
  contact: TeacherContact,
): Promise<ApiResult<TeacherSummary>> {
  return sendJson(authFetch, "PUT", `/api/v1/teachers/${teacher.id}`, {
    ...contact,
    version: teacher.version,
  });
}

export function changeTeacherStatus(
  authFetch: AuthFetch,
  teacherId: string,
  status: TeacherStatus,
  effectiveOn: string,
): Promise<ApiResult<TeacherSummary>> {
  return sendJson(authFetch, "POST", `/api/v1/teachers/${teacherId}/status`, {
    status,
    effectiveOn: effectiveOn || null,
  });
}

export function placeTeacher(
  authFetch: AuthFetch,
  teacherId: string,
  schoolId: string,
  effectiveOn: string,
  positionId?: string | null,
): Promise<ApiResult<TeacherSummary>> {
  return sendJson(
    authFetch,
    "POST",
    `/api/v1/teachers/${teacherId}/placements`,
    {
      schoolId,
      positionId: positionId ?? null,
      effectiveOn: effectiveOn || null,
    },
  );
}

export interface AccountCandidate {
  userId: string;
  displayName: string;
  phone: string;
}

export function listAccountCandidates(
  authFetch: AuthFetch,
): Promise<ApiResult<AccountCandidate[]>> {
  return getJson(
    authFetch,
    "/api/v1/teachers/candidates",
    "Could not load the available accounts.",
  );
}

export function linkTeacherAccount(
  authFetch: AuthFetch,
  teacherId: string,
  userId: string | null,
): Promise<ApiResult<TeacherSummary>> {
  return sendJson(authFetch, "PUT", `/api/v1/teachers/${teacherId}/user`, {
    userId,
  });
}

export function cancelScheduledPlacement(
  authFetch: AuthFetch,
  teacherId: string,
): Promise<ApiResult> {
  return sendJson(
    authFetch,
    "DELETE",
    `/api/v1/teachers/${teacherId}/placements/pending`,
    undefined,
    false,
  );
}

export function updateTeacherEmployment(
  authFetch: AuthFetch,
  teacher: TeacherSummary,
  values: { designationId: string | null; employeeId: string | null },
): Promise<ApiResult<TeacherSummary>> {
  return sendJson(
    authFetch,
    "PUT",
    `/api/v1/teachers/${teacher.id}/employment`,
    {
      ...values,
      version: teacher.version,
    },
  );
}
