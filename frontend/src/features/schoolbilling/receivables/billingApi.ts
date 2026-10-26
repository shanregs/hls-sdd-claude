import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../../common/masterDataApi";

/** School Billing and Receivables (spec 022, contracts/receivables-api.md). Money is a number on the wire; dates are ISO. */
export const BILLING_ROUTE = "/operations/school-billing";

const BASE = "/api/v1/school-billing";

export type DueStatus = "OPEN" | "PART_PAID" | "PAID" | "CANCELLED";

export const STATUS_LABELS: Record<DueStatus, string> = {
  OPEN: "Open",
  PART_PAID: "Part paid",
  PAID: "Paid",
  CANCELLED: "Cancelled",
};

export const MODE_LABELS: Record<string, string> = {
  BANK: "Bank",
  CASH: "Cash",
  CHEQUE: "Cheque",
  UPI: "UPI",
};

export const NOT_BILLED_LABELS: Record<string, string> = {
  MOU_PENDING: "MoU pending",
  NO_POSITION: "Not mapped to a position",
  NO_ACTIVE_CONTRACT: "No contract covers the dates",
};

export interface DueRow {
  id: string;
  reference: string;
  schoolId: string;
  schoolName: string;
  month: string;
  raisedOn: string;
  dueOn: string;
  total: number;
  adjustments: number;
  collected: number;
  balance: number;
  status: DueStatus;
  overdue: boolean;
  daysOverdue: number;
  cancelReason: string | null;
}

export interface DueLine {
  id: string;
  teacherId: string;
  teacherName: string;
  positionNumber: number | null;
  amount: number;
  fullMonth: boolean;
  working: string;
}

export interface Adjustment {
  id: string;
  dueId: string;
  amount: number;
  reason: string;
  reversesId: string | null;
  reversed: boolean;
  createdAt: string;
}

export interface Allocation {
  id: string;
  dueReference: string;
  amount: number;
}

export interface DueDetail {
  due: DueRow;
  lines: DueLine[];
  adjustmentEntries: Adjustment[];
  allocations: Allocation[];
  followUpTaskId: string | null;
}

export interface Collection {
  id: string;
  schoolId: string;
  amount: number;
  receivedOn: string;
  mode: string;
  reference: string | null;
  receiver: string | null;
  reversesId: string | null;
  reversed: boolean;
  reason: string | null;
  allocated: number;
  unallocated: number;
}

export interface SchoolLedger {
  schoolId: string;
  schoolName: string;
  owed: number;
  collected: number;
  outstanding: number;
  overdue: number;
  advanceCredit: number;
  dues: DueRow[];
  collections: Collection[];
  adjustments: Adjustment[];
}

export interface OverviewRow {
  schoolId: string;
  schoolName: string;
  zoneName: string;
  managerName: string | null;
  owed: number;
  collected: number;
  outstanding: number;
  overdue: number;
  advanceCredit: number;
}

export interface Overview {
  month: string;
  owed: number;
  collected: number;
  outstanding: number;
  overdue: number;
  advanceCredit: number;
  schools: OverviewRow[];
}

export interface AgeingRow {
  schoolName: string | null;
  zoneName: string | null;
  managerName: string | null;
  notYetDue: number;
  b0to30: number;
  b31to60: number;
  b61to90: number;
  over90: number;
  total: number;
}

export interface Ageing {
  asOf: string;
  totals: AgeingRow;
  schools: AgeingRow[];
  zones: AgeingRow[];
  managers: AgeingRow[];
}

export interface NotBilledRow {
  schoolId: string;
  schoolName: string;
  teacherName: string;
  reason: string;
}

export interface RaiseResult {
  raised: DueRow[];
  skipped: { schoolId: string; schoolName: string; reason: string }[];
  notBilled: NotBilledRow[];
}

export interface Settings {
  paymentDueDays: number;
  escalationDays: number;
}

export function getOverview(authFetch: AuthFetch, month: string) {
  return getJson<Overview>(
    authFetch,
    `${BASE}/overview?${queryString({ month })}`,
    "Could not load the billing overview.",
  );
}

export function getAgeing(authFetch: AuthFetch) {
  return getJson<Ageing>(authFetch, `${BASE}/ageing`, "Could not load the ageing.");
}

export function getNotBilled(authFetch: AuthFetch, month: string) {
  return getJson<NotBilledRow[]>(
    authFetch,
    `${BASE}/not-billed?${queryString({ month })}`,
    "Could not load the not-billed list.",
  );
}

export function getLedger(authFetch: AuthFetch, schoolId: string) {
  return getJson<SchoolLedger>(
    authFetch,
    `${BASE}/schools/${schoolId}/ledger`,
    "Could not load this School's ledger.",
  );
}

export function getDue(authFetch: AuthFetch, id: string) {
  return getJson<DueDetail>(authFetch, `${BASE}/dues/${id}`, "Could not load this monthly due.");
}

export function getSettings(authFetch: AuthFetch) {
  return getJson<Settings>(authFetch, `${BASE}/settings`, "Could not load the settings.");
}

export function saveSettings(authFetch: AuthFetch, body: Settings) {
  return sendJson<Settings>(authFetch, "PUT", `${BASE}/settings`, body);
}

export function raiseDues(authFetch: AuthFetch, month: string) {
  return sendJson<RaiseResult>(authFetch, "POST", `${BASE}/dues/raise`, { month });
}

export function cancelDue(authFetch: AuthFetch, id: string, reason: string) {
  return sendJson<DueRow>(authFetch, "POST", `${BASE}/dues/${id}/cancel`, { reason });
}

export function addAdjustment(authFetch: AuthFetch, dueId: string, amount: string, reason: string) {
  return sendJson<Adjustment>(authFetch, "POST", `${BASE}/dues/${dueId}/adjustments`, { amount, reason });
}

export function reverseAdjustment(authFetch: AuthFetch, id: string, reason: string) {
  return sendJson<Adjustment>(authFetch, "POST", `${BASE}/adjustments/${id}/reverse`, { reason });
}

export interface CollectionBody {
  schoolId: string;
  amount: string;
  receivedOn: string;
  mode: string;
  reference: string;
  receiver: string;
  comment: string;
  confirmDuplicate?: boolean;
}

/** A likely duplicate comes back as a 409 with `warning`; the caller may repeat the call with confirmDuplicate. */
export async function recordCollection(
  authFetch: AuthFetch,
  body: CollectionBody,
): Promise<ApiResult<Collection> & { duplicate?: boolean }> {
  try {
    const response = await authFetch(`${BASE}/collections`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (response.ok) return { ok: true, data: (await response.json()) as Collection };
    const failure = (await response.json().catch(() => ({}))) as {
      reason?: string;
      warning?: string;
    };
    return {
      ok: false,
      reason: failure.reason ?? GENERIC_FAILURE,
      status: response.status,
      duplicate: failure.warning === "LIKELY_DUPLICATE",
    };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

export function allocateCollection(authFetch: AuthFetch, id: string) {
  return sendJson<Collection>(authFetch, "POST", `${BASE}/collections/${id}/allocate`, {});
}

export function reverseCollection(authFetch: AuthFetch, id: string, reason: string) {
  return sendJson<Collection>(authFetch, "POST", `${BASE}/collections/${id}/reverse`, { reason });
}

export async function downloadCsv(
  authFetch: AuthFetch,
  params: Record<string, string>,
): Promise<ApiResult<string>> {
  try {
    const response = await authFetch(`${BASE}/export?${queryString(params)}`);
    if (!response.ok) return { ok: false, reason: "Could not export." };
    return { ok: true, data: URL.createObjectURL(await response.blob()) };
  } catch {
    return { ok: false, reason: "Could not export." };
  }
}

export type { PageOf };
