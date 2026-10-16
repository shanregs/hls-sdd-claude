import { GENERIC_FAILURE, type AuthFetch } from "../common/masterDataApi";

const BASE = "/api/v1/school-working-days";

/** One row of the grid (contracts/school-working-days-api.md). */
export interface WorkingDaysRow {
  id: string;
  schoolId: string;
  schoolName: string;
  zoneId: string | null;
  zoneName: string;
  year: number;
  month: number;
  workingDays: number;
  daysInMonth: number;
  calendarDerived: number;
  updatedBy: string;
  updatedAt: string;
  version: number;
}

export interface WorkingDaysPage {
  items: WorkingDaysRow[];
  total: number;
  page: number;
  size: number;
}

export interface SchoolOption {
  id: string;
  name: string;
  zoneId: string | null;
  zoneName: string;
  active: boolean;
}

export interface HistoryEntry {
  action: "CREATE" | "EDIT" | "DELETE" | "COPY";
  oldValue: number | null;
  newValue: number | null;
  actorName: string;
  actorRoles: string;
  occurredAt: string;
}

export interface DeletedEntry {
  month: number;
  lastValue: number;
  deletedByName: string;
  deletedAt: string;
}

export interface CopyResult {
  created: { month: number; workingDays: number }[];
  skipped: {
    month: number;
    workingDays: number;
    reason: "EXISTS" | "EXCEEDS_DAYS";
    daysInMonth?: number;
  }[];
}

export type Outcome<T> =
  | { ok: true; data: T }
  | {
      ok: false;
      status?: number;
      /** DUPLICATE, STALE or ROW_DELETED when the server sent one of those. */
      code?: string;
      reason: string;
      /** The row that blocked a create (DUPLICATE). */
      existing?: WorkingDaysRow;
      /** The row as it is now (STALE). */
      current?: WorkingDaysRow;
    };

export interface ListParams {
  year: number;
  schoolId?: string;
  zoneId?: string;
  q?: string;
  sort?: string;
  page: number;
  size: number;
}

async function call<T>(
  authFetch: AuthFetch,
  method: "GET" | "POST" | "PUT" | "DELETE",
  url: string,
  body?: unknown,
  failure = GENERIC_FAILURE,
): Promise<Outcome<T>> {
  try {
    const response = await authFetch(url, {
      method,
      headers:
        body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) {
      try {
        const payload = (await response.json()) as {
          code?: string;
          reason?: string;
          existing?: WorkingDaysRow;
          current?: WorkingDaysRow;
        };
        return {
          ok: false,
          status: response.status,
          code: payload.code,
          reason: payload.reason ?? failure,
          existing: payload.existing,
          current: payload.current,
        };
      } catch {
        return { ok: false, status: response.status, reason: failure };
      }
    }
    if (response.status === 204) {
      return { ok: true, data: undefined as T };
    }
    return { ok: true, data: (await response.json()) as T };
  } catch {
    return { ok: false, reason: failure };
  }
}

export function listWorkingDays(
  authFetch: AuthFetch,
  params: ListParams,
): Promise<Outcome<WorkingDaysPage>> {
  const query = new URLSearchParams({
    year: String(params.year),
    page: String(params.page),
    size: String(params.size),
  });
  if (params.schoolId) query.set("schoolId", params.schoolId);
  if (params.zoneId) query.set("zoneId", params.zoneId);
  if (params.q) query.set("q", params.q);
  if (params.sort) query.set("sort", params.sort);
  return call(
    authFetch,
    "GET",
    `${BASE}?${query.toString()}`,
    undefined,
    "Could not load the working days.",
  );
}

export function listSchoolOptions(
  authFetch: AuthFetch,
): Promise<Outcome<SchoolOption[]>> {
  return call(
    authFetch,
    "GET",
    `${BASE}/schools`,
    undefined,
    "Could not load the Schools.",
  );
}

export function createWorkingDays(
  authFetch: AuthFetch,
  body: { schoolId: string; year: number; month: number; workingDays: number },
): Promise<Outcome<WorkingDaysRow>> {
  return call(authFetch, "POST", BASE, body);
}

export function editWorkingDays(
  authFetch: AuthFetch,
  row: WorkingDaysRow,
  workingDays: number,
): Promise<Outcome<WorkingDaysRow>> {
  return call(authFetch, "PUT", `${BASE}/${row.id}`, {
    workingDays,
    version: row.version,
  });
}

export function deleteWorkingDays(
  authFetch: AuthFetch,
  row: WorkingDaysRow,
): Promise<Outcome<void>> {
  return call(authFetch, "DELETE", `${BASE}/${row.id}?version=${row.version}`);
}

export function rowHistory(
  authFetch: AuthFetch,
  row: WorkingDaysRow,
): Promise<Outcome<HistoryEntry[]>> {
  return call(
    authFetch,
    "GET",
    `${BASE}/history?schoolId=${row.schoolId}&year=${row.year}&month=${row.month}`,
    undefined,
    "Could not load the history.",
  );
}

export function deletedRows(
  authFetch: AuthFetch,
  schoolId: string,
  year: number,
): Promise<Outcome<DeletedEntry[]>> {
  return call(
    authFetch,
    "GET",
    `${BASE}/deleted?schoolId=${schoolId}&year=${year}`,
    undefined,
    "Could not load the deleted rows.",
  );
}

export function copyYear(
  authFetch: AuthFetch,
  body: { schoolId: string; fromYear: number; toYear: number },
): Promise<Outcome<CopyResult>> {
  return call(authFetch, "POST", `${BASE}/copy`, body);
}

/** One month of the year screen: the stored figure (the last five fields are null when none) and the hint. */
export interface YearMonthState {
  month: number;
  daysInMonth: number;
  calendarDerived: number;
  id: string | null;
  workingDays: number | null;
  version: number | null;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface YearView {
  schoolId: string;
  schoolName: string;
  zoneName: string;
  year: number;
  months: YearMonthState[];
}

export interface YearMonthRequest {
  month: number;
  workingDays: number;
  /** The version loaded for the month, null when the screen loaded no figure for it. */
  version: number | null;
}

export type YearMonthReason =
  | "INVALID_FIGURE"
  | "STALE"
  | "ALREADY_EXISTS"
  | "ROW_DELETED"
  | "NOT_PERMITTED";

export interface YearMonthResult {
  month: number;
  status: "CREATED" | "UPDATED" | "UNCHANGED" | "NOT_SAVED";
  reason?: YearMonthReason | null;
  message?: string | null;
  current: YearMonthState;
}

export interface YearSaveResult {
  created: number;
  updated: number;
  unchanged: number;
  notSaved: number;
  months: YearMonthResult[];
}

export function getYear(
  authFetch: AuthFetch,
  schoolId: string,
  year: number,
): Promise<Outcome<YearView>> {
  return call(
    authFetch,
    "GET",
    `${BASE}/year?schoolId=${schoolId}&year=${year}`,
    undefined,
    "Could not load the year.",
  );
}

export function saveYear(
  authFetch: AuthFetch,
  body: { schoolId: string; year: number; months: YearMonthRequest[] },
): Promise<Outcome<YearSaveResult>> {
  return call(
    authFetch,
    "PUT",
    `${BASE}/year`,
    body,
    "Could not save the year.",
  );
}
