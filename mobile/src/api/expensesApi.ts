import { toBase64 } from "../formats/base64";
import { api, fetchBinary, upload, type UploadFile } from "./httpClient";

/**
 * Typed calls to the My Expenses endpoints of spec 015, unchanged (spec 036 contracts/server-calls.md). A claim carries
 * `allowedActions`; the app shows exactly those buttons and computes no status or action itself.
 */
const BASE = "/api/v1/my-expenses";

export type ClaimStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED" | "PAID" | "PAID_BY_COMPANY";
export type AllowedAction = "EDIT" | "SUBMIT" | "WITHDRAW" | "DELETE" | "ADD_BILL" | string;
export type CategoryKey = "RENT_UTILITIES" | "TRAVEL_FUEL" | "TRAINING_STAY" | "MARKETING" | "OFFICE_SUPPLIES" | "OTHER";
export type PaidBy = "CLAIMANT" | "COMPANY";

export interface ClaimRow {
  id: string;
  claimantId: string;
  claimantName: string;
  expenseDate: string;
  amount: string;
  category: CategoryKey;
  categoryLabel: string;
  zoneId: string | null;
  areaName: string | null;
  status: ClaimStatus;
  statusLabel: string;
  paidBy: PaidBy;
  billCount: number;
  noBillReason: string | null;
  linkLabel: string | null;
  note: string | null;
  submittedAt: string | null;
  ageDays: number | null;
  allowedActions: AllowedAction[];
}

export interface BillRef {
  id: string;
  name: string;
  contentType: string;
  sizeBytes: number;
  addedBy?: string | null;
  addedByName?: string | null;
  addedAt?: string | null;
  removed: boolean;
  removedByName?: string | null;
  removedAt?: string | null;
  removalReason?: string | null;
}

export interface LinkOption {
  kind: string;
  kindLabel: string;
  ref: string;
  date: string | null;
  label: string;
}

export interface Area {
  id: string | null;
  name: string;
}

export interface ClaimDetail extends ClaimRow {
  reason: string | null;
  link: LinkOption | null;
  decision: { by: string | null; byName: string | null; at: string | null; reason: string | null } | null;
  payment: {
    how: string | null;
    paidOn: string | null;
    mode: string | null;
    reference: string | null;
    payrollRunId?: string | null;
    payrollMonth?: string | null;
    recordedByName?: string | null;
  } | null;
  payrollState?: string | null;
  bills: BillRef[];
  history: { at: string; actorId: string | null; actorName: string | null; event: string; reason: string | null; detail: string | null }[];
  ownClaim?: boolean;
  version: number;
  createdAt?: string;
}

export interface MyList {
  content: ClaimRow[];
  page: number;
  size: number;
  totalElements: number;
  owed: string;
}

/** What is sent to create or update a claim. `submit` is always false: submitting is its own call. */
export interface ClaimBody {
  clientRequestId?: string;
  expenseDate: string;
  amount: string;
  category: CategoryKey;
  zoneId: string | null;
  note: string | null;
  paidBy: PaidBy;
  reason: string | null;
  noBillReason: string | null;
  link: { kind: string; ref: string } | null;
  confirmDuplicate: boolean;
  submit: false;
  version?: number;
}

export interface ListQuery {
  status?: ClaimStatus;
  month?: string;
  page?: number;
  size?: number;
}

function query(params: Record<string, string | number | undefined>): string {
  const parts = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== "")
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`);
  return parts.length ? `?${parts.join("&")}` : "";
}

export const listMyExpenses = ({ status, month, page = 0, size = 50 }: ListQuery = {}) =>
  api.get<MyList>(`${BASE}${query({ status, month, page, size })}`);

export const getClaim = (id: string) => api.get<ClaimDetail>(`${BASE}/${id}`);
export const createClaim = (body: ClaimBody) => api.post<ClaimDetail>(BASE, body);
export const updateClaim = (id: string, body: ClaimBody) => api.put<ClaimDetail>(`${BASE}/${id}`, body);
export const deleteClaim = (id: string) => api.delete<void>(`${BASE}/${id}`);
export const submitClaim = (id: string, body: { version: number; confirmDuplicate: boolean }) =>
  api.post<ClaimDetail>(`${BASE}/${id}/submit`, body);
export const withdrawClaim = (id: string, version: number) => api.post<ClaimDetail>(`${BASE}/${id}/withdraw`, { version });
export const addBill = (id: string, file: UploadFile) => upload<ClaimDetail>(`${BASE}/${id}/bills`, "file", file);
export const listAreas = () => api.get<Area[]>(`${BASE}/areas`);
export const listLinkOptions = (category: CategoryKey, date: string) =>
  api.get<LinkOption[]>(`${BASE}/link-options${query({ category, date })}`);

/** A bill's bytes for viewing: held in memory as a data address, never written anywhere. */
export interface BillView {
  dataUri: string;
  contentType: string;
  sizeBytes: number;
}

export async function openBill(id: string, billId: string): Promise<BillView> {
  const answer = await fetchBinary(`${BASE}/${id}/bills/${billId}`);
  return {
    dataUri: `data:${answer.contentType};base64,${toBase64(answer.bytes)}`,
    contentType: answer.contentType,
    sizeBytes: answer.sizeBytes,
  };
}
