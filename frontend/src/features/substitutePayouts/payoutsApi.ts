import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

/** Substitute day attendance and payouts (spec 017b, contracts/payout-api.md). Money is a number; dates are ISO. */
export const PAYOUTS_ROUTE = "/operations/substitute-payouts";
const BASE = "/api/v1/substitute-payouts";

export type DayStatus =
  | "ALLOCATED"
  | "DID_NOT_ATTEND"
  | "ATTENDED"
  | "CANNOT_PAY"
  | "LOCKED_WAITING_RUN"
  | "BACKPAY_WAITING"
  | "IN_DRAFT_RUN"
  | "IN_RUN_AWAITING_APPROVAL"
  | "PAID"
  | "CANCELLED"
  | "CANCELLED_BEFORE_PAYMENT"
  | "CHANGED_RUN_AWAITING"
  | "CORRECTION_NEEDED"
  | "CORRECTION_RAISED"
  | "CORRECTED";

export const STATUS_LABELS: Record<DayStatus, string> = {
  ALLOCATED: "Allocated, attendance not marked",
  DID_NOT_ATTEND: "Did not attend",
  ATTENDED: "Attended, month not locked yet",
  CANNOT_PAY: "Cannot be paid yet",
  LOCKED_WAITING_RUN: "Locked, waiting for the payroll run",
  BACKPAY_WAITING: "Waiting for a supplementary run",
  IN_DRAFT_RUN: "In a draft payroll run",
  IN_RUN_AWAITING_APPROVAL: "In a payroll run awaiting approval",
  PAID: "Paid in an approved run",
  CANCELLED: "Allocation cancelled",
  CANCELLED_BEFORE_PAYMENT: "Cancelled before payment",
  CHANGED_RUN_AWAITING: "Changed while the run awaits approval",
  CORRECTION_NEEDED: "Correction needed",
  CORRECTION_RAISED: "Correction raised",
  CORRECTED: "Corrected",
};

export type Attendance = "NOT_MARKED" | "PRESENT" | "ABSENT";

export const ATTENDANCE_LABELS: Record<Attendance, string> = {
  NOT_MARKED: "Not marked",
  PRESENT: "Present",
  ABSENT: "Absent",
};

export interface DayRow {
  id: string;
  substituteTeacherId: string;
  substituteName: string;
  schoolId: string;
  schoolName: string;
  zoneId: string;
  zoneName: string;
  date: string;
  dayValue: number;
  live: boolean;
  attendance: Attendance;
  status: DayStatus;
  statusText: string;
  mismatchReason: string | null;
  rate: number | null;
  amount: number | null;
  runMonth: string | null;
  workMonth: string;
  paidAmount: number | null;
  needsReview: boolean;
  locked: boolean;
  version: number;
  actions: string[];
}

export interface Totals {
  allocated: number;
  attended: number;
  paid: number;
  unpaidByStatus: Record<string, number>;
  amountPaid: number;
}

export interface DayPage {
  content: DayRow[];
  page: number;
  size: number;
  totalElements: number;
  totals: Totals;
}

export interface DayFilters {
  month?: string;
  status?: string;
  substituteId?: string;
  schoolId?: string;
  page?: number;
  size?: number;
}

export const listDays = (
  f: AuthFetch,
  filters: DayFilters,
): Promise<ApiResult<DayPage>> =>
  getJson(
    f,
    `${BASE}/days?${queryString({ ...filters })}`,
    "Could not load the substitute days.",
  );

export const markDay = (
  f: AuthFetch,
  row: Pick<DayRow, "id" | "version">,
  attendance: Attendance,
): Promise<ApiResult<DayRow>> =>
  sendJson(f, "PUT", `${BASE}/days/${row.id}/attendance`, {
    attendance,
    version: row.version,
  });

export interface BulkLockResult {
  locked: number;
  alreadyLocked: number;
  blocked: { substituteTeacherId: string; reason: string }[];
}

export const lockAllMonths = (
  f: AuthFetch,
  month: string,
): Promise<ApiResult<BulkLockResult>> =>
  sendJson(f, "POST", `${BASE}/months/lock?${queryString({ month })}`);

export const lockMonth = (
  f: AuthFetch,
  teacherId: string,
  month: string,
): Promise<ApiResult> =>
  sendJson(f, "POST", `${BASE}/months/${teacherId}/${month}/lock`, {}, false);

export const reopenMonth = (
  f: AuthFetch,
  teacherId: string,
  month: string,
  reason: string,
): Promise<ApiResult> =>
  sendJson(
    f,
    "POST",
    `${BASE}/months/${teacherId}/${month}/reopen`,
    { reason },
    false,
  );

export interface Mismatch {
  day: DayRow;
  reason: string;
  nextStep: string;
  /** What a Correction would be: positive pays more, negative recovers; null when none is suggested. */
  suggestedCorrection: number | null;
}

export const listMismatches = (
  f: AuthFetch,
  month: string,
): Promise<ApiResult<Mismatch[]>> =>
  getJson(
    f,
    `${BASE}/mismatches?${queryString({ month })}`,
    "Could not load the days that need attention.",
  );

/** Admin only (payroll's own PROCESS right): one Correction per day, linked to it. */
export const raiseCorrection = (
  f: AuthFetch,
  dayId: string,
  amount: number,
  reason: string,
): Promise<ApiResult<DayRow>> =>
  sendJson(f, "POST", `${BASE}/days/${dayId}/correction`, { amount, reason });

export interface MigrationResult {
  migrated: number;
  futureAllocations: number;
  alreadyRecorded: number;
  settleFirst: { teacherId: string; name: string; credits: number }[];
}

/** The one-off go-live move of the spec 017a credits into day records (Admin; safe to run again). */
export const runMigration = (f: AuthFetch): Promise<ApiResult<MigrationResult>> =>
  sendJson(f, "POST", `${BASE}/migration/run`, {});

export async function downloadCsv(
  f: AuthFetch,
  filters: { month?: string; status?: string },
): Promise<ApiResult<void>> {
  try {
    const response = await f(`${BASE}/export?${queryString({ ...filters })}`);
    if (!response.ok) {
      return { ok: false, reason: "Could not download the file.", status: response.status };
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "substitute-payouts.csv";
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
    return { ok: true, data: undefined };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}
