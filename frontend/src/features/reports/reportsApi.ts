import {
  getJson,
  queryString,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

/** Reports (spec 014a, `/api/v1/reports`). Types mirror the JSON the server sends; the screens never recompute a figure. */
export type CellState = "VALUE" | "NO_DATA" | "NOT_AVAILABLE" | "NOT_MATCHED";
export type Unit = "COUNT" | "DAYS" | "PERCENT" | "RUPEES" | "TEXT";
export type MonthState =
  "LOCKED" | "OPEN" | "DRAFT" | "APPROVED" | "PAID" | "NONE";
export type ReportFilterKey =
  | "ZONE"
  | "SCHOOL"
  | "TEACHER"
  | "MANAGER"
  | "SEASON";

export interface Cell {
  state: CellState;
  value: number | string | null;
}

export interface Column {
  key: string;
  label: string;
  unit: Unit;
}

export interface Row {
  id: string;
  label: string;
  cells: Cell[];
}

export interface Section {
  key: string;
  title: string;
  columns: Column[];
  rows: Row[];
  totals: Row | null;
  failed: boolean;
  note: string | null;
}

export interface ReportPageData {
  key: string;
  title: string;
  month: string;
  monthState: MonthState;
  sections: Section[];
  notes: string[];
}

export interface MenuItem {
  key: string;
  title: string;
  filters: ReportFilterKey[];
  hasMonthState: boolean;
  canExport: boolean;
  defaultMonth: string;
}

export interface Named {
  id: string;
  name: string;
}

export interface Menu {
  reports: MenuItem[];
  zones: Named[];
  schools: Named[];
}

export interface ReportFilters {
  month?: string;
  zoneId?: string;
  schoolId?: string;
  teacherId?: string;
  managerId?: string;
  /** A recruitment season label (spec 014c); a report that offers it has no month. */
  season?: string;
}

const BASE = "/api/v1/reports";

export function getMenu(authFetch: AuthFetch): Promise<ApiResult<Menu>> {
  return getJson<Menu>(authFetch, BASE, "Could not load the reports.");
}

export function getReport(
  authFetch: AuthFetch,
  key: string,
  filters: ReportFilters,
): Promise<ApiResult<ReportPageData>> {
  const query = queryString({ ...filters });
  return getJson<ReportPageData>(
    authFetch,
    `${BASE}/${key}${query ? `?${query}` : ""}`,
    "Could not load this report.",
  );
}

export interface ReportFile {
  blob: Blob;
  filename: string;
}

/** The CSV of the same page, with the same month and filters (permission Export). */
export async function downloadReport(
  authFetch: AuthFetch,
  key: string,
  filters: ReportFilters,
): Promise<ApiResult<ReportFile>> {
  const failure = "Could not export this report.";
  const query = queryString({ ...filters });
  try {
    const response = await authFetch(
      `${BASE}/${key}/export${query ? `?${query}` : ""}`,
    );
    if (!response.ok) {
      const body = (await response.json().catch(() => ({}))) as {
        reason?: string;
      };
      return {
        ok: false,
        reason: body.reason ?? failure,
        status: response.status,
      };
    }
    const header = response.headers.get("Content-Disposition") ?? "";
    const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header);
    return {
      ok: true,
      data: {
        blob: await response.blob(),
        filename: match ? decodeURIComponent(match[1]) : `${key}-report.csv`,
      },
    };
  } catch {
    return { ok: false, reason: failure };
  }
}

/** Hands a downloaded file to the browser save dialog. */
export function saveReportFile(file: ReportFile): void {
  const url = URL.createObjectURL(file.blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = file.filename;
  link.click();
  URL.revokeObjectURL(url);
}
