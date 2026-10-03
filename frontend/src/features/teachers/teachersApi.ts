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
  interim: boolean;
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
): Promise<ApiResult<TeacherSummary>> {
  return sendJson(
    authFetch,
    "POST",
    `/api/v1/teachers/${teacherId}/placements`,
    {
      schoolId,
      effectiveOn: effectiveOn || null,
    },
  );
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
