import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Manager incentives (spec 030, contracts/incentive-api.md). Money is a number on the wire; dates are ISO. */
export const INCENTIVES_ROUTE = "/operations/incentives";
const BASE = "/api/v1/incentives";

export type IncentiveStatus =
  "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | "HELD" | "PAID_IN_RUN";

export const STATUS_LABELS: Record<IncentiveStatus, string> = {
  PENDING_APPROVAL: "Waiting for approval",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  HELD: "Held",
  PAID_IN_RUN: "Paid in a pay run",
};

export type IncentiveAction = "approve" | "reject" | "reopen";

export interface IncentiveRow {
  id: string;
  schoolId: string;
  schoolName: string;
  zoneName: string;
  earnerManagerId: string;
  earnerName: string;
  contractId: string;
  closureDate: string;
  amount: number;
  status: IncentiveStatus;
  mouChanged: boolean;
  holdReason: string | null;
  decidedById: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  approvedByEarner: boolean;
  rejectReason: string | null;
  runMonth: string | null;
  payrollStatus: string | null;
  version: number;
  actions: IncentiveAction[];
}

export interface HistoryEntry {
  kind: string;
  actor: string;
  detail: string | null;
  at: string;
}

export interface IncentiveDetail {
  incentive: IncentiveRow;
  history: HistoryEntry[];
}

export interface StatusTotal {
  status: IncentiveStatus;
  count: number;
  amount: number;
}

export interface ManagerMonthTotal {
  managerId: string;
  managerName: string;
  month: string;
  count: number;
  amount: number;
}

export interface IncentiveSummary {
  byStatus: StatusTotal[];
  byManagerMonth: ManagerMonthTotal[];
  total: number;
}

export interface NoEarnerRow {
  id: string;
  schoolId: string;
  schoolName: string;
  zoneName: string;
  contractId: string | null;
  reason: string;
  reasonText: string;
  detail: string | null;
  resolved: boolean;
  createdAt: string;
}

export interface IncentiveFilters {
  status?: string;
  month?: string;
  q?: string;
  sort?: string;
  dir?: "asc" | "desc";
  page?: number;
  size?: number;
}

export const listIncentives = (
  f: AuthFetch,
  filters: IncentiveFilters,
  mine = false,
): Promise<ApiResult<PageOf<IncentiveRow>>> =>
  getJson(
    f,
    `${BASE}${mine ? "/mine" : ""}?${queryString({ ...filters })}`,
    "Could not load the incentives.",
  );

export const getIncentive = (
  f: AuthFetch,
  id: string,
): Promise<ApiResult<IncentiveDetail>> =>
  getJson(f, `${BASE}/${id}`, "Could not load this incentive.");

export const getSummary = (
  f: AuthFetch,
  from?: string,
  to?: string,
): Promise<ApiResult<IncentiveSummary>> =>
  getJson(
    f,
    `${BASE}/summary?${queryString({ from, to })}`,
    "Could not load the totals.",
  );

export const getNoEarner = (f: AuthFetch): Promise<ApiResult<NoEarnerRow[]>> =>
  getJson(f, `${BASE}/no-earner`, "Could not load the Schools without an earner.");

export const approveIncentive = (
  f: AuthFetch,
  id: string,
  version: number,
): Promise<ApiResult<IncentiveDetail>> =>
  sendJson(f, "POST", `${BASE}/${id}/approve`, { version });

export const rejectIncentive = (
  f: AuthFetch,
  id: string,
  version: number,
  reason: string,
): Promise<ApiResult<IncentiveDetail>> =>
  sendJson(f, "POST", `${BASE}/${id}/reject`, { version, reason });

export const reopenIncentive = (
  f: AuthFetch,
  id: string,
  version: number,
  reason: string,
): Promise<ApiResult<IncentiveDetail>> =>
  sendJson(f, "POST", `${BASE}/${id}/reopen`, { version, reason });

/** Downloads the CSV for the filters through the signed-in session. */
export async function downloadCsv(
  f: AuthFetch,
  filters: IncentiveFilters,
): Promise<ApiResult<void>> {
  try {
    const response = await f(
      `${BASE}/export.csv?${queryString({ ...filters, page: undefined, size: undefined })}`,
    );
    if (!response.ok) {
      return {
        ok: false,
        reason: "Could not download the file.",
        status: response.status,
      };
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "incentives.csv";
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
    return { ok: true, data: undefined };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

/** The history line a person reads for one event kind. */
export const EVENT_LABELS: Record<string, string> = {
  EARNED: "Earned",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  REOPENED: "Reopened",
  OFFERED: "Offered to payroll",
  OFFER_REFUSED: "Payroll refused the offer",
  HELD: "Held back",
  RELEASED: "Released",
  APPLIED: "Paid in a pay run",
  MOU_CHANGED: "MoU changed during a pay run",
};
