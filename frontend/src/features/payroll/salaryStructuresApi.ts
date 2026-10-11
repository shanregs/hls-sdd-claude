import {
  getJson,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

/** Salary structures and the pay policy (spec 013a, contracts/salary-structures-api.md). Amounts are strings. */
export type DesignationKind = "TEACHER" | "MANAGER";

export const KIND_LABELS: Record<DesignationKind, string> = {
  TEACHER: "Teachers",
  MANAGER: "Managers",
};

export interface InEffect {
  amount: string;
  effectiveOn: string;
}

export interface MissingSalary {
  flag: boolean;
  people: number;
  note: string | null;
}

export interface SalaryListEntry {
  designationId: string;
  name: string;
  kind: DesignationKind;
  retired: boolean;
  /** Null when no salary is on or before today: the screen says "no salary yet". */
  inEffect: InEffect | null;
  missingSalary: MissingSalary;
  /** Present on a Teacher designation only. */
  teacherDefaultNote?: string;
}

export interface Actor {
  id: string;
  name: string;
}

export interface SalaryRow {
  id: string;
  designationId: string;
  amount: string;
  effectiveOn: string;
  note: string | null;
  recordedBy: Actor;
  recordedAt: string;
}

export interface DesignationSalaryDetail {
  designationId: string;
  name: string;
  kind: DesignationKind;
  retired: boolean;
  inEffect: InEffect | null;
  missingSalary: MissingSalary;
  teacherDefaultNote?: string;
  history: SalaryRow[];
}

export interface PolicyVersion {
  id: string;
  effectiveOn: string;
  lopDivisor: string;
  halfDayFraction: string;
  rounding: RoundingRule;
  payMonth: string;
  note: string | null;
  /** Null for the version the installation starts with. */
  recordedBy: Actor | null;
  recordedAt: string;
}

export interface PolicyResponse {
  inEffect: PolicyVersion;
  history: PolicyVersion[];
}

export type RoundingRule = "NEAREST_RUPEE" | "UP" | "DOWN";

export const ROUNDING_LABELS: Record<RoundingRule, string> = {
  NEAREST_RUPEE: "Nearest rupee",
  UP: "Up to the next rupee",
  DOWN: "Down to the rupee",
};

export const DIVISOR_LABELS: Record<string, string> = {
  WORKING_DAYS: "The month's working days",
};

export const PAY_MONTH_LABELS: Record<string, string> = {
  CALENDAR_MONTH: "Calendar month",
};

const BASE = "/api/v1/salary-structures";

export function listSalaryStructures(
  authFetch: AuthFetch,
  includeRetired: boolean,
): Promise<ApiResult<SalaryListEntry[]>> {
  return getJson(
    authFetch,
    `${BASE}/designations?includeRetired=${includeRetired}`,
    "Could not load the salary structures.",
  );
}

export function getDesignationSalary(
  authFetch: AuthFetch,
  designationId: string,
): Promise<ApiResult<DesignationSalaryDetail>> {
  return getJson(
    authFetch,
    `${BASE}/designations/${designationId}`,
    "Could not load this designation's salary.",
  );
}

export function recordSalary(
  authFetch: AuthFetch,
  designationId: string,
  body: { amount: string; effectiveOn: string; note: string | null },
): Promise<ApiResult<SalaryRow>> {
  return sendJson(
    authFetch,
    "POST",
    `${BASE}/designations/${designationId}/salaries`,
    body,
  );
}

export function getPayPolicy(
  authFetch: AuthFetch,
): Promise<ApiResult<PolicyResponse>> {
  return getJson(authFetch, `${BASE}/policy`, "Could not load the pay policy.");
}

export interface PolicyInput {
  effectiveOn: string;
  lopDivisor: string;
  halfDayFraction: string;
  rounding: RoundingRule;
  payMonth: string;
  note: string | null;
}

export function recordPolicy(
  authFetch: AuthFetch,
  body: PolicyInput,
): Promise<ApiResult<PolicyVersion>> {
  return sendJson(authFetch, "POST", `${BASE}/policy`, body);
}
