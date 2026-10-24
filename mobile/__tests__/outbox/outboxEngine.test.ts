import * as SecureStore from "expo-secure-store";
import { claimDetail } from "../support/expenseFixtures";
import { installExpenses, type ExpensesFake } from "../support/expensesServer";
import { newServer, type FakeServer } from "../support/fakeServer";
import { makeItem } from "../support/outboxFixtures";
import { setAccessToken } from "../../src/security/memoryToken";
import { BACKOFF_SECONDS, OutboxEngine } from "../../src/outbox/outboxEngine";
import { listItems, saveItem, updateItem } from "../../src/outbox/outboxStore";
import type { OutboxItem } from "../../src/outbox/types";

const fsState = () => jest.requireMock("expo-file-system/legacy").__state;
const T0 = Date.parse("2026-10-23T09:00:00.000Z");

let server: FakeServer;
let fake: ExpensesFake;
let clock: number;
let sessionEnded: jest.Mock;

const engine = (owner = "u-1") => new OutboxEngine(owner, { now: () => clock, onSessionEnded: sessionEnded });

beforeEach(async () => {
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  setAccessToken("access-1");
  server = newServer();
  fake = installExpenses(server);
  clock = T0;
  sessionEnded = jest.fn();
  fsState().files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
});

async function add(overrides: Parameters<typeof makeItem>[0] = {}): Promise<OutboxItem> {
  const item = makeItem(overrides);
  await saveItem(item, { "bill-a": "file:///cache/photo.jpg" });
  return item;
}

const stored = async (id: string) => (await listItems("u-1")).items.find((i) => i.itemId === id);

describe("outcomes", () => {
  it("sends a waiting item, shows nothing sent before the last step, and deletes it with its files", async () => {
    await add({ itemId: "i-1" });
    await engine().run();
    expect(fake.claims[0]).toMatchObject({ status: "SUBMITTED", billCount: 1 });
    expect((await listItems("u-1")).items).toEqual([]);
    expect([...fsState().files.keys()].filter((k: string) => k.includes("/outbox/"))).toEqual([]);
  });

  it("sends two items in the order they were captured", async () => {
    await add({ itemId: "late", createdAt: "2026-10-23T09:30:00.000Z", clientRequestId: "r-late", fields: { note: "second", amount: "200.00" } });
    await add({ itemId: "early", createdAt: "2026-10-23T08:00:00.000Z", clientRequestId: "r-early", fields: { note: "first", amount: "100.00" } });
    await engine().run();
    expect(fake.claims.map((c) => c.note)).toEqual(["first", "second"]);
  });

  it("sends only the signed-in owner's items", async () => {
    await add({ itemId: "mine", ownerUserId: "u-1" });
    await add({ itemId: "theirs", ownerUserId: "u-2", clientRequestId: "r-2" });
    await engine("u-1").run();
    expect(fake.claims).toHaveLength(1);
    expect((await listItems("u-2")).items).toHaveLength(1);
  });

  it("runs once when asked twice at the same time", async () => {
    await add({ itemId: "i-1" });
    const e = engine();
    await Promise.all([e.run(), e.run()]);
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);
  });
});

describe("waiting and backoff", () => {
  it("keeps the item waiting with a growing backoff when there is no connection", async () => {
    await add({ itemId: "i-1" });
    server.offline = true;
    await engine().run();
    let item = await stored("i-1");
    expect(item).toMatchObject({ state: "WAITING", attempts: 1, stage: "CREATE" });
    expect(Date.parse(item!.nextTryAt!)).toBe(T0 + BACKOFF_SECONDS[0] * 1000);

    clock = T0 + 31_000;
    await engine().run();
    item = await stored("i-1");
    expect(item!.attempts).toBe(2);
    expect(Date.parse(item!.nextTryAt!)).toBe(clock + BACKOFF_SECONDS[1] * 1000);

    clock += 61_000;
    await engine().run();
    clock += 301_000;
    await engine().run();
    item = await stored("i-1");
    expect(Date.parse(item!.nextTryAt!)).toBe(clock + BACKOFF_SECONDS[2] * 1000);
  });

  it("does not try again before the backoff ends, unless Send now is pressed", async () => {
    await add({ itemId: "i-1" });
    server.offline = true;
    await engine().run();
    server.offline = false;
    server.calls.length = 0;
    await engine().run();
    expect(server.calls).toHaveLength(0);
    await engine().run({ force: true });
    expect(fake.claims[0].status).toBe("SUBMITTED");
  });

  it("treats a server error as waiting, not refused", async () => {
    await add({ itemId: "i-1" });
    fake.refusals.create = { status: 503, body: { message: "busy" } };
    await engine().run();
    expect(await stored("i-1")).toMatchObject({ state: "WAITING", attempts: 1, refusal: null });
  });

  it("waits for Retry-After after a 429", async () => {
    await add({ itemId: "i-1" });
    fake.refusals.create = { status: 429, body: { message: "slow down" }, headers: { "Retry-After": "120" } };
    await engine().run();
    const item = await stored("i-1");
    expect(Date.parse(item!.nextTryAt!)).toBe(T0 + 120_000);
  });

  it("stops the run and leaves the items alone when the session has ended", async () => {
    await add({ itemId: "a", createdAt: "2026-10-23T08:00:00.000Z" });
    await add({ itemId: "b", createdAt: "2026-10-23T08:30:00.000Z", clientRequestId: "r-b" });
    fake.refusals.create = { status: 401, body: { message: "Please sign in again." } };
    const e = engine();
    await e.run();
    expect(sessionEnded).toHaveBeenCalledTimes(1);
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);
    expect(await stored("a")).toMatchObject({ state: "WAITING", attempts: 0, refusal: null });
    fake.refusals = {};
    await e.run();
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(1);
  });
});

describe("refusals", () => {
  it("keeps a refused item with the server's reason and its stage, and still sends the next", async () => {
    await add({ itemId: "bad", createdAt: "2026-10-23T08:00:00.000Z", fields: { expenseDate: "2026-07-01" } });
    await add({ itemId: "good", createdAt: "2026-10-23T08:30:00.000Z", clientRequestId: "r-good" });
    await engine().run();
    const bad = await stored("bad");
    expect(bad).toMatchObject({ state: "REFUSED", stage: "SUBMIT", refusal: { status: 400 } });
    expect(bad!.refusal!.reason).toMatch(/60 days/);
    expect(bad!.serverClaimId).not.toBeNull();
    expect(await stored("good")).toBeUndefined();
    expect(fake.claims.filter((c) => c.status === "SUBMITTED")).toHaveLength(1);
  });

  it("does not send a refused item again by itself", async () => {
    await add({ itemId: "bad", fields: { expenseDate: "2026-07-01" } });
    await engine().run();
    server.calls.length = 0;
    await engine().run({ force: true });
    expect(server.calls).toHaveLength(0);
  });

  it("refuses with the reason on 404", async () => {
    await add({ itemId: "i-1" });
    fake.refusals.create = { status: 404, body: { reason: "Claim not found." } };
    await engine().run();
    expect(await stored("i-1")).toMatchObject({ state: "REFUSED", refusal: { status: 404, reason: "Claim not found." } });
  });

  it("asks for confirmation on a duplicate warning, then sends once confirmed", async () => {
    fake.claims = [claimDetail({ id: "c-old", status: "SUBMITTED", amount: "850.00", expenseDate: "2026-10-22" })];
    await add({ itemId: "i-1" });
    await engine().run();
    const item = await stored("i-1");
    expect(item).toMatchObject({ state: "NEEDS_CONFIRMATION", stage: "SUBMIT" });
    await engine().run({ force: true });
    expect((await stored("i-1"))!.state).toBe("NEEDS_CONFIRMATION");

    await saveAfterConfirm(item!);
    await engine().run();
    expect(await stored("i-1")).toBeUndefined();
  });

  it("refuses a stale version and shows the claim as the server holds it", async () => {
    fake.claims = [claimDetail({ id: "c-7", status: "DRAFT", version: 5, note: "web edit", amount: "900.00" })];
    await add({
      itemId: "i-1",
      intent: "SAVE_DRAFT",
      bills: [],
      serverClaimId: "c-7",
      serverVersion: 3,
      stage: "UPDATE",
      baseSnapshot: { expenseDate: "2026-10-22", amount: "850.00", categoryLabel: "Travel and Fuel", note: "old" },
    });
    await engine().run();
    const item = await stored("i-1");
    expect(item).toMatchObject({ state: "REFUSED", refusal: { status: 409 } });
    expect(item!.refusal!.reason).toMatch(/changed by someone else/);
    expect(item!.serverCurrent).toMatchObject({ amount: "900.00", note: "web edit", version: 5 });
  });
});

async function saveAfterConfirm(item: OutboxItem) {
  await updateItem({ ...item, state: "WAITING", confirmDuplicate: true, refusal: null });
}
