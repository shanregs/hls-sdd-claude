import type { Assignee, CreateTaskBody, TaskActions, TaskDetail, TaskSummary, TaskTypeOption } from "../../src/api/tasksApi";
import type { FakeReply, FakeServer, MultipartBody } from "./fakeServer";
import { ME, taskDetail, taskTypes } from "./visitTaskFixtures";

const BASE = "/api/v1/tasks";
const OPEN = ["ASSIGNED", "ACKNOWLEDGED", "IN_PROGRESS"];

export type TaskAction =
  | "list"
  | "summary"
  | "get"
  | "acknowledge"
  | "start"
  | "overdue"
  | "complete"
  | "evidence"
  | "create"
  | "file";

/** The state and the knobs of the fake task endpoints (spec 025), which tests inspect and set. */
export interface TasksFake {
  details: TaskDetail[];
  /** The server's date, used to work out the views. */
  today: string;
  types: TaskTypeOption[];
  assignees: Assignee[];
  refusals: Partial<Record<TaskAction, FakeReply>>;
  files: Record<string, { bytes: Uint8Array; contentType: string; name: string }>;
  steps: { id: string; step: string; body: unknown }[];
  created: CreateTaskBody[];
  evidenceUploads: { id: string; part: { fieldName: string; name?: string; type?: string } | undefined }[];
}

const refuse = (status: number, reason: string): FakeReply => ({ status, body: { reason } });
const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0, 16, 0x4a, 0x46, 0x49, 0x46, 0, 1]);

/** The steps the real server allows, worked out the way spec 025 does, for a task of the signed-in Manager. */
export function actionsOf(d: TaskDetail): TaskActions {
  const mine = d.task.assigneeUserId === ME.userId;
  const status = d.task.status;
  const open = OPEN.includes(status);
  const blockedByReason = d.task.overdue && !d.overdueReason;
  return {
    acknowledge: mine && status === "ASSIGNED",
    start: mine && status === "ACKNOWLEDGED",
    overdueReason: mine && open && d.task.overdue && !d.overdueReason,
    complete: mine && (status === "ACKNOWLEDGED" || status === "IN_PROGRESS") && !blockedByReason,
    addEvidence: mine && (open || status === "COMPLETED") && status !== "ASSIGNED" && d.evidence.length < 5,
    verify: false,
    sendBack: false,
    edit: false,
    cancel: false,
  };
}

export function summaryOf(details: TaskDetail[], today: string): TaskSummary {
  const count = (view: string) => details.filter((d) => inView(d, view, today)).length;
  return {
    today: count("today"),
    upcoming: count("upcoming"),
    overdue: count("overdue"),
    awaiting: count("awaiting"),
    escalated: details.filter((d) => d.task.escalated).length,
    done: count("done"),
  };
}

function inView(d: TaskDetail, view: string, today: string): boolean {
  const { status, dueOn, overdue } = d.task;
  const open = OPEN.includes(status);
  switch (view) {
    case "today":
      return open && dueOn === today;
    case "upcoming":
      return open && dueOn > today;
    case "overdue":
      return open && overdue;
    case "awaiting":
      return status === "COMPLETED";
    case "done":
      return status === "VERIFIED";
    default:
      return status !== "CANCELLED";
  }
}

export function installTasks(server: FakeServer, overrides: Partial<TasksFake> = {}): TasksFake {
  const fake: TasksFake = {
    details: [],
    today: "2026-10-05",
    types: taskTypes(),
    assignees: [{ userId: ME.userId, name: "Manoj Manager" }],
    refusals: {},
    files: {},
    steps: [],
    created: [],
    evidenceUploads: [],
    ...overrides,
  };

  const find = (id: string) => fake.details.find((d) => d.task.id === id);
  const store = (next: TaskDetail): TaskDetail => {
    const withActions = { ...next, actions: actionsOf(next), version: next.version + 1 };
    fake.details = fake.details.map((d) => (d.task.id === next.task.id ? withActions : d));
    return withActions;
  };
  const note = (d: TaskDetail, event: string, before: string | null, after: string | null, text: string | null): TaskDetail => ({
    ...d,
    history: [
      ...d.history,
      { event, actorUserId: ME.userId, actorName: "Manoj Manager", at: "2026-10-05T04:00:00Z", before, after, note: text },
    ],
  });

  server.on(`GET ${BASE}`, (request) => {
    if (fake.refusals.list) return fake.refusals.list;
    const view = request.query.view ?? "all";
    const page = Number(request.query.page ?? 0);
    const size = Number(request.query.size ?? 50);
    const rows = fake.details.filter((d) => inView(d, view, fake.today)).map((d) => d.task);
    return {
      status: 200,
      body: { content: rows.slice(page * size, page * size + size), page, size, totalElements: rows.length },
    };
  });

  server.on(`GET ${BASE}/summary`, () => {
    if (fake.refusals.summary) return fake.refusals.summary;
    return { status: 200, body: summaryOf(fake.details, fake.today) };
  });

  server.on(`GET ${BASE}/types`, () => ({ status: 200, body: fake.types }));
  server.on(`GET ${BASE}/assignees`, () => ({ status: 200, body: fake.assignees }));

  server.on(`GET ${BASE}/{id}`, (request) => {
    if (fake.refusals.get) return fake.refusals.get;
    const found = find(request.params.id);
    return found ? { status: 200, body: found } : refuse(404, "Task not found.");
  });

  const step = (name: TaskAction, path: string, change: (d: TaskDetail, body: Record<string, unknown>) => TaskDetail | FakeReply) => {
    server.on(`POST ${BASE}/{id}/${path}`, (request) => {
      const body = (request.body ?? {}) as Record<string, unknown>;
      fake.steps.push({ id: request.params.id, step: path, body: request.body });
      if (fake.refusals[name]) return fake.refusals[name] as FakeReply;
      const found = find(request.params.id);
      if (!found) return refuse(404, "Task not found.");
      const result = change(found, body);
      if (!("task" in result)) return result;
      return { status: 200, body: store(result as TaskDetail) };
    });
  };

  step("acknowledge", "acknowledge", (d) => {
    if (!d.actions.acknowledge) return refuse(409, "This task cannot be acknowledged now.");
    return note({ ...d, task: { ...d.task, status: "ACKNOWLEDGED" } }, "ACKNOWLEDGED", "ASSIGNED", "ACKNOWLEDGED", null);
  });

  step("start", "start", (d) => {
    if (!d.actions.start) return refuse(409, "This task cannot be started now.");
    return note({ ...d, task: { ...d.task, status: "IN_PROGRESS" } }, "STARTED", "ACKNOWLEDGED", "IN_PROGRESS", null);
  });

  step("overdue", "overdue-reason", (d, body) => {
    const reason = String(body.reason ?? "").trim();
    if (reason.length < 5) return refuse(400, "Give a reason of at least 5 characters.");
    if (!d.actions.overdueReason) return refuse(409, "This task does not need an overdue reason.");
    return note({ ...d, overdueReason: reason }, "OVERDUE_REASON", null, null, reason);
  });

  step("complete", "complete", (d, body) => {
    const text = String(body.note ?? "").trim();
    if (text.length < 10) return refuse(400, "Add a note of at least 10 characters.");
    if (!d.actions.complete) {
      return refuse(409, d.task.overdue && !d.overdueReason ? "Give the reason it is overdue before completing this task." : "This task cannot be completed now.");
    }
    return note(
      { ...d, completionNote: text, completedAt: "2026-10-05T04:30:00Z", task: { ...d.task, status: "COMPLETED" } },
      "COMPLETED",
      d.task.status,
      "COMPLETED",
      text,
    );
  });

  server.on(`POST ${BASE}/{id}/evidence`, (request) => {
    const multipart = (request.body as MultipartBody | undefined)?.multipart ?? [];
    fake.evidenceUploads.push({ id: request.params.id, part: multipart[0] });
    if (fake.refusals.evidence) return fake.refusals.evidence;
    const found = find(request.params.id);
    if (!found) return refuse(404, "Task not found.");
    if (found.evidence.length >= 5) return refuse(409, "A task can have at most 5 evidence files.");
    const part = multipart[0];
    const fileId = `tf-${fake.evidenceUploads.length}`;
    const type = part?.type ?? "image/jpeg";
    fake.files[fileId] = { bytes: JPEG, contentType: type, name: part?.name ?? "photo.jpg" };
    const updated = store({
      ...found,
      evidence: [
        ...found.evidence,
        { fileId, name: part?.name ?? "photo.jpg", contentType: type, sizeBytes: JPEG.length, addedAt: "2026-10-05T04:40:00Z", addedBy: ME.userId },
      ],
    });
    return { status: 201, body: updated };
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

  server.on(`POST ${BASE}`, (request) => {
    const body = request.body as CreateTaskBody;
    fake.created.push(body);
    if (fake.refusals.create) return fake.refusals.create;
    const title = (body.title ?? "").trim();
    if (title.length < 3 || title.length > 120) return refuse(400, "The title must be 3 to 120 characters.");
    if (body.assigneeUserId !== ME.userId) return refuse(400, "A Manager may assign a task only to themselves.");
    if (body.dueOn < fake.today) return refuse(400, "The due date cannot be in the past.");
    const created = taskDetail({
      task: {
        id: `t-new-${fake.details.length + 1}`,
        type: body.type,
        typeLabel: fake.types.find((t) => t.value === body.type)?.label ?? body.type,
        title,
        dueOn: body.dueOn,
        place: body.place ?? null,
      },
      description: body.description ?? null,
      createdByName: "Manoj Manager",
    });
    const withActions = { ...created, actions: actionsOf(created) };
    fake.details = [...fake.details, withActions];
    return { status: 201, body: withActions };
  });

  return fake;
}
