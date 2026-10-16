import type { LineState } from "./payrollRunApi";

export interface WorkingPolicy {
  id: string;
  effectiveOn: string;
  rounding: string;
  halfDayFraction: string;
}

export interface WorkingAmounts {
  unrounded: string;
  rounded: string;
}

/** What a line was computed from, as stored with it (spec 013b FR-011). Nothing here is read live. */
export interface LineWorking {
  state: LineState;
  reason?: string;
  policy: WorkingPolicy;
  salaryRows?: { from: string; to: string; monthly: string }[];
  payableDates?: string[];
  days?: { date: string; salary: string; divisor: number; amount: string }[];
  unpaid?: {
    date: string;
    dayValue: string;
    salary: string;
    divisor: number;
    amount: string;
    reason?: string;
  }[];
  earned?: WorkingAmounts;
  deduction?: WorkingAmounts;
  adjustments?: { id: string; kind: string; amount: string; reason: string }[];
  manager?: { workingDaysAtLock: string; unpaidAtLock: string } | null;
}

export const ADJUSTMENT_KIND_LABELS: Record<string, string> = {
  CORRECTION: "Correction",
  INCENTIVE: "Incentive",
  REIMBURSEMENT: "Reimbursement",
  SUBSTITUTE_CREDIT: "Substitute credit",
};
