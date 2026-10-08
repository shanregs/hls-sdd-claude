import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Employment details of a Manager (spec 005a); absent on responses from before the feature. */
export interface ManagerEmployment {
  employeeId: string | null;
  joiningDate: string | null;
  exitDate: string | null;
  designation: { id: string; name: string; retired: boolean } | null;
  /** Newest first; filled on the detail endpoint only. */
  history?:
    | {
        designationId: string;
        name: string;
        effectiveOn: string;
        recordedAt: string;
      }[]
    | null;
  /** DESIGNATION, JOINING_DATE and EXIT_DATE for what is not recorded. */
  missing: string[];
}

export const MISSING_LABELS: Record<string, string> = {
  DESIGNATION: "designation missing",
  JOINING_DATE: "joining date missing",
  EXIT_DATE: "exit date missing",
};

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
  employment?: ManagerEmployment | null;
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
  missing = false,
): Promise<ApiResult<PageOf<ManagerSummary>>> {
  return getJson(
    authFetch,
    `/api/v1/managers?${queryString({ query: query.trim(), page, size, missing: missing || undefined })}`,
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

export function updateManagerEmployment(
  authFetch: AuthFetch,
  manager: ManagerSummary,
  values: {
    employeeId: string | null;
    joiningDate: string | null;
    exitDate: string | null;
  },
): Promise<ApiResult<ManagerSummary>> {
  return sendJson(
    authFetch,
    "PUT",
    `/api/v1/managers/${manager.id}/employment`,
    { ...values, version: manager.version },
  );
}

export function recordManagerDesignation(
  authFetch: AuthFetch,
  managerId: string,
  designationId: string,
  effectiveOn: string,
): Promise<ApiResult<ManagerSummary>> {
  return sendJson(
    authFetch,
    "POST",
    `/api/v1/managers/${managerId}/designation`,
    { designationId, effectiveOn },
  );
}

export function getManager(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<ManagerSummary>> {
  return getJson(
    authFetch,
    `/api/v1/managers/${id}`,
    "Could not load this manager.",
  );
}
