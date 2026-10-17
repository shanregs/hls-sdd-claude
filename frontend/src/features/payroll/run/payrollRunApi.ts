import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../../common/masterDataApi";

/**
 * The payroll run endpoints (spec 013b, contracts/payroll-run-api.md). Money is a decimal string with two places
 * (`"12500.00"`), months are `yyyy-MM`, dates are ISO strings; the screens format them (Indian digit grouping,
 * DD/MM/YYYY) and never do arithmetic on the strings.
 */
export type RunStatus =
  "DRAFT" | "AWAITING_APPROVAL" | "APPROVED" | "CANCELLED";
export type ComputeState = "QUEUED" | "RUNNING" | "DONE" | "FAILED";
export type RunKind = "PRIMARY" | "SUPPLEMENTARY";
export type PersonKind = "TEACHER" | "MANAGER" | "STAFF";
export type LineState = "PAYABLE" | "CANNOT_PAY";

export const STATUS_LABELS: Record<RunStatus, string> = {
  DRAFT: "Draft",
  AWAITING_APPROVAL: "Awaiting approval",
  APPROVED: "Approved",
  CANCELLED: "Cancelled",
};

export const KIND_LABELS: Record<RunKind, string> = {
  PRIMARY: "Primary run",
  SUPPLEMENTARY: "Supplementary run",
};

export const PERSON_KIND_LABELS: Record<PersonKind, string> = {
  TEACHER: "Teacher",
  MANAGER: "Manager",
  STAFF: "Staff",
};

export interface Person {
  id: string;
  name: string;
}

export interface Totals {
  earned: string;
  deduction: string;
  adjustments: string;
  net: string;
}

export interface RecomputeEntry {
  kind: PersonKind;
  personId: string;
  name: string;
  reason?: string;
  oldNet?: string;
  newNet?: string;
  from?: LineState;
  to?: LineState;
  state?: LineState;
  net?: string | null;
}

/** What the last "Compute again" changed (FR-008). */
export interface RecomputeSummary {
  added: RecomputeEntry[];
  removed: RecomputeEntry[];
  changed: RecomputeEntry[];
  moved: RecomputeEntry[];
}

export interface RunEvent {
  at: string;
  actor: Person | null;
  kind: string;
  reason: string | null;
  detail: unknown;
}

export interface Run {
  id: string;
  month: string;
  kind: RunKind;
  sequence: number;
  status: RunStatus;
  computeState: ComputeState;
  /** Computing, but the heartbeat is older than ten minutes: "Compute again" is offered. */
  stalled: boolean;
  progressDone: number;
  progressTotal: number;
  totals: Totals;
  payableCount: number;
  cannotPayCount: number;
  paidCount: number;
  unpaidCount: number;
  paidAmount: string;
  unpaidAmount: string;
  startedBy: Person | null;
  startedAt: string;
  submittedBy: Person | null;
  submittedAt: string | null;
  approvedBy: Person | null;
  approvedAt: string | null;
  cancelledBy: Person | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  policy: {
    id: string;
    effectiveOn: string;
    rounding: string;
    halfDayFraction: string;
  } | null;
  version: number;
  /** Only on the single-run read. */
  events: RunEvent[] | null;
  recomputeSummary: RecomputeSummary | null;
  /** Waiting adjustments recorded after this draft was computed; they join when it is computed again. */
  adjustmentsWaitingSinceComputed: number;
}

export interface AdjustmentLine {
  adjustmentId: string;
  kind: string;
  amount: string;
  reason: string;
}

/** One line. A person who cannot be paid has a reason and `null` amounts, never zero. */
export interface Line {
  id: string;
  personKind: PersonKind;
  personId: string;
  name: string;
  label: string | null;
  state: LineState;
  reason: string | null;
  payableDays: number | null;
  workingDays: string | null;
  unpaidDays: string | null;
  salary: string | null;
  earned: string | null;
  deduction: string | null;
  adjustments: string | null;
  net: string | null;
  warning: string | null;
  computedAt: string;
  /** Only on the single-line read: the frozen working the line was computed from. */
  working: unknown;
  adjustmentLines: AdjustmentLine[] | null;
  /** The newest payment entry of the line, or null when none was recorded. */
  payment: PaymentEntry | null;
}

const BASE = "/api/v1/payroll";

export function listRuns(
  authFetch: AuthFetch,
  filter: {
    month?: string;
    status?: RunStatus | "";
    page?: number;
    size?: number;
  } = {},
): Promise<ApiResult<PageOf<Run>>> {
  const query = queryString({
    month: filter.month,
    status: filter.status,
    page: filter.page,
    size: filter.size,
  });
  return getJson(
    authFetch,
    `${BASE}/runs${query ? `?${query}` : ""}`,
    "Could not load the pay runs.",
  );
}

export function startRun(
  authFetch: AuthFetch,
  month: string,
  supplementary = false,
): Promise<ApiResult<Run>> {
  return sendJson(authFetch, "POST", `${BASE}/runs`, { month, supplementary });
}

export function getRun(
  authFetch: AuthFetch,
  runId: string,
): Promise<ApiResult<Run>> {
  return getJson(
    authFetch,
    `${BASE}/runs/${runId}`,
    "Could not load this pay run.",
  );
}

export function recomputeRun(
  authFetch: AuthFetch,
  runId: string,
): Promise<ApiResult<Run>> {
  return sendJson(authFetch, "POST", `${BASE}/runs/${runId}/recompute`);
}

export function cancelRun(
  authFetch: AuthFetch,
  runId: string,
  reason: string,
): Promise<ApiResult<Run>> {
  return sendJson(authFetch, "POST", `${BASE}/runs/${runId}/cancel`, {
    reason,
  });
}

export function submitRun(
  authFetch: AuthFetch,
  runId: string,
): Promise<ApiResult<Run>> {
  return sendJson(authFetch, "POST", `${BASE}/runs/${runId}/submit`);
}

export function returnRun(
  authFetch: AuthFetch,
  runId: string,
  reason: string,
): Promise<ApiResult<Run>> {
  return sendJson(authFetch, "POST", `${BASE}/runs/${runId}/return`, {
    reason,
  });
}

export function approveRun(
  authFetch: AuthFetch,
  runId: string,
): Promise<ApiResult<Run>> {
  return sendJson(authFetch, "POST", `${BASE}/runs/${runId}/approve`);
}

export interface LineFilter {
  state?: LineState | "";
  kind?: PersonKind | "";
  query?: string;
  page?: number;
  size?: number;
}

export function listLines(
  authFetch: AuthFetch,
  runId: string,
  filter: LineFilter = {},
): Promise<ApiResult<PageOf<Line>>> {
  const query = queryString({
    state: filter.state,
    kind: filter.kind,
    query: filter.query,
    page: filter.page,
    size: filter.size,
  });
  return getJson(
    authFetch,
    `${BASE}/runs/${runId}/lines${query ? `?${query}` : ""}`,
    "Could not load the lines of this run.",
  );
}

export function getLine(
  authFetch: AuthFetch,
  runId: string,
  lineId: string,
): Promise<ApiResult<Line>> {
  return getJson(
    authFetch,
    `${BASE}/runs/${runId}/lines/${lineId}`,
    "Could not load this line.",
  );
}

export interface OwedPerson {
  kind: PersonKind;
  personId: string;
  name: string;
  reason: string;
  resolved: boolean;
}

export function listOwed(
  authFetch: AuthFetch,
  runId: string,
): Promise<ApiResult<OwedPerson[]>> {
  return getJson(
    authFetch,
    `${BASE}/runs/${runId}/owed`,
    "Could not load the people still to be paid.",
  );
}

// ------------------------------------------------------------------ payslips

export interface PayslipFile {
  blob: Blob;
  filename: string;
}

/** The file name the server chose ({@code Content-Disposition: attachment; filename="..."}), or the fallback. */
function fileNameOf(response: Response, fallback: string): string {
  const header = response.headers.get("Content-Disposition") ?? "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header);
  return match ? decodeURIComponent(match[1]) : fallback;
}

async function fetchFile(
  authFetch: AuthFetch,
  url: string,
  fallbackName: string,
  failure: string,
): Promise<ApiResult<PayslipFile>> {
  try {
    const response = await authFetch(url);
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
    return {
      ok: true,
      data: {
        blob: await response.blob(),
        filename: fileNameOf(response, fallbackName),
      },
    };
  } catch {
    return { ok: false, reason: failure };
  }
}

/** One person's payslip of an approved run (permission View); the PDF as issued at approval. */
export function downloadPayslip(
  authFetch: AuthFetch,
  runId: string,
  lineId: string,
): Promise<ApiResult<PayslipFile>> {
  return fetchFile(
    authFetch,
    `${BASE}/runs/${runId}/lines/${lineId}/payslip`,
    "payslip.pdf",
    "Could not download the payslip.",
  );
}

/** Every payslip of an approved run, as one zip. */
export function downloadAllPayslips(
  authFetch: AuthFetch,
  runId: string,
): Promise<ApiResult<PayslipFile>> {
  return fetchFile(
    authFetch,
    `${BASE}/runs/${runId}/payslips`,
    "payslips.zip",
    "Could not download the payslips.",
  );
}

/** The run as a CSV for the accountant (permission Export); a draft says so in the file itself. */
export function downloadExport(
  authFetch: AuthFetch,
  runId: string,
): Promise<ApiResult<PayslipFile>> {
  return fetchFile(
    authFetch,
    `${BASE}/runs/${runId}/export`,
    "payroll.csv",
    "Could not export this run.",
  );
}

/** Hands a downloaded file to the browser's save dialog. */
export function saveFile(file: PayslipFile): void {
  const url = URL.createObjectURL(file.blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = file.filename;
  link.click();
  URL.revokeObjectURL(url);
}

// ------------------------------------------------------------------ adjustments

export type AdjustmentKind =
  "CORRECTION" | "INCENTIVE" | "REIMBURSEMENT" | "SUBSTITUTE_CREDIT";
export type AdjustmentStatus = "WAITING" | "APPLIED" | "WITHDRAWN";

export interface Adjustment {
  id: string;
  personKind: PersonKind;
  personId: string;
  personName: string;
  kind: AdjustmentKind;
  amount: string;
  reason: string;
  status: AdjustmentStatus;
  attachedRunId: string | null;
  appliedRunId: string | null;
  appliedLineId: string | null;
  createdBy: Person | null;
  createdAt: string;
  withdrawnBy: Person | null;
  withdrawnAt: string | null;
  withdrawReason: string | null;
}

export function listAdjustments(
  authFetch: AuthFetch,
  filter: {
    personKind?: PersonKind;
    personId?: string;
    status?: AdjustmentStatus | "";
    page?: number;
  } = {},
): Promise<ApiResult<PageOf<Adjustment>>> {
  const query = queryString(filter);
  return getJson(
    authFetch,
    `${BASE}/adjustments${query ? `?${query}` : ""}`,
    "Could not load the adjustments.",
  );
}

export function recordAdjustment(
  authFetch: AuthFetch,
  body: {
    personKind: PersonKind;
    personId: string;
    kind: AdjustmentKind;
    amount: string;
    reason: string;
  },
): Promise<ApiResult<Adjustment>> {
  return sendJson(authFetch, "POST", `${BASE}/adjustments`, body);
}

export function withdrawAdjustment(
  authFetch: AuthFetch,
  adjustmentId: string,
  reason: string,
): Promise<ApiResult<Adjustment>> {
  return sendJson(
    authFetch,
    "POST",
    `${BASE}/adjustments/${adjustmentId}/withdraw`,
    {
      reason,
    },
  );
}

// ---------------------------------------------------------------------- payment

export type PaymentMode = "BANK_TRANSFER" | "CASH" | "UPI" | "CHEQUE" | "OTHER";

export const PAYMENT_MODE_LABELS: Record<PaymentMode, string> = {
  BANK_TRANSFER: "Bank transfer",
  CASH: "Cash",
  UPI: "UPI",
  CHEQUE: "Cheque",
  OTHER: "Other",
};

export interface PaymentEntry {
  id: string;
  paid: boolean;
  paidOn: string | null;
  mode: PaymentMode | null;
  reference: string | null;
  enteredBy: string;
  enteredByName: string;
  enteredAt: string;
}

export interface Payment {
  lineId: string;
  current: PaymentEntry | null;
  earlier: PaymentEntry[];
}

export function getPayment(
  authFetch: AuthFetch,
  runId: string,
  lineId: string,
): Promise<ApiResult<Payment>> {
  return getJson(
    authFetch,
    `${BASE}/runs/${runId}/lines/${lineId}/payment`,
    "Could not load the payment details.",
  );
}

export function recordPayment(
  authFetch: AuthFetch,
  runId: string,
  lineId: string,
  body: {
    paid: boolean;
    paidOn: string | null;
    mode: PaymentMode | null;
    reference: string | null;
  },
): Promise<ApiResult<Payment>> {
  return sendJson(
    authFetch,
    "PUT",
    `${BASE}/runs/${runId}/lines/${lineId}/payment`,
    body,
  );
}

// ------------------------------------------------------- payslips, export, history

/** The URLs of the files; the caller downloads them with `authFetch` (they need the access token). */
export const payslipUrl = (runId: string, lineId: string) =>
  `${BASE}/runs/${runId}/lines/${lineId}/payslip`;
export const payslipsZipUrl = (runId: string) =>
  `${BASE}/runs/${runId}/payslips`;
export const exportUrl = (runId: string) => `${BASE}/runs/${runId}/export`;

export interface HistoryEntry {
  runId: string;
  lineId: string;
  month: string;
  runKind: RunKind;
  sequence: number;
  status: RunStatus;
  state: LineState;
  reason: string | null;
  net: string | null;
  payment: PaymentEntry | null;
}

export function getPersonHistory(
  authFetch: AuthFetch,
  kind: PersonKind,
  personId: string,
): Promise<ApiResult<HistoryEntry[]>> {
  return getJson(
    authFetch,
    `${BASE}/people/${kind}/${personId}/history`,
    "Could not load this person's payroll history.",
  );
}
