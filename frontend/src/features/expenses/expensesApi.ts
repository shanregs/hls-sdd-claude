import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Expenses (spec 015, contracts/expenses-api.md). Money is a decimal string with two decimals; dates are ISO strings. */
export const MY_EXPENSES_ROUTE = "/operations/my-expenses";
export const EXPENSES_ROUTE = "/operations/expenses";

export type Category =
  | "RENT_UTILITIES"
  | "TRAVEL_FUEL"
  | "TRAINING_STAY"
  | "MARKETING"
  | "OFFICE_SUPPLIES"
  | "OTHER";

export const CATEGORY_LABELS: Record<Category, string> = {
  RENT_UTILITIES: "Rent and Utilities",
  TRAVEL_FUEL: "Travel and Fuel",
  TRAINING_STAY: "Training Stay",
  MARKETING: "Marketing",
  OFFICE_SUPPLIES: "Office Supplies",
  OTHER: "Other",
};

export type ClaimStatus =
  "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED" | "PAID" | "PAID_BY_COMPANY";

export const STATUS_LABELS: Record<ClaimStatus, string> = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  PAID: "Paid",
  PAID_BY_COMPANY: "Paid by company",
};

export type AllowedAction =
  | "EDIT"
  | "SUBMIT"
  | "WITHDRAW"
  | "DELETE"
  | "ADD_BILL"
  | "APPROVE"
  | "REJECT"
  | "REVERSE"
  | "RECORD_PAID"
  | "REMOVE_BILL";

export type PayrollState =
  "NONE" | "WAITING" | "HELD_BY_DRAFT" | "HELD_AWAITING_APPROVAL" | "APPLIED";

export const PAYROLL_STATE_TEXT: Record<PayrollState, string> = {
  NONE: "Not with payroll",
  WAITING: "Waiting for the next pay run",
  HELD_BY_DRAFT: "In a draft pay run",
  HELD_AWAITING_APPROVAL: "In a pay run awaiting approval",
  APPLIED: "Paid in an approved pay run",
};

export const PAID_MODES = ["CASH", "BANK_TRANSFER", "UPI", "OTHER"] as const;
export type PaidMode = (typeof PAID_MODES)[number];

export const MODE_LABELS: Record<PaidMode, string> = {
  CASH: "Cash",
  BANK_TRANSFER: "Bank transfer",
  UPI: "UPI",
  OTHER: "Other",
};

export interface LinkView {
  kind: string;
  kindLabel: string;
  ref: string;
  date: string | null;
  label: string | null;
}

export interface BillView {
  id: string;
  name: string;
  contentType: string | null;
  sizeBytes: number | null;
  addedBy: string;
  addedByName: string;
  addedAt: string;
  removed: boolean;
  removedByName: string | null;
  removedAt: string | null;
  removalReason: string | null;
}

export interface HistoryView {
  at: string;
  actorId: string | null;
  actorName: string;
  event: string;
  reason: string | null;
  detail: string | null;
}

export interface DecisionView {
  by: string;
  byName: string;
  at: string;
  reason: string | null;
}

export interface PaymentView {
  how: "PAYROLL" | "OUTSIDE";
  paidOn: string | null;
  mode: PaidMode | null;
  reference: string | null;
  payrollRunId: string | null;
  payrollMonth: string | null;
  recordedByName: string | null;
}

export interface ClaimDetail {
  id: string;
  claimantId: string;
  claimantName: string;
  expenseDate: string;
  amount: string;
  category: Category;
  categoryLabel: string;
  zoneId: string | null;
  areaName: string;
  note: string | null;
  paidBy: "CLAIMANT" | "COMPANY";
  reason: string | null;
  noBillReason: string | null;
  link: LinkView | null;
  status: ClaimStatus;
  statusLabel: string;
  submittedAt: string | null;
  ageDays: number | null;
  decision: DecisionView | null;
  payment: PaymentView | null;
  payrollState: PayrollState;
  bills: BillView[];
  history: HistoryView[];
  allowedActions: AllowedAction[];
  ownClaim: boolean;
  version: number;
}

export interface ClaimRow {
  id: string;
  claimantId: string;
  claimantName: string;
  expenseDate: string;
  amount: string;
  category: Category;
  categoryLabel: string;
  zoneId: string | null;
  areaName: string;
  status: ClaimStatus;
  statusLabel: string;
  paidBy: string;
  billCount: number;
  noBillReason: string | null;
  linkLabel: string | null;
  note: string | null;
  submittedAt: string | null;
  ageDays: number | null;
  remainingBudget: string | null;
  allowedActions: AllowedAction[];
}

export interface MyList extends PageOf<ClaimRow> {
  owed: string;
}

export interface Area {
  id: string | null;
  name: string;
}

export interface LinkOption {
  kind: string;
  kindLabel: string;
  ref: string;
  date: string;
  label: string;
}

export interface ClaimBody {
  clientRequestId?: string;
  expenseDate: string;
  amount: string;
  category: Category;
  zoneId: string | null;
  note: string | null;
  paidBy: "CLAIMANT" | "COMPANY";
  reason: string | null;
  noBillReason: string | null;
  link: { kind: string; ref: string } | null;
  confirmDuplicate?: boolean;
  submit?: boolean;
  version?: number;
}

/** A 409 from submit when another claim looks the same: the ids of the matches. */
export interface DuplicateMatches {
  matches: string[];
}

export const MAX_BILLS = 5;
export const MAX_BILL_BYTES = 10 * 1024 * 1024;
export const BILL_EXTENSIONS = ["jpg", "jpeg", "png", "pdf"];

/** The reason a bill is refused before it is sent, or null when it looks fine. */
export function billProblem(file: {
  name: string;
  size: number;
}): string | null {
  const dot = file.name.lastIndexOf(".");
  const extension = dot < 0 ? "" : file.name.slice(dot + 1).toLowerCase();
  if (!BILL_EXTENSIONS.includes(extension)) {
    return "A bill must be a jpg, png or pdf file.";
  }
  if (file.size > MAX_BILL_BYTES) {
    return "The file is too large. A bill can be at most 10 MB.";
  }
  return null;
}

const MINE = "/api/v1/my-expenses";
const REVIEW = "/api/v1/expenses";

// ---------------------------------------------------------------- My Expenses

export function listMine(
  authFetch: AuthFetch,
  filters: { status?: string; month?: string; page?: number },
): Promise<ApiResult<MyList>> {
  const query = queryString({ size: 50, ...filters });
  return getJson(authFetch, `${MINE}?${query}`, "Could not load your claims.");
}

export function getMine(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<ClaimDetail>> {
  return getJson(authFetch, `${MINE}/${id}`, "Could not load this claim.");
}

export function getAreas(authFetch: AuthFetch): Promise<ApiResult<Area[]>> {
  return getJson(authFetch, `${MINE}/areas`, "Could not load the areas.");
}

export function getLinkOptions(
  authFetch: AuthFetch,
  category: Category,
  date: string,
): Promise<ApiResult<LinkOption[]>> {
  const query = queryString({ category, date });
  return getJson(
    authFetch,
    `${MINE}/link-options?${query}`,
    "Could not load what this claim can be linked to.",
  );
}

export function createClaim(
  authFetch: AuthFetch,
  body: ClaimBody,
): Promise<ApiResult<ClaimDetail>> {
  return sendJson(authFetch, "POST", MINE, body);
}

export function updateClaim(
  authFetch: AuthFetch,
  id: string,
  body: ClaimBody,
): Promise<ApiResult<ClaimDetail>> {
  return sendJson(authFetch, "PUT", `${MINE}/${id}`, body);
}

export function deleteClaim(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult> {
  return sendJson(authFetch, "DELETE", `${MINE}/${id}`, undefined, false);
}

export function submitClaim(
  authFetch: AuthFetch,
  id: string,
  version: number,
  confirmDuplicate: boolean,
): Promise<ApiResult<ClaimDetail>> {
  return sendJson(authFetch, "POST", `${MINE}/${id}/submit`, {
    version,
    confirmDuplicate,
  });
}

export function withdrawClaim(
  authFetch: AuthFetch,
  id: string,
  version: number,
): Promise<ApiResult<ClaimDetail>> {
  return sendJson(authFetch, "POST", `${MINE}/${id}/withdraw`, { version });
}

/** Sends one bill; the result is the claim with its bills. */
export async function uploadBill(
  authFetch: AuthFetch,
  id: string,
  file: File,
): Promise<ApiResult<ClaimDetail>> {
  try {
    const form = new FormData();
    form.append("file", file);
    const response = await authFetch(`${MINE}/${id}/bills`, {
      method: "POST",
      body: form,
    });
    if (!response.ok) {
      let reason = GENERIC_FAILURE;
      try {
        reason =
          ((await response.json()) as { reason?: string }).reason ?? reason;
      } catch {
        // keep the generic reason
      }
      return { ok: false, reason, status: response.status };
    }
    return { ok: true, data: (await response.json()) as ClaimDetail };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

/** The path a bill is downloaded from, for the signed-in person's own claim or for a reviewer. */
export function billPath(review: boolean, claimId: string, billId: string) {
  return `${review ? REVIEW : MINE}/${claimId}/bills/${billId}`;
}

/** Fetches a bill with the access token and returns an object URL to open it in a new tab. */
export async function fetchBillUrl(
  authFetch: AuthFetch,
  path: string,
): Promise<ApiResult<string>> {
  try {
    const response = await authFetch(path);
    if (!response.ok) {
      return { ok: false, reason: "Could not open this bill." };
    }
    return { ok: true, data: URL.createObjectURL(await response.blob()) };
  } catch {
    return { ok: false, reason: "Could not open this bill." };
  }
}

// -------------------------------------------------------------------- Review

export function listClaims(
  authFetch: AuthFetch,
  filters: {
    status?: string;
    claimant?: string;
    zone?: string;
    category?: string;
    from?: string;
    to?: string;
    page?: number;
  },
): Promise<ApiResult<PageOf<ClaimRow>>> {
  const query = queryString({ size: 50, ...filters });
  return getJson(authFetch, `${REVIEW}?${query}`, "Could not load the claims.");
}

export function getClaim(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<ClaimDetail>> {
  return getJson(authFetch, `${REVIEW}/${id}`, "Could not load this claim.");
}

export function approveClaim(
  authFetch: AuthFetch,
  id: string,
  version: number,
): Promise<ApiResult<ClaimDetail>> {
  return sendJson(authFetch, "POST", `${REVIEW}/${id}/approve`, { version });
}

export function rejectClaim(
  authFetch: AuthFetch,
  id: string,
  version: number,
  reason: string,
): Promise<ApiResult<ClaimDetail>> {
  return sendJson(authFetch, "POST", `${REVIEW}/${id}/reject`, {
    version,
    reason,
  });
}

export function reverseClaim(
  authFetch: AuthFetch,
  id: string,
  version: number,
  reason: string,
): Promise<ApiResult<ClaimDetail>> {
  return sendJson(authFetch, "POST", `${REVIEW}/${id}/reverse`, {
    version,
    reason,
  });
}

export function recordPaid(
  authFetch: AuthFetch,
  id: string,
  body: {
    version: number;
    paidOn: string;
    mode: PaidMode;
    reference: string | null;
  },
): Promise<ApiResult<ClaimDetail>> {
  return sendJson(authFetch, "POST", `${REVIEW}/${id}/record-paid`, body);
}

export function removeBill(
  authFetch: AuthFetch,
  claimId: string,
  billId: string,
  reason: string,
): Promise<ApiResult<ClaimDetail>> {
  return sendJson(authFetch, "DELETE", `${REVIEW}/${claimId}/bills/${billId}`, {
    reason,
  });
}

// ------------------------------------------------------------ pending, budgets, costs

export interface PendingBucket {
  label: string;
  count: number;
  total: string;
}

export interface PendingSummary {
  buckets: PendingBucket[];
  count: number;
  total: string;
  oldestSubmittedAt: string | null;
}

export function getPending(
  authFetch: AuthFetch,
): Promise<ApiResult<PendingSummary>> {
  return getJson(
    authFetch,
    `${REVIEW}/pending`,
    "Could not load the pending approvals.",
  );
}

export interface AreaFigures {
  zoneId: string | null;
  areaName: string;
  total: boolean;
  budgetId: string | null;
  budgetVersion: number | null;
  budget: string | null;
  actual: string;
  pending: string;
  remaining: string | null;
  percentUsed: number | null;
  over: boolean;
}

export interface BudgetOverview {
  month: string;
  areas: AreaFigures[];
  total: AreaFigures;
}

export interface BudgetDetail {
  month: string;
  areaName: string;
  byCategory: {
    category: Category;
    categoryLabel: string;
    count: number;
    total: string;
  }[];
  byClaimant: {
    claimantId: string;
    claimantName: string;
    count: number;
    total: string;
  }[];
  claims: ClaimRow[];
}

export function getBudgets(
  authFetch: AuthFetch,
  month: string,
): Promise<ApiResult<BudgetOverview>> {
  return getJson(
    authFetch,
    `${REVIEW}/budgets?${queryString({ month })}`,
    "Could not load the budgets.",
  );
}

export function getBudgetDetail(
  authFetch: AuthFetch,
  month: string,
  zoneId: string | null | "all",
): Promise<ApiResult<BudgetDetail>> {
  const zone = zoneId === "all" ? undefined : (zoneId ?? "head-office");
  return getJson(
    authFetch,
    `${REVIEW}/budgets/detail?${queryString({ month, zoneId: zone })}`,
    "Could not load the claims behind this figure.",
  );
}

export function setBudget(
  authFetch: AuthFetch,
  body: {
    zoneId: string | null;
    month: string;
    amount: string;
    version?: number | null;
  },
): Promise<ApiResult<{ budgetId: string; created: boolean }>> {
  return sendJson(authFetch, "PUT", `${REVIEW}/budgets`, {
    ...body,
    version: body.version ?? undefined,
  });
}

export interface CostMeasure {
  cost: string;
  divisor: number;
  perUnit: string | null;
  state: "OK" | "NO_SCHOOLS" | "NO_TEACHERS";
  claims: ClaimRow[];
}

export function getCostPerSchool(
  authFetch: AuthFetch,
  from: string,
  to: string,
  zoneId?: string,
): Promise<ApiResult<CostMeasure>> {
  return getJson(
    authFetch,
    `${REVIEW}/costs/per-school?${queryString({ from, to, zoneId })}`,
    "Could not load the cost per School.",
  );
}

export function getCostPerTeacher(
  authFetch: AuthFetch,
  from: string,
  to: string,
): Promise<ApiResult<CostMeasure>> {
  return getJson(
    authFetch,
    `${REVIEW}/costs/per-teacher?${queryString({ from, to })}`,
    "Could not load the cost per Teacher.",
  );
}

/** The month of claims as a CSV file, saved through the browser. */
export async function downloadExport(
  authFetch: AuthFetch,
  month: string,
): Promise<ApiResult<string>> {
  return fetchBillUrl(authFetch, `${REVIEW}/export?${queryString({ month })}`);
}
