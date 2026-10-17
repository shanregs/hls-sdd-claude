import type { AttachmentRef, Person, PlanVisitBody, Prospect, Visit } from "../../src/api/marketingApi";
import type { FakeReply, FakeServer, MultipartBody } from "./fakeServer";
import { colleague, prospect as prospectFixture, visit as visitFixture, ME } from "./visitTaskFixtures";

const BASE = "/api/v1/marketing";

/** The actions a test can make the fake server refuse, by name. */
export type VisitAction = "list" | "get" | "plan" | "complete" | "reschedule" | "cancel" | "attach" | "file";

/** The state and the knobs of the fake marketing visit endpoints (spec 023), which tests inspect and set. */
export interface MarketingFake {
  visits: Visit[];
  prospects: Prospect[];
  colleagues: Person[];
  /** The server's date, used for the follow-up rule and for nothing else. */
  today: string;
  /** Replies to return instead of the normal behaviour, by action. */
  refusals: Partial<Record<VisitAction, FakeReply>>;
  /** The bytes the server holds for each attachment. */
  files: Record<string, { bytes: Uint8Array; contentType: string; name: string }>;
  /** Called when a visit is completed with a follow-up date, so a test can add the follow-up task. */
  onFollowUp?: (visit: Visit) => void;
  /** What tests look at. */
  planned: PlanVisitBody[];
  completions: { id: string; body: Record<string, unknown> }[];
  reschedules: { id: string; body: Record<string, unknown> }[];
  cancels: { id: string; body: Record<string, unknown> }[];
  uploads: { id: string; part: { fieldName: string; name?: string; type?: string } | undefined }[];
  prospectQueries: string[];
}

const refuse = (status: number, reason: string): FakeReply => ({ status, body: { reason } });
const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0, 16, 0x4a, 0x46, 0x49, 0x46, 0, 1]);

export function installMarketing(server: FakeServer, overrides: Partial<MarketingFake> = {}): MarketingFake {
  const fake: MarketingFake = {
    visits: [],
    prospects: [prospectFixture()],
    colleagues: [colleague(), { userId: ME.userId, name: "Manoj Manager" }],
    today: "2026-10-05",
    refusals: {},
    files: {},
    planned: [],
    completions: [],
    reschedules: [],
    cancels: [],
    uploads: [],
    prospectQueries: [],
    ...overrides,
  };

  const find = (id: string) => fake.visits.find((v) => v.id === id);
  const replace = (next: Visit) => {
    fake.visits = fake.visits.map((v) => (v.id === next.id ? next : v));
    return next;
  };

  server.on(`GET ${BASE}/activities`, (request) => {
    if (fake.refusals.list) return fake.refusals.list;
    const { from, to } = request.query;
    const content = fake.visits.filter((v) => (!from || v.date >= from) && (!to || v.date <= to));
    return { status: 200, body: { content, drives: [] } };
  });

  server.on(`GET ${BASE}/activities/{id}`, (request) => {
    if (fake.refusals.get) return fake.refusals.get;
    const found = find(request.params.id);
    return found ? { status: 200, body: found } : refuse(404, "Activity not found.");
  });

  server.on(`POST ${BASE}/activities`, (request) => {
    const body = request.body as PlanVisitBody;
    fake.planned.push(body);
    if (fake.refusals.plan) return fake.refusals.plan;
    if (!body.prospectId) return refuse(400, "A visit is for a prospect or for a School, not both and not neither.");
    if (!body.date) return refuse(400, "The date is required.");
    const target = fake.prospects.find((p) => p.id === body.prospectId);
    if (!target) return refuse(404, "Prospect not found.");
    const attendeeIds = body.attendeeUserIds && body.attendeeUserIds.length > 0 ? body.attendeeUserIds : [ME.userId];
    const created = visitFixture({
      id: `v-new-${fake.visits.length + 1}`,
      type: body.type,
      date: body.date,
      notes: body.notes ?? null,
      prospect: { id: target.id, name: target.name },
      attendees: attendeeIds.map((id) => fake.colleagues.find((c) => c.userId === id) ?? { userId: id, name: "Colleague" }),
    });
    fake.visits = [...fake.visits, created];
    return { status: 201, body: created };
  });

  server.on(`POST ${BASE}/activities/{id}/complete`, (request) => {
    const body = request.body as { outcome?: string; notes?: string; followUpOn?: string; version?: number };
    fake.completions.push({ id: request.params.id, body });
    if (fake.refusals.complete) return fake.refusals.complete;
    const found = find(request.params.id);
    if (!found) return refuse(404, "Activity not found.");
    if (body.version !== found.version) return refuse(409, "The visit was changed meanwhile.");
    if (found.status !== "PLANNED") return refuse(409, "Only a planned visit can be completed.");
    if (!body.outcome || body.outcome.trim() === "") {
      return refuse(400, "An outcome is required to complete a visit: who was met, what was discussed and the next action.");
    }
    if (body.followUpOn && body.followUpOn < found.date) return refuse(400, "The follow-up date cannot be before the visit.");
    const done = replace({
      ...found,
      status: "COMPLETED",
      effectiveStatus: "COMPLETED",
      outcome: body.outcome.trim(),
      notes: body.notes ?? found.notes,
      followUpOn: body.followUpOn ?? null,
      version: found.version + 1,
    });
    if (body.followUpOn) fake.onFollowUp?.(done);
    return { status: 200, body: done };
  });

  server.on(`POST ${BASE}/activities/{id}/reschedule`, (request) => {
    const body = request.body as { date?: string; version?: number };
    fake.reschedules.push({ id: request.params.id, body });
    if (fake.refusals.reschedule) return fake.refusals.reschedule;
    const found = find(request.params.id);
    if (!found) return refuse(404, "Activity not found.");
    if (body.version !== found.version) return refuse(409, "The visit was changed meanwhile.");
    if (found.status !== "PLANNED") return refuse(409, "Only a planned visit can be rescheduled.");
    if (!body.date) return refuse(400, "The new date is required.");
    if (body.date === found.date) return refuse(409, "The activity is already on this date.");
    const moved = replace({
      ...found,
      date: body.date,
      rescheduled: true,
      effectiveStatus: "PLANNED",
      dateHistory: [...found.dateHistory, { from: found.date, to: body.date }],
      version: found.version + 1,
    });
    return { status: 200, body: moved };
  });

  server.on(`POST ${BASE}/activities/{id}/cancel`, (request) => {
    const body = request.body as { reason?: string; version?: number };
    fake.cancels.push({ id: request.params.id, body });
    if (fake.refusals.cancel) return fake.refusals.cancel;
    const found = find(request.params.id);
    if (!found) return refuse(404, "Activity not found.");
    if (body.version !== found.version) return refuse(409, "The visit was changed meanwhile.");
    if (found.status !== "PLANNED") return refuse(409, "Only a planned visit can be cancelled.");
    if (!body.reason || body.reason.trim() === "") return refuse(400, "A reason is required to cancel an activity.");
    const cancelled = replace({
      ...found,
      status: "CANCELLED",
      effectiveStatus: "CANCELLED",
      cancelReason: body.reason.trim(),
      version: found.version + 1,
    });
    return { status: 200, body: cancelled };
  });

  server.on(`POST ${BASE}/activities/{id}/attachments`, (request) => {
    const multipart = (request.body as MultipartBody | undefined)?.multipart ?? [];
    fake.uploads.push({ id: request.params.id, part: multipart[0] });
    if (fake.refusals.attach) return fake.refusals.attach;
    const found = find(request.params.id);
    if (!found) return refuse(404, "Activity not found.");
    if (found.attachments.length >= 10) return refuse(409, "A visit can have at most 10 files.");
    const part = multipart[0];
    const fileId = `vf-${fake.uploads.length}`;
    const ref: AttachmentRef = {
      fileId,
      name: part?.name ?? "photo.jpg",
      sizeBytes: JPEG.length,
      contentType: part?.type ?? "image/jpeg",
    };
    fake.files[fileId] = { bytes: JPEG, contentType: ref.contentType, name: ref.name };
    replace({ ...found, attachments: [...found.attachments, ref] });
    return { status: 201, body: ref };
  });

  server.on(`GET ${BASE}/files/{fileId}`, (request) => {
    if (fake.refusals.file) return fake.refusals.file;
    const file = fake.files[request.params.fileId];
    if (!file) return refuse(404, "File not found.");
    return {
      status: 200,
      rawBody: file.bytes,
      headers: {
        "Content-Type": file.contentType,
        "Content-Length": String(file.bytes.length),
        "Content-Disposition": `attachment; filename="${file.name}"`,
      },
    };
  });

  server.on(`GET ${BASE}/prospects`, (request) => {
    const query = (request.query.query ?? "").toLowerCase();
    fake.prospectQueries.push(request.query.query ?? "");
    const page = Number(request.query.page ?? 0);
    const size = Number(request.query.size ?? 25);
    const matching = fake.prospects.filter((p) => p.name.toLowerCase().includes(query));
    return {
      status: 200,
      body: { content: matching.slice(page * size, page * size + size), page, size, totalElements: matching.length },
    };
  });

  server.on("GET /api/v1/recruitment/interviewers", () => ({ status: 200, body: fake.colleagues }));

  return fake;
}
