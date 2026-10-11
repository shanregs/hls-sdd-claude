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
  /** Says the amount is only a suggested default, never anyone's pay. */
  defaultNote: string;
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
  defaultNote: string;
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

// ---------------------------------------------------------------- people (amendment 2026-10-11)

export type PersonKind = "TEACHER" | "MANAGER" | "STAFF";

export const PERSON_KIND_LABELS: Record<PersonKind, string> = {
  TEACHER: "Teacher",
  MANAGER: "Manager",
  STAFF: "Staff",
};

export interface PersonRow {
  kind: PersonKind;
  personId: string;
  name: string;
  /** The designation name, or null (staff have none). */
  designation: string | null;
  /** Null when the person has no salary: the screen says "no salary yet". */
  inEffect: InEffect | null;
  joiningDate: string | null;
  exitDate: string | null;
  active: boolean;
  /** False for a Teacher: their salary is changed on the Teachers screen. */
  salaryEditable: boolean;
  roles: string[];
}

export interface PersonPage {
  content: PersonRow[];
  page: number;
  size: number;
  totalElements: number;
}

export interface PersonSalaryRow {
  id: string;
  amount: string;
  effectiveOn: string;
  note: string | null;
  recordedBy: Actor;
  recordedAt: string;
}

export interface PersonDetail {
  person: PersonRow;
  /** The person's designation's suggested default, offered when entering their salary. */
  suggestedDefault: InEffect | null;
  history: PersonSalaryRow[];
}

export interface PeopleQuery {
  kind: PersonKind | "";
  q: string;
  withoutSalary: boolean;
  page: number;
  size: number;
}

export function listPeople(
  authFetch: AuthFetch,
  query: PeopleQuery,
): Promise<ApiResult<PersonPage>> {
  const params = new URLSearchParams();
  if (query.kind) params.set("kind", query.kind);
  if (query.q.trim()) params.set("q", query.q.trim());
  if (query.withoutSalary) params.set("withoutSalary", "true");
  params.set("page", String(query.page));
  params.set("size", String(query.size));
  return getJson(
    authFetch,
    `${BASE}/people?${params.toString()}`,
    "Could not load the people.",
  );
}

export function getPerson(
  authFetch: AuthFetch,
  kind: PersonKind,
  personId: string,
): Promise<ApiResult<PersonDetail>> {
  return getJson(
    authFetch,
    `${BASE}/people/${kind}/${personId}`,
    "Could not load this person's salary.",
  );
}

export function recordPersonSalary(
  authFetch: AuthFetch,
  kind: PersonKind,
  personId: string,
  body: { amount: string; effectiveOn: string; note: string | null },
): Promise<ApiResult<PersonSalaryRow>> {
  return sendJson(
    authFetch,
    "POST",
    `${BASE}/people/${kind}/${personId}/salaries`,
    body,
  );
}

export interface StaffDates {
  joiningDate: string | null;
  exitDate: string | null;
}

export function setStaffEmployment(
  authFetch: AuthFetch,
  personId: string,
  body: StaffDates,
): Promise<ApiResult<StaffDates>> {
  return sendJson(
    authFetch,
    "PUT",
    `${BASE}/people/STAFF/${personId}/employment`,
    body,
  );
}
