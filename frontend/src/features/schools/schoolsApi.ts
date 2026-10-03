import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

export interface SchoolSummary {
  id: string;
  name: string;
  place: { id: string; name: string; pinCode: string };
  zone: { id: string; name: string };
  address: string;
  contactPerson: string | null;
  contactPhone: string | null;
  billingContact: string | null;
  active: boolean;
  version: number;
  /** Contributed by the organization module. */
  manager?: { id: string; displayName: string } | null;
  needsManager?: boolean;
  /** Contributed by the teacher module. */
  teacherCount?: number;
}

export interface SchoolProfile {
  name: string;
  address: string;
  contactPerson: string;
  contactPhone: string;
  billingContact: string;
}

export interface SchoolListParams {
  query: string;
  zoneId: string;
  active: "" | "true" | "false";
  page: number;
  size: number;
}

export function listSchools(
  authFetch: AuthFetch,
  params: SchoolListParams,
): Promise<ApiResult<PageOf<SchoolSummary>>> {
  return getJson(
    authFetch,
    `/api/v1/schools?${queryString({
      query: params.query.trim(),
      zoneId: params.zoneId,
      active: params.active,
      page: params.page,
      size: params.size,
    })}`,
    "Could not load schools.",
  );
}

export function createSchool(
  authFetch: AuthFetch,
  placeId: string,
  profile: SchoolProfile,
): Promise<ApiResult<SchoolSummary>> {
  return sendJson(authFetch, "POST", "/api/v1/schools", {
    ...profile,
    placeId,
  });
}

export function updateSchool(
  authFetch: AuthFetch,
  school: SchoolSummary,
  profile: SchoolProfile,
): Promise<ApiResult<SchoolSummary>> {
  return sendJson(authFetch, "PUT", `/api/v1/schools/${school.id}`, {
    ...profile,
    version: school.version,
  });
}

export function changeSchoolPlace(
  authFetch: AuthFetch,
  school: SchoolSummary,
  placeId: string,
): Promise<ApiResult<SchoolSummary>> {
  return sendJson(authFetch, "PUT", `/api/v1/schools/${school.id}/place`, {
    placeId,
    version: school.version,
  });
}

export function setSchoolActive(
  authFetch: AuthFetch,
  schoolId: string,
  active: boolean,
): Promise<ApiResult> {
  return sendJson(
    authFetch,
    "POST",
    `/api/v1/schools/${schoolId}/${active ? "reactivate" : "deactivate"}`,
    undefined,
    false,
  );
}
