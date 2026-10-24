import type { CategoryKey, PaidBy } from "../api/expensesApi";

/** The on-phone entities of spec 036 data-model.md A. Nothing here is authoritative: the server's claim is. */

export type OutboxIntent = "SAVE_DRAFT" | "SUBMIT";
export type OutboxState = "WAITING" | "SENDING" | "NEEDS_CONFIRMATION" | "REFUSED";
/** The next step to run for an item. */
export type OutboxStage = "CREATE" | "UPDATE" | "BILLS" | "SUBMIT";

export interface OutboxBill {
  localId: string;
  /** Generated, unique, `.jpg` for images and `.pdf` for a pdf: the lost-reply check compares it with the claim's bills. */
  fileName: string;
  contentType: "image/jpeg" | "application/pdf";
  sizeBytes: number;
  /** True once the server listed this file name on the claim. */
  uploaded: boolean;
  serverBillId: string | null;
}

export interface OutboxFields {
  expenseDate: string;
  amount: string;
  category: CategoryKey;
  /** Null is Head office. */
  zoneId: string | null;
  areaName: string | null;
  note: string;
  paidBy: PaidBy;
  reason: string;
  noBillReason: string;
  link: { kind: string; ref: string; label: string; date: string | null } | null;
  /** A Training Stay saved offline with no cached batch: not sent until a batch is chosen when connected. */
  batchPending?: boolean;
}

export interface OutboxRefusal {
  status: number;
  reason: string;
  at: string;
}

/** The row a correction started from, shown next to the server's current values after a stale-version refusal. */
export interface BaseSnapshot {
  expenseDate: string;
  amount: string;
  categoryLabel: string;
  note: string | null;
}

export interface OutboxItem {
  itemId: string;
  ownerUserId: string;
  /** Made when the form is first saved and reused on every retry. */
  clientRequestId: string;
  intent: OutboxIntent;
  createdAt: string;
  updatedAt: string;
  fields: OutboxFields;
  bills: OutboxBill[];
  serverClaimId: string | null;
  serverVersion: number | null;
  baseSnapshot: BaseSnapshot | null;
  confirmDuplicate: boolean;
  state: OutboxState;
  stage: OutboxStage;
  refusal: OutboxRefusal | null;
  attempts: number;
  nextTryAt: string | null;
  /** Set when the engine found the claim on the server already changed (stale version): its current values. */
  serverCurrent?: BaseSnapshot | null;
}
