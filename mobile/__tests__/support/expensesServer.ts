import type { Area, BillRef, ClaimBody, ClaimDetail, ClaimStatus, LinkOption } from "../../src/api/expensesApi";
import { area, bill, CATEGORY_LABELS, claimDetail, ME, STATUS_LABELS } from "./expenseFixtures";
import type { FakeReply, FakeRequest, FakeServer, MultipartBody } from "./fakeServer";

const BASE = "/api/v1/my-expenses";

export type ExpenseStep =
  | "list"
  | "get"
  | "areas"
  | "links"
  | "create"
  | "update"
  | "delete"
  | "submit"
  | "withdraw"
  | "bill"
  | "openBill";

/** The state and the knobs of the fake My Expenses endpoints (spec 015), which tests inspect and set. */
export interface ExpensesFake {
  claims: ClaimDetail[];
  /** The Zones offered besides Head office. */
  areas: Area[];
  /** Planned work offered for Travel and Fuel. */
  linkOptions: LinkOption[];
  /** Induction batches offered for Training Stay. */
  batches: LinkOption[];
  /** The server's business date. */
  today: string;
  /** The signed-in claimant; claims of anyone else answer 404. */
  userId: string;
  /** A step answered with this reply, whatever the request (sticky until removed). */
  refusals: Partial<Record<ExpenseStep, FakeReply>>;
  /** Steps whose next reply is lost after the server acted: the number of replies still to drop. */
  loseReply: Partial<Record<ExpenseStep, number>>;
  /** clientRequestId to the claim it made. */
  requests: Map<string, { id: string; body: ClaimBody }>;
  /** Every bill part received, in order: the claim id and the part. */
  uploads: { id: string; part: { fieldName: string; name?: string; type?: string; uri?: string } | undefined }[];
  files: Record<string, { bytes: Uint8Array; contentType: string }>;
  /** The order in which steps reached the server (`create`, `bill`, `submit`...), including those whose reply was lost. */
  steps: { step: ExpenseStep; id?: string }[];
}

const refuse = (status: number, reason: string, extra: Record<string, unknown> = {}): FakeReply => ({
  status,
  body: { reason, ...extra },
});
const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0, 16, 0x4a, 0x46, 0x49, 0x46, 0, 1]);

/** The buttons the real server allows on the claimant's own claim, worked out the way spec 015 does. */
export function actionsOfClaim(status: ClaimStatus): string[] {
  switch (status) {
    case "DRAFT":
      return ["EDIT", "SUBMIT", "DELETE", "ADD_BILL"];
    case "REJECTED":
      return ["EDIT", "SUBMIT", "ADD_BILL"];
    case "SUBMITTED":
      return ["WITHDRAW"];
    default:
      return [];
  }
}

const dayNumber = (iso: string) => Math.floor(Date.parse(`${iso}T00:00:00Z`) / 86_400_000);

export function installExpenses(server: FakeServer, overrides: Partial<ExpensesFake> = {}): ExpensesFake {
  const fake: ExpensesFake = {
    claims: [],
    areas: [area()],
    linkOptions: [],
    batches: [],
    today: "2026-10-23",
    userId: ME.id,
    refusals: {},
    loseReply: {},
    requests: new Map(),
    uploads: [],
    files: {},
    steps: [],
    ...overrides,
  };

  const find = (id: string) => fake.claims.find((c) => c.id === id && c.claimantId === fake.userId);
  const store = (next: ClaimDetail): ClaimDetail => {
    const stored: ClaimDetail = {
      ...next,
      statusLabel: STATUS_LABELS[next.status],
      categoryLabel: CATEGORY_LABELS[next.category],
      billCount: next.bills.filter((b) => !b.removed).length,
      allowedActions: actionsOfClaim(next.status),
      version: next.version + 1,
    };
    fake.claims = fake.claims.some((c) => c.id === stored.id)
      ? fake.claims.map((c) => (c.id === stored.id ? stored : c))
      : [...fake.claims, stored];
    return stored;
  };
  const note = (c: ClaimDetail, event: string, reason: string | null = null): ClaimDetail => ({
    ...c,
    history: [...c.history, { at: `${fake.today}T04:00:00Z`, actorId: fake.userId, actorName: ME.displayName, event, reason, detail: null }],
  });
  const areaName = (zoneId: string | null) =>
    zoneId === null ? "Head office" : (fake.areas.find((a) => a.id === zoneId)?.name ?? "Unknown zone");

  /** Answers the step's standing refusal, and drops the reply of a step whose reply is to be lost. */
  const guard = (step: ExpenseStep, request: FakeRequest, act: () => FakeReply): FakeReply => {
    fake.steps.push({ step, id: request.params.id });
    const refusal = fake.refusals[step];
    if (refusal) return refusal;
    const reply = act();
    const lost = fake.loseReply[step] ?? 0;
    if (lost > 0 && reply.status < 300) {
      fake.loseReply[step] = lost - 1;
      return { status: 200, drop: true };
    }
    return reply;
  };

  const amountProblem = (body: ClaimBody): string | null => {
    const amount = Number(body.amount);
    if (!Number.isFinite(amount) || amount <= 0) return "amount must be above zero";
    if (!/^\d+(\.\d{1,2})?$/.test(String(body.amount))) return "amount can have at most two decimals";
    return null;
  };

  server.on(`GET ${BASE}`, (request) =>
    guard("list", request, () => {
      const page = Number(request.query.page ?? 0);
      const size = Number(request.query.size ?? 50);
      let rows = fake.claims.filter((c) => c.claimantId === fake.userId);
      if (request.query.status) rows = rows.filter((c) => c.status === request.query.status);
      if (request.query.month) rows = rows.filter((c) => c.expenseDate.startsWith(request.query.month));
      rows = [...rows].sort((a, b) => b.expenseDate.localeCompare(a.expenseDate));
      const owed = fake.claims
        .filter((c) => c.claimantId === fake.userId && c.status === "APPROVED" && c.paidBy === "CLAIMANT")
        .reduce((sum, c) => sum + Number(c.amount), 0);
      return {
        status: 200,
        body: { content: rows.slice(page * size, page * size + size), page, size, totalElements: rows.length, owed: owed.toFixed(2) },
      };
    }),
  );

  server.on(`GET ${BASE}/areas`, (request) =>
    guard("areas", request, () => ({ status: 200, body: [{ id: null, name: "Head office" }, ...fake.areas] })),
  );

  server.on(`GET ${BASE}/link-options`, (request) =>
    guard("links", request, () => ({
      status: 200,
      body: request.query.category === "TRAVEL_FUEL" ? fake.linkOptions : request.query.category === "TRAINING_STAY" ? fake.batches : [],
    })),
  );

  server.on(`GET ${BASE}/{id}`, (request) =>
    guard("get", request, () => {
      const found = find(request.params.id);
      return found ? { status: 200, body: found } : refuse(404, "Claim not found.");
    }),
  );

  server.on(`POST ${BASE}`, (request) =>
    guard("create", request, () => {
      const body = request.body as ClaimBody;
      const known = body.clientRequestId ? fake.requests.get(body.clientRequestId) : undefined;
      if (known) {
        const same =
          known.body.expenseDate === body.expenseDate &&
          known.body.amount === body.amount &&
          known.body.category === body.category &&
          known.body.zoneId === body.zoneId &&
          known.body.note === body.note;
        if (!same) return refuse(409, "That request id was already used for a different claim.");
        return { status: 200, body: find(known.id) };
      }
      const problem = amountProblem(body);
      if (problem) return refuse(400, problem);
      const created = store(
        note(
          claimDetail({
            id: `c-${fake.claims.length + 1}`,
            claimantId: fake.userId,
            status: "DRAFT",
            expenseDate: body.expenseDate,
            amount: Number(body.amount).toFixed(2),
            category: body.category,
            zoneId: body.zoneId,
            areaName: areaName(body.zoneId),
            note: body.note,
            paidBy: body.paidBy,
            reason: body.reason,
            noBillReason: body.noBillReason,
            link: body.link
              ? ([...fake.linkOptions, ...fake.batches].find((o) => o.ref === body.link?.ref) ?? {
                  kind: body.link.kind,
                  kindLabel: body.link.kind,
                  ref: body.link.ref,
                  date: null,
                  label: body.link.ref,
                })
              : null,
            linkLabel: body.link ? ([...fake.linkOptions, ...fake.batches].find((o) => o.ref === body.link?.ref)?.label ?? null) : null,
            bills: [],
            version: -1,
          }),
          "CREATED",
        ),
      );
      if (body.clientRequestId) fake.requests.set(body.clientRequestId, { id: created.id, body });
      return { status: 201, body: created };
    }),
  );

  server.on(`PUT ${BASE}/{id}`, (request) =>
    guard("update", request, () => {
      const found = find(request.params.id);
      if (!found) return refuse(404, "Claim not found.");
      const body = request.body as ClaimBody;
      if (found.status !== "DRAFT" && found.status !== "REJECTED") return refuse(409, `Only a Draft or Rejected claim can be edited; this one is ${found.statusLabel}.`);
      if (body.version !== found.version) return refuse(409, "This claim was changed by someone else. Reload and try again.");
      const problem = amountProblem(body);
      if (problem) return refuse(400, problem);
      const option = body.link ? [...fake.linkOptions, ...fake.batches].find((o) => o.ref === body.link?.ref) : undefined;
      return {
        status: 200,
        body: store(
          note(
            {
              ...found,
              expenseDate: body.expenseDate,
              amount: Number(body.amount).toFixed(2),
              category: body.category,
              zoneId: body.zoneId,
              areaName: areaName(body.zoneId),
              note: body.note,
              paidBy: body.paidBy,
              reason: body.reason,
              noBillReason: body.noBillReason,
              link: option ?? null,
              linkLabel: option?.label ?? null,
            },
            "EDITED",
          ),
        ),
      };
    }),
  );

  server.on(`DELETE ${BASE}/{id}`, (request) =>
    guard("delete", request, () => {
      const found = find(request.params.id);
      if (!found) return refuse(404, "Claim not found.");
      if (found.status !== "DRAFT") return refuse(409, "Only a Draft claim can be deleted.");
      fake.claims = fake.claims.filter((c) => c.id !== found.id);
      return { status: 204 };
    }),
  );

  server.on(`POST ${BASE}/{id}/submit`, (request) =>
    guard("submit", request, () => {
      const found = find(request.params.id);
      if (!found) return refuse(404, "Claim not found.");
      const body = request.body as { version: number; confirmDuplicate: boolean };
      if (found.status !== "DRAFT" && found.status !== "REJECTED") {
        return refuse(409, `Only a Draft or Rejected claim can be submitted; this one is ${found.statusLabel}.`);
      }
      if (body.version !== found.version) return refuse(409, "This claim was changed by someone else. Reload and try again.");
      const age = dayNumber(fake.today) - dayNumber(found.expenseDate);
      if (age < 0) return refuse(400, "The expense date is in the future.");
      if (age > 60) return refuse(400, "The expense is older than 60 days.");
      if (found.category === "OTHER" && !found.reason) return refuse(400, "A reason is required for Other.");
      if (found.category === "TRAINING_STAY" && !found.link) return refuse(400, "A batch is required for Training Stay.");
      if (found.bills.filter((b) => !b.removed).length === 0 && !found.noBillReason) {
        return refuse(400, "Add a bill or give the reason there is no bill.");
      }
      if (!body.confirmDuplicate) {
        const matches = fake.claims.filter(
          (c) =>
            c.id !== found.id &&
            c.claimantId === found.claimantId &&
            c.expenseDate === found.expenseDate &&
            c.amount === found.amount &&
            c.category === found.category &&
            c.status !== "DRAFT",
        );
        if (matches.length > 0) return refuse(409, "You may have raised this already.", { matches: matches.map((c) => c.id) });
      }
      return {
        status: 200,
        body: store(note({ ...found, status: "SUBMITTED", submittedAt: `${fake.today}T04:00:00Z`, ageDays: 0 }, "SUBMITTED")),
      };
    }),
  );

  server.on(`POST ${BASE}/{id}/withdraw`, (request) =>
    guard("withdraw", request, () => {
      const found = find(request.params.id);
      if (!found) return refuse(404, "Claim not found.");
      if (found.status !== "SUBMITTED") return refuse(409, `Only a Submitted claim can be withdrawn; this one is ${found.statusLabel}.`);
      return { status: 200, body: store(note({ ...found, status: "DRAFT", submittedAt: null, ageDays: null }, "WITHDRAWN")) };
    }),
  );

  server.on(`POST ${BASE}/{id}/bills`, (request) =>
    guard("bill", request, () => {
      const multipart = (request.body as MultipartBody | undefined)?.multipart ?? [];
      const part = multipart[0];
      fake.uploads.push({ id: request.params.id, part });
      const found = find(request.params.id);
      if (!found) return refuse(404, "Claim not found.");
      if (found.status !== "DRAFT" && found.status !== "REJECTED") return refuse(409, `Only a Draft or Rejected claim can take bills; this one is ${found.statusLabel}.`);
      if (!/\.(jpe?g|png|pdf)$/i.test(part?.name ?? "")) return refuse(400, "A bill must be a jpg, png or pdf file.");
      if (found.bills.filter((b) => !b.removed).length >= 5) return refuse(400, "A claim can have at most 5 bills.");
      const id = `b-${fake.uploads.length}`;
      const contentType = part?.type ?? "image/jpeg";
      fake.files[id] = { bytes: JPEG, contentType };
      const added: BillRef = bill({ id, name: part?.name ?? "bill.jpg", contentType, sizeBytes: JPEG.length });
      return { status: 201, body: store(note({ ...found, bills: [...found.bills, added] }, "BILL_ADDED")) };
    }),
  );

  server.on(`GET ${BASE}/{id}/bills/{billId}`, (request) =>
    guard("openBill", request, () => {
      const found = find(request.params.id);
      const file = fake.files[request.params.billId];
      if (!found || !file) return refuse(404, "Bill not found.");
      return {
        status: 200,
        rawBody: file.bytes,
        headers: { "Content-Type": file.contentType, "Content-Length": String(file.bytes.length), "Cache-Control": "no-store" },
      };
    }),
  );

  return fake;
}
