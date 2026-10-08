import {
  getJson,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

export type DesignationKind = "TEACHER" | "MANAGER";

export const KIND_LABELS: Record<DesignationKind, string> = {
  TEACHER: "Teachers",
  MANAGER: "Managers",
};

export interface DesignationRow {
  id: string;
  name: string;
  kind: DesignationKind;
  retired: boolean;
  /** How many people hold it today. */
  holders: number;
  version: number;
}

export interface DesignationSummary {
  teachersMissingDesignation: number;
  managersMissingDesignation: number;
  managersMissingJoiningDate: number;
  managersMissingExitDate: number;
}

export function listDesignations(
  authFetch: AuthFetch,
): Promise<ApiResult<DesignationRow[]>> {
  return getJson(
    authFetch,
    "/api/v1/designations",
    "Could not load designations.",
  );
}

export function getDesignationSummary(
  authFetch: AuthFetch,
): Promise<ApiResult<DesignationSummary>> {
  return getJson(
    authFetch,
    "/api/v1/designations/summary",
    "Could not load the missing-details counts.",
  );
}

/** Active designations of one kind: the choice offered when setting a person's designation. */
export function listDesignationOptions(
  authFetch: AuthFetch,
  kind: DesignationKind,
): Promise<ApiResult<DesignationRow[]>> {
  return getJson(
    authFetch,
    `/api/v1/designations/options?kind=${kind}`,
    "Could not load the designations.",
  );
}

export function createDesignation(
  authFetch: AuthFetch,
  name: string,
  kind: DesignationKind,
): Promise<ApiResult<DesignationRow>> {
  return sendJson(authFetch, "POST", "/api/v1/designations", { name, kind });
}

export function updateDesignation(
  authFetch: AuthFetch,
  row: DesignationRow,
  patch: { name?: string; retired?: boolean },
): Promise<ApiResult<DesignationRow>> {
  return sendJson(authFetch, "PUT", `/api/v1/designations/${row.id}`, {
    ...patch,
    version: row.version,
  });
}
