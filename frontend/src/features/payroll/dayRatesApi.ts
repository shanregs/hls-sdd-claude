import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

/** Designation day rates for Substitute Teachers (spec 017b FR-005). Amounts are plain decimal strings. */
const BASE = "/api/v1/salary-structures/designation-day-rates";

export interface DayRateListEntry {
  designationId: string;
  name: string;
  retired: boolean;
  currentAmount: string | null;
  currentEffectiveOn: string | null;
}

export interface DayRateRow {
  id: string;
  amount: string;
  effectiveOn: string;
  note: string | null;
  recordedBy: string;
  recordedAt: string;
}

export const listDayRates = (
  f: AuthFetch,
  includeRetired: boolean,
): Promise<ApiResult<DayRateListEntry[]>> =>
  getJson(
    f,
    `${BASE}?${queryString({ includeRetired })}`,
    "Could not load the day rates.",
  );

export const dayRateHistory = (
  f: AuthFetch,
  designationId: string,
): Promise<ApiResult<DayRateRow[]>> =>
  getJson(f, `${BASE}/${designationId}`, "Could not load the rate history.");

export const recordDayRate = (
  f: AuthFetch,
  designationId: string,
  values: { amount: number; effectiveOn: string; note: string },
): Promise<ApiResult<DayRateRow>> =>
  sendJson(f, "POST", `${BASE}/${designationId}`, {
    amount: values.amount,
    effectiveOn: values.effectiveOn,
    note: values.note.trim() === "" ? null : values.note.trim(),
  });
