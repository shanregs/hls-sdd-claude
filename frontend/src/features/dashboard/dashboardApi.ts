import {
  getJson,
  queryString,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

/** Management Dashboard (spec 028, contracts/dashboard-api.md). Types mirror the JSON the server sends. */
export type AreaKey =
  | "MANAGERS"
  | "SCHOOLS"
  | "RECRUITMENT"
  | "TEACHERS"
  | "ATTENDANCE_LEAVE"
  | "PAYROLL";

export type PeriodKind = "TODAY" | "MONTH" | "ALL_TIME";

export type TileState =
  "OK" | "NO_DATA" | "NOT_AVAILABLE" | "NOT_WORKING_DAY" | "ERROR";

export type Unit = "COUNT" | "RUPEES" | "PERCENT" | "TEXT";

export interface TileLink {
  path: string;
  query: Record<string, string>;
}

export interface TileValue {
  label: string;
  value: number | string;
  unit: Unit;
  link: TileLink | null;
}

export interface TileTable {
  columns: string[];
  rows: TileValue[][];
}

export interface TileResult {
  key: string;
  state: TileState;
  values: TileValue[];
  organizationWide: boolean;
  note: string | null;
  takenAt: string | null;
  table: TileTable | null;
}

export interface TileDefinition {
  key: string;
  label: string;
  periodKind: PeriodKind;
  zoneAware: boolean;
  link: TileLink | null;
}

export interface ManifestArea {
  area: AreaKey;
  title: string;
  tiles: TileDefinition[];
}

export interface Manifest {
  areas: ManifestArea[];
  filters: {
    zones: { id: string; name: string }[];
    defaultPeriod: string | null;
  };
  inbox: boolean;
}

export interface AreaResponse {
  area: AreaKey;
  takenAt: string;
  tiles: TileResult[];
}

export interface ManagerTableRow {
  managerId: string;
  name: string;
  figures: Record<string, number | null>;
}

export interface ManagerTable {
  columns: { key: string; label: string; linkTemplate: string | null }[];
  rows: ManagerTableRow[];
}

export interface InboxItem {
  kind: string;
  itemId: string;
  requester: string;
  subject: string;
  askedOn: string;
  ageDays: number;
  link: TileLink;
}

export interface InboxResponse {
  totals: Record<string, number>;
  takenAt: string;
  page: number;
  size: number;
  total: number;
  items: InboxItem[];
}

export interface DashboardFilter {
  zoneId?: string;
  period?: string;
}

const BASE = "/api/v1/dashboard";

function withQuery(
  params: Record<string, string | number | boolean | undefined | null>,
): string {
  const query = queryString(params);
  return query === "" ? "" : `?${query}`;
}

export function fetchManifest(
  authFetch: AuthFetch,
): Promise<ApiResult<Manifest>> {
  return getJson<Manifest>(
    authFetch,
    BASE,
    "Could not load the Dashboard. Please try again.",
  );
}

export function fetchArea(
  authFetch: AuthFetch,
  area: AreaKey,
  filter: DashboardFilter = {},
): Promise<ApiResult<AreaResponse>> {
  return getJson<AreaResponse>(
    authFetch,
    `${BASE}/areas/${area}${withQuery({ ...filter })}`,
    "Could not load this area.",
  );
}

export function fetchTile(
  authFetch: AuthFetch,
  area: AreaKey,
  key: string,
  filter: DashboardFilter = {},
): Promise<ApiResult<TileResult>> {
  return getJson<TileResult>(
    authFetch,
    `${BASE}/areas/${area}/tiles/${encodeURIComponent(key)}${withQuery({ ...filter })}`,
    "Could not read this figure.",
  );
}

export function fetchManagers(
  authFetch: AuthFetch,
  params: DashboardFilter & { sort?: string; dir?: "asc" | "desc" } = {},
): Promise<ApiResult<ManagerTable>> {
  return getJson<ManagerTable>(
    authFetch,
    `${BASE}/managers${withQuery({ ...params })}`,
    "Could not load the Manager comparison.",
  );
}

export function fetchInbox(
  authFetch: AuthFetch,
  params: { kind?: string; page?: number; size?: number } = {},
): Promise<ApiResult<InboxResponse>> {
  return getJson<InboxResponse>(
    authFetch,
    `${BASE}/inbox${withQuery({ ...params })}`,
    "Could not load the approvals waiting for you.",
  );
}
