import type { OutboxBill, OutboxFields, OutboxItem } from "../../src/outbox/types";

export function makeFields(overrides: Partial<OutboxFields> = {}): OutboxFields {
  return {
    expenseDate: "2026-10-22",
    amount: "850.00",
    category: "TRAVEL_FUEL",
    zoneId: null,
    areaName: "Head office",
    note: "Petrol, school visit",
    paidBy: "CLAIMANT",
    reason: "",
    noBillReason: "",
    link: null,
    ...overrides,
  };
}

export function makeBill(overrides: Partial<OutboxBill> = {}): OutboxBill {
  return {
    localId: "bill-a",
    fileName: "bill-aaaa0001.jpg",
    contentType: "image/jpeg",
    sizeBytes: 300_000,
    uploaded: false,
    serverBillId: null,
    ...overrides,
  };
}

/** An outbox item for owner u-1 with one bill, ready to send as a submitted claim. */
export function makeItem(overrides: Omit<Partial<OutboxItem>, "fields"> & { fields?: Partial<OutboxFields> } = {}): OutboxItem {
  const { fields, ...rest } = overrides;
  return {
    itemId: "item-1",
    ownerUserId: "u-1",
    clientRequestId: "req-item-1",
    intent: "SUBMIT",
    createdAt: "2026-10-23T08:00:00.000Z",
    updatedAt: "2026-10-23T08:00:00.000Z",
    fields: makeFields(fields),
    bills: [makeBill()],
    serverClaimId: null,
    serverVersion: null,
    baseSnapshot: null,
    confirmDuplicate: false,
    state: "WAITING",
    stage: "CREATE",
    refusal: null,
    attempts: 0,
    nextTryAt: null,
    ...rest,
  };
}
