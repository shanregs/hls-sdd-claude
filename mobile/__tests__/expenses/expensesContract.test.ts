import * as SecureStore from "expo-secure-store";
import { ApiError } from "../../src/api/errors";
import {
  addBill,
  createClaim,
  deleteClaim,
  getClaim,
  listAreas,
  listLinkOptions,
  listMyExpenses,
  openBill,
  submitClaim,
  updateClaim,
  withdrawClaim,
  type ClaimBody,
} from "../../src/api/expensesApi";
import { setAccessToken } from "../../src/security/memoryToken";
import { claimDetail, linkOption } from "../support/expenseFixtures";
import { installExpenses, type ExpensesFake } from "../support/expensesServer";
import { newServer, type FakeServer, type MultipartBody } from "../support/fakeServer";

/** Spec 036 T006: the request shapes of contracts/server-calls.md, against a fake that copies the real answers of spec 015. */
const BASE = "/api/v1/my-expenses";

let server: FakeServer;
let fake: ExpensesFake;

const body = (overrides: Partial<ClaimBody> = {}): ClaimBody => ({
  clientRequestId: "req-1",
  expenseDate: "2026-10-22",
  amount: "850.00",
  category: "TRAVEL_FUEL",
  zoneId: null,
  note: "Petrol",
  paidBy: "CLAIMANT",
  reason: null,
  noBillReason: null,
  link: null,
  confirmDuplicate: false,
  submit: false,
  ...overrides,
});

beforeEach(async () => {
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  setAccessToken("access-1");
  server = newServer();
  fake = installExpenses(server);
});

describe("list and read", () => {
  it("sends the status, month, page and size as query values", async () => {
    await listMyExpenses({ status: "REJECTED", month: "2026-10", page: 2, size: 50 });
    expect(server.calls[0].query).toEqual({ status: "REJECTED", month: "2026-10", page: "2", size: "50" });
  });

  it("omits an empty filter and defaults to the first page of 50", async () => {
    await listMyExpenses();
    expect(server.calls[0].query).toEqual({ page: "0", size: "50" });
  });

  it("reads one claim, the areas and the link options for a category and date", async () => {
    fake.claims = [claimDetail({ id: "c-9" })];
    fake.linkOptions = [linkOption()];
    expect((await getClaim("c-9")).id).toBe("c-9");
    expect((await listAreas())[0]).toEqual({ id: null, name: "Head office" });
    expect(await listLinkOptions("TRAVEL_FUEL", "2026-10-22")).toHaveLength(1);
    expect(server.callsTo(`GET ${BASE}/link-options`)[0].query).toEqual({ category: "TRAVEL_FUEL", date: "2026-10-22" });
  });
});

describe("create and update", () => {
  it("creates with the client request id, a two-decimal amount string and submit false", async () => {
    const created = await createClaim(body());
    const sent = server.callsTo(`POST ${BASE}`)[0].body as ClaimBody;
    expect(sent).toMatchObject({ clientRequestId: "req-1", amount: "850.00", submit: false, confirmDuplicate: false });
    expect(sent.version).toBeUndefined();
    expect(created.status).toBe("DRAFT");
  });

  it("updates with the version and no client request id", async () => {
    const created = await createClaim(body());
    await updateClaim(created.id, body({ clientRequestId: undefined, note: "Changed", version: created.version }));
    const sent = server.callsTo(`PUT ${BASE}/{id}`)[0].body as ClaimBody;
    expect(sent.version).toBe(created.version);
    expect(sent.clientRequestId).toBeUndefined();
    expect(sent.submit).toBe(false);
  });

  it("answers a repeat of the same create with the first claim", async () => {
    const first = await createClaim(body());
    const again = await createClaim(body());
    expect(again.id).toBe(first.id);
    expect(fake.claims).toHaveLength(1);
  });

  it("refuses a request id used for a different claim, with the server's reason", async () => {
    await createClaim(body());
    await expect(createClaim(body({ amount: "900.00" }))).rejects.toMatchObject({ status: 409, message: expect.stringContaining("different claim") });
  });
});

describe("submit, withdraw, delete and bills", () => {
  it("submits with the version and the duplicate confirmation", async () => {
    const created = await createClaim(body({ noBillReason: "Paid in cash" }));
    await submitClaim(created.id, { version: created.version, confirmDuplicate: false });
    expect(server.callsTo(`POST ${BASE}/{id}/submit`)[0].body).toEqual({ version: created.version, confirmDuplicate: false });
  });

  it("withdraws with the version and deletes without a body", async () => {
    fake.claims = [claimDetail({ id: "c-1", status: "SUBMITTED", version: 4 })];
    await withdrawClaim("c-1", 4);
    expect(server.callsTo(`POST ${BASE}/{id}/withdraw`)[0].body).toEqual({ version: 4 });
    await deleteClaim("c-1");
    expect(server.callsTo(`DELETE ${BASE}/{id}`)[0].body).toBeUndefined();
  });

  it("sends a bill as multipart field file and reads it back as bytes", async () => {
    const created = await createClaim(body());
    const updated = await addBill(created.id, { uri: "file:///cache/a.jpg", name: "bill-1.jpg", type: "image/jpeg" });
    const part = (server.callsTo(`POST ${BASE}/{id}/bills`)[0].body as MultipartBody).multipart[0];
    expect(part).toMatchObject({ fieldName: "file", name: "bill-1.jpg", type: "image/jpeg" });
    const view = await openBill(created.id, updated.bills[0].id);
    expect(view.contentType).toBe("image/jpeg");
    expect(view.dataUri.startsWith("data:image/jpeg;base64,")).toBe(true);
  });
});

describe("refusals", () => {
  it("reaches the caller as an ApiError carrying the server's reason", async () => {
    const created = await createClaim(body());
    const error = await submitClaim(created.id, { version: created.version, confirmDuplicate: false }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(400);
    expect((error as ApiError).message).toMatch(/bill/i);
  });

  it("carries the matching claims of a duplicate warning", async () => {
    fake.claims = [claimDetail({ id: "c-old", status: "SUBMITTED", amount: "850.00", expenseDate: "2026-10-22" })];
    const created = await createClaim(body({ noBillReason: "Cash" }));
    const error = (await submitClaim(created.id, { version: created.version, confirmDuplicate: false }).catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(409);
    expect(error.matches).toEqual(["c-old"]);
  });
});
