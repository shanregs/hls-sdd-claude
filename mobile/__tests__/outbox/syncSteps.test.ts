import * as SecureStore from "expo-secure-store";
import { NoConnectionError } from "../../src/api/errors";
import { setAccessToken } from "../../src/security/memoryToken";
import { saveItem } from "../../src/outbox/outboxStore";
import { runNextStep } from "../../src/outbox/syncSteps";
import type { OutboxItem } from "../../src/outbox/types";
import { claimDetail } from "../support/expenseFixtures";
import { installExpenses, type ExpensesFake } from "../support/expensesServer";
import { newServer, type FakeServer } from "../support/fakeServer";
import { makeBill, makeItem } from "../support/outboxFixtures";

const fsState = () => jest.requireMock("expo-file-system/legacy").__state;

let server: FakeServer;
let fake: ExpensesFake;
let saved: OutboxItem[];
const env = { persist: async (item: OutboxItem) => void saved.push(item) };

beforeEach(async () => {
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  setAccessToken("access-1");
  server = newServer();
  fake = installExpenses(server);
  saved = [];
  fsState().files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
});

async function withBill(overrides: Parameters<typeof makeItem>[0] = {}): Promise<OutboxItem> {
  const item = makeItem({ fields: { noBillReason: "" }, ...overrides });
  await saveItem(item, { "bill-a": "file:///cache/photo.jpg" });
  return item;
}

/** Runs steps like the engine, until the item is finished or a step throws. */
async function drain(start: OutboxItem): Promise<OutboxItem> {
  let item = start;
  for (let i = 0; i < 10; i += 1) {
    const result = await runNextStep(item, env);
    item = result.item;
    if (result.finished) return item;
  }
  throw new Error("did not finish");
}

describe("the three steps", () => {
  it("creates the Draft, uploads the bill and submits", async () => {
    const done = await drain(await withBill());
    expect(fake.claims).toHaveLength(1);
    expect(fake.claims[0]).toMatchObject({ status: "SUBMITTED", billCount: 1 });
    expect(done.serverClaimId).toBe(fake.claims[0].id);
    expect(fake.uploads[0].part).toMatchObject({ name: "bill-aaaa0001.jpg", type: "image/jpeg" });
    expect(saved.map((s) => s.stage)).toEqual(["BILLS", "BILLS", "SUBMIT"]);
    expect(server.callsTo("POST /api/v1/my-expenses")[0].body).toMatchObject({ submit: false, clientRequestId: "req-item-1", amount: "850.00" });
  });

  it("stops after the bills for a draft", async () => {
    await drain(await withBill({ intent: "SAVE_DRAFT" }));
    expect(fake.claims[0]).toMatchObject({ status: "DRAFT", billCount: 1 });
    expect(server.callsTo("POST /api/v1/my-expenses/{id}/submit")).toHaveLength(0);
  });
});

describe("lost replies", () => {
  it("makes one claim when the reply to the create is lost", async () => {
    fake.loseReply.create = 1;
    const item = await withBill();
    await expect(runNextStep(item, env)).rejects.toBeInstanceOf(NoConnectionError);
    expect(fake.claims).toHaveLength(1);
    expect(saved).toHaveLength(0);
    await drain(item);
    expect(fake.claims).toHaveLength(1);
    expect(fake.claims[0].status).toBe("SUBMITTED");
  });

  it("adds no second copy of a bill when the reply to the upload is lost", async () => {
    fake.loseReply.bill = 1;
    let item = await withBill();
    item = (await runNextStep(item, env)).item;
    await expect(runNextStep(item, env)).rejects.toBeInstanceOf(NoConnectionError);
    expect(fake.claims[0].bills).toHaveLength(1);
    await drain(item);
    expect(fake.uploads).toHaveLength(1);
    expect(fake.claims[0]).toMatchObject({ status: "SUBMITTED", billCount: 1 });
  });

  it("counts a Submitted claim as done when the reply to the submit is lost", async () => {
    fake.loseReply.submit = 1;
    let item = await withBill();
    item = (await runNextStep(item, env)).item;
    item = (await runNextStep(item, env)).item;
    expect(item.stage).toBe("SUBMIT");
    await expect(runNextStep(item, env)).rejects.toBeInstanceOf(NoConnectionError);
    expect(fake.claims[0].status).toBe("SUBMITTED");
    const result = await runNextStep(item, env);
    expect(result.finished).toBe(true);
    expect(server.callsTo("POST /api/v1/my-expenses/{id}/submit")).toHaveLength(1);
  });
});

describe("refusals and corrections", () => {
  it("keeps the claim id when the submit is refused, and a corrected date updates the same claim", async () => {
    const item = await withBill({ fields: { expenseDate: "2026-07-01" } });
    let current = item;
    await expect(drain(current)).rejects.toMatchObject({ status: 400 });
    current = saved[saved.length - 1];
    expect(current.serverClaimId).not.toBeNull();
    expect(current.stage).toBe("SUBMIT");

    const corrected: OutboxItem = { ...current, stage: "UPDATE", fields: { ...current.fields, expenseDate: "2026-10-20" } };
    await drain(corrected);
    expect(fake.claims).toHaveLength(1);
    expect(fake.claims[0]).toMatchObject({ status: "SUBMITTED", expenseDate: "2026-10-20" });
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);
    expect(server.callsTo("PUT /api/v1/my-expenses/{id}")).toHaveLength(1);
    expect(fake.uploads).toHaveLength(1);
  });

  it("sends a correction of an existing claim against the version the claimant saw, so a change elsewhere is refused", async () => {
    fake.claims = [claimDetail({ id: "c-7", status: "DRAFT", version: 5, note: "web edit" })];
    const item = makeItem({
      bills: [],
      intent: "SAVE_DRAFT",
      serverClaimId: "c-7",
      serverVersion: 3,
      stage: "UPDATE",
      baseSnapshot: { expenseDate: "2026-10-22", amount: "850.00", categoryLabel: "Travel and Fuel", note: "old" },
      fields: { note: "phone edit" },
    });
    await expect(runNextStep(item, env)).rejects.toMatchObject({ status: 409, message: expect.stringContaining("changed by someone else") });
    expect(fake.claims[0].note).toBe("web edit");
  });

  it("does not send an update again when the claim already holds the item's values", async () => {
    fake.claims = [claimDetail({ id: "c-7", status: "DRAFT", version: 6, note: "phone edit", expenseDate: "2026-10-22", amount: "850.00" })];
    const item = makeItem({
      bills: [],
      intent: "SAVE_DRAFT",
      serverClaimId: "c-7",
      serverVersion: 5,
      stage: "UPDATE",
      baseSnapshot: { expenseDate: "2026-10-22", amount: "850.00", categoryLabel: "Travel and Fuel", note: "old" },
      fields: { note: "phone edit" },
    });
    const result = await runNextStep(item, env);
    expect(result.item.stage).toBe("BILLS");
    expect(server.callsTo("PUT /api/v1/my-expenses/{id}")).toHaveLength(0);
  });

  it("surfaces the duplicate warning with the matching claims", async () => {
    fake.claims = [claimDetail({ id: "c-old", status: "SUBMITTED", amount: "850.00", expenseDate: "2026-10-22" })];
    const item = await withBill();
    await expect(drain(item)).rejects.toMatchObject({ status: 409, matches: ["c-old"] });
    // Confirmed, it goes through.
    const confirmed: OutboxItem = { ...saved[saved.length - 1], confirmDuplicate: true };
    await drain(confirmed);
    expect(fake.claims.find((c) => c.id === confirmed.serverClaimId)?.status).toBe("SUBMITTED");
  });

  it("uploads only the bills the server does not list", async () => {
    const item = await withBill({ bills: [makeBill({ localId: "bill-a", fileName: "bill-aaaa0001.jpg", uploaded: false })] });
    fake.claims = [];
    const step1 = await runNextStep(item, env);
    fake.claims[0].bills = [
      { id: "b-pre", name: "bill-aaaa0001.jpg", contentType: "image/jpeg", sizeBytes: 10, removed: false },
    ];
    const step2 = await runNextStep(step1.item, env);
    expect(fake.uploads).toHaveLength(0);
    expect(step2.item.bills[0]).toMatchObject({ uploaded: true, serverBillId: "b-pre" });
  });
});
