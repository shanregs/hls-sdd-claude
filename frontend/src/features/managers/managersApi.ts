import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

export interface ManagerSummary {
  id: string;
  userId: string;
  displayName: string;
  phone: string | null;
  active: boolean;
  version: number;
  zones: { id: string; name: string }[];
  schoolCount: number;
  /** Contributed by the teacher module. */
  teacherCount?: number;
  history?: {
    kind: "ZONE_MANAGER" | "SCHOOL_MANAGER";
    targetId: string;
    targetName: string;
    startsOn: string;
    endsOn: string | null;
  }[];
}

export interface ManagerCandidate {
  userId: string;
  displayName: string;
  phone: string;
}

export function listManagers(
  authFetch: AuthFetch,
  query: string,
  page: number,
  size: number,
): Promise<ApiResult<PageOf<ManagerSummary>>> {
  return getJson(
    authFetch,
    `/api/v1/managers?${queryString({ query: query.trim(), page, size })}`,
    "Could not load managers.",
  );
}

export function listCandidates(
  authFetch: AuthFetch,
): Promise<ApiResult<ManagerCandidate[]>> {
  return getJson(
    authFetch,
    "/api/v1/managers/candidates",
    "Could not load the available users.",
  );
}

export function createManager(
  authFetch: AuthFetch,
  userId: string,
): Promise<ApiResult<ManagerSummary>> {
  return sendJson(authFetch, "POST", "/api/v1/managers", { userId });
}

export function setManagerZones(
  authFetch: AuthFetch,
  manager: ManagerSummary,
  zoneIds: string[],
): Promise<ApiResult<ManagerSummary>> {
  return sendJson(authFetch, "PUT", `/api/v1/managers/${manager.id}/zones`, {
    zoneIds,
    version: manager.version,
  });
}

export function assignSchoolManager(
  authFetch: AuthFetch,
  schoolId: string,
  managerId: string | null,
): Promise<ApiResult> {
  return sendJson(
    authFetch,
    "PUT",
    `/api/v1/schools/${schoolId}/manager`,
    { managerId },
    false,
  );
}
