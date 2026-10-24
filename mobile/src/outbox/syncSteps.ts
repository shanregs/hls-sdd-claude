import {
  addBill,
  createClaim,
  getClaim,
  submitClaim,
  updateClaim,
  type ClaimBody,
  type ClaimDetail,
} from "../api/expensesApi";
import { ApiError } from "../api/errors";
import { withBillFile } from "./outboxStore";
import type { OutboxFields, OutboxItem } from "./types";

/**
 * The idempotent, resumable steps of sending one claim (spec 036 research §2): create (or update) the Draft, upload the
 * bills one by one, then submit. Each step saves its progress through `persist` before it returns, so a kill or a lost
 * reply resumes at the same step and never makes a second claim or a second copy of a bill. A step throws what the API
 * threw; the engine decides what that means for the item.
 */
export interface StepEnv {
  /** Writes the item's new progress (encrypted, on the phone). */
  persist: (item: OutboxItem) => Promise<void>;
}

export interface StepResult {
  item: OutboxItem;
  /** True when nothing is left to send: the item can be deleted from the phone. */
  finished: boolean;
}

const blank = (text: string): string | null => (text.trim() === "" ? null : text.trim());

/** The create / update body of an item. The app always sends `submit: false`; submitting is its own call. */
export function bodyOf(fields: OutboxFields): ClaimBody {
  return {
    expenseDate: fields.expenseDate,
    amount: Number(fields.amount).toFixed(2),
    category: fields.category,
    zoneId: fields.zoneId,
    note: blank(fields.note),
    paidBy: fields.paidBy,
    reason: blank(fields.reason),
    noBillReason: blank(fields.noBillReason),
    link: fields.link ? { kind: fields.link.kind, ref: fields.link.ref } : null,
    confirmDuplicate: false,
    submit: false,
  };
}

const sameAsServer = (claim: ClaimDetail, fields: OutboxFields): boolean =>
  claim.expenseDate === fields.expenseDate &&
  Number(claim.amount) === Number(fields.amount) &&
  claim.category === fields.category &&
  (claim.note ?? "") === fields.note.trim() &&
  (claim.zoneId ?? null) === fields.zoneId;

async function save(item: OutboxItem, env: StepEnv, change: Partial<OutboxItem>): Promise<OutboxItem> {
  const next = { ...item, ...change, updatedAt: new Date().toISOString() };
  await env.persist(next);
  return next;
}

/** Create the Draft with the item's own request id, or update the claim this item already has on the server. */
export async function ensureClaim(item: OutboxItem, env: StepEnv): Promise<OutboxItem> {
  if (item.stage === "CREATE" && item.serverClaimId === null) {
    const created = await createClaim({ ...bodyOf(item.fields), clientRequestId: item.clientRequestId });
    return save(item, env, { serverClaimId: created.id, serverVersion: created.version, stage: "BILLS" });
  }
  const claimId = item.serverClaimId as string;
  const fresh = await getClaim(claimId);
  // A correction of a claim the claimant started from is sent against the version they saw, so a change made elsewhere is
  // refused by the server (server wins). A claim this item made itself is sent against the version just read.
  const based = item.baseSnapshot !== null && item.serverVersion !== null;
  const sendVersion = based ? (item.serverVersion as number) : fresh.version;
  // After a lost reply to the update the claim already holds the item's values: do not send them again.
  if (based && fresh.version !== sendVersion && sameAsServer(fresh, item.fields)) {
    return save(item, env, { serverVersion: fresh.version, stage: "BILLS" });
  }
  const updated = await updateClaim(claimId, { ...bodyOf(item.fields), version: sendVersion });
  return save(item, env, { serverVersion: updated.version, stage: "BILLS" });
}

/** Uploads the bills the server does not list yet, by their generated file names. */
export async function uploadBills(item: OutboxItem, env: StepEnv): Promise<StepResult> {
  let current = item;
  let claim = await getClaim(item.serverClaimId as string);
  for (const bill of item.bills) {
    const live = claim.bills.find((b) => !b.removed && b.name === bill.fileName);
    if (bill.uploaded && live) continue;
    if (live) {
      current = await save(current, env, {
        bills: current.bills.map((b) => (b.localId === bill.localId ? { ...b, uploaded: true, serverBillId: live.id } : b)),
      });
      continue;
    }
    claim = await withBillFile(item.ownerUserId, item.itemId, bill.localId, bill.fileName, (uri) =>
      addBill(item.serverClaimId as string, { uri, name: bill.fileName, type: bill.contentType }),
    );
    const added = claim.bills.find((b) => b.name === bill.fileName);
    current = await save(current, env, {
      serverVersion: claim.version,
      bills: current.bills.map((b) => (b.localId === bill.localId ? { ...b, uploaded: true, serverBillId: added?.id ?? null } : b)),
    });
  }
  if (item.intent === "SAVE_DRAFT") return { item: current, finished: true };
  return { item: await save(current, env, { stage: "SUBMIT" }), finished: false };
}

/** Submits the claim. A claim already Submitted (a lost reply after the submit) counts as done. */
export async function submitStep(item: OutboxItem, env: StepEnv): Promise<StepResult> {
  const claimId = item.serverClaimId as string;
  const claim = await getClaim(claimId);
  if (claim.status === "SUBMITTED") return { item, finished: true };
  try {
    await submitClaim(claimId, { version: claim.version, confirmDuplicate: item.confirmDuplicate });
  } catch (error) {
    if (error instanceof ApiError && error.status === 409 && error.matches.length === 0) {
      const again = await getClaim(claimId);
      if (again.status === "SUBMITTED") return { item, finished: true };
    }
    throw error;
  }
  void env;
  return { item, finished: true };
}

/** Runs the item's next step, according to its stage. */
export async function runNextStep(item: OutboxItem, env: StepEnv): Promise<StepResult> {
  switch (item.stage) {
    case "CREATE":
    case "UPDATE":
      return { item: await ensureClaim(item, env), finished: false };
    case "BILLS":
      return uploadBills(item, env);
    case "SUBMIT":
      return submitStep(item, env);
  }
}
