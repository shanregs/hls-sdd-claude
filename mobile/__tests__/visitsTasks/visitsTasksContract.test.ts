import * as SecureStore from "expo-secure-store";
import { ApiError } from "../../src/api/errors";
import {
  attachVisitPhoto,
  cancelVisit,
  completeVisit,
  getVisit,
  listColleagues,
  listProspects,
  listVisits,
  openVisitFile,
  planVisit,
  rescheduleVisit,
} from "../../src/api/marketingApi";
import {
  acknowledgeTask,
  addTaskEvidence,
  completeTask,
  createTask,
  getTask,
  getTaskSummary,
  giveOverdueReason,
  listAssignees,
  listTaskTypes,
  listTasks,
  openTaskFile,
  startTask,
} from "../../src/api/tasksApi";
import { setAccessToken } from "../../src/security/memoryToken";
import { newServer } from "../support/fakeServer";
import { installMarketing, type MarketingFake } from "../support/marketingServer";
import { installTasks, type TasksFake } from "../support/tasksServer";
import { ME, taskDetail, visit } from "../support/visitTaskFixtures";

/** Spec 034 T006 to T008: the request shapes of contracts/server-calls.md against the fake servers. */
let server: ReturnType<typeof newServer>;
let marketing: MarketingFake;
let tasks: TasksFake;

beforeEach(async () => {
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  setAccessToken("access-1");
  server = newServer();
  tasks = installTasks(server);
  marketing = installMarketing(server);
});

const FILE = { uri: "file:///cache/a.jpg", name: "a.jpg", type: "image/jpeg" };

describe("visits", () => {
  it("lists a date range of the Manager's own visits and returns only the visits", async () => {
    marketing.visits = [visit({ id: "v-1", date: "2026-10-06" }), visit({ id: "v-2", date: "2026-11-02" })];

    const found = await listVisits("2026-10-01", "2026-10-31");

    expect(found.map((v) => v.id)).toEqual(["v-1"]);
    const call = server.callsTo("GET /api/v1/marketing/activities")[0];
    expect(call.query).toEqual({ from: "2026-10-01", to: "2026-10-31", mine: "true" });
  });

  it("reads one visit", async () => {
    marketing.visits = [visit({ id: "v-1" })];

    expect((await getVisit("v-1")).prospect?.name).toBe("Green Valley Public School");
  });

  it("plans against a prospect, leaving out colleagues and notes when none", async () => {
    await planVisit({ prospectId: "p-1", type: "CALL", date: "2026-10-07" });

    expect(server.callsTo("POST /api/v1/marketing/activities")[0].body).toEqual({
      prospectId: "p-1",
      type: "CALL",
      date: "2026-10-07",
    });
  });

  it("completes, moves and cancels with the version it holds", async () => {
    marketing.visits = [visit({ id: "v-1", version: 3 }), visit({ id: "v-2", version: 1 }), visit({ id: "v-3", version: 0 })];

    await completeVisit("v-1", { outcome: "Met the Principal", notes: "n", followUpOn: "2026-10-12", version: 3 });
    await rescheduleVisit("v-2", { date: "2026-10-09", version: 1 });
    await cancelVisit("v-3", { reason: "Principal away", version: 0 });

    expect(marketing.completions[0].body).toEqual({ outcome: "Met the Principal", notes: "n", followUpOn: "2026-10-12", version: 3 });
    expect(marketing.reschedules[0].body).toEqual({ date: "2026-10-09", version: 1 });
    expect(marketing.cancels[0].body).toEqual({ reason: "Principal away", version: 0 });
  });

  it("turns a stale version into an error carrying the server's reason", async () => {
    marketing.visits = [visit({ id: "v-1", version: 4 })];

    await expect(completeVisit("v-1", { outcome: "x", version: 3 })).rejects.toMatchObject({
      status: 409,
      message: "The visit was changed meanwhile.",
    });
    await expect(completeVisit("v-1", { outcome: "x", version: 3 })).rejects.toBeInstanceOf(ApiError);
  });

  it("uploads one file under the field name file, and opens an attachment with its details", async () => {
    marketing.visits = [visit({ id: "v-1" })];

    const ref = await attachVisitPhoto("v-1", FILE);
    const view = await openVisitFile(ref.fileId);

    expect(marketing.uploads[0]).toMatchObject({ id: "v-1", part: { fieldName: "file", name: "a.jpg", type: "image/jpeg" } });
    expect(view).toMatchObject({ contentType: "image/jpeg", fileName: "a.jpg" });
    expect(view.dataUri.startsWith("data:image/jpeg;base64,")).toBe(true);
  });

  it("searches prospects by the typed text, 25 at a time, and lists colleagues", async () => {
    await listProspects({ query: " green ", page: 0, size: 25 });
    await listProspects();
    const colleagues = await listColleagues();

    const calls = server.callsTo("GET /api/v1/marketing/prospects");
    expect(calls[0].query).toEqual({ page: "0", size: "25", query: "green" });
    expect(calls[1].query).toEqual({ page: "0", size: "25" });
    expect(colleagues.map((c) => c.name)).toContain("Divya Director");
  });
});

describe("tasks", () => {
  beforeEach(() => {
    tasks.details = [taskDetail({ task: { id: "t-1" } })];
  });

  it("lists a view a page at a time and reads the summary", async () => {
    const page = await listTasks("all", 1, 25);
    const summary = await getTaskSummary();

    expect(server.callsTo("GET /api/v1/tasks")[0].query).toEqual({ view: "all", page: "1", size: "25" });
    expect(page).toMatchObject({ page: 1, size: 25 });
    expect(summary).toHaveProperty("overdue");
  });

  it("reads a task's detail with its actions", async () => {
    const detail = await getTask("t-1");

    expect(detail.actions.acknowledge).toBe(true);
    expect(detail.history[0].event).toBe("CREATED");
  });

  it("acknowledges and starts with no body, gives a reason and completes with the bodies the server reads", async () => {
    await acknowledgeTask("t-1");
    await startTask("t-1");
    await giveOverdueReason("t-1", { reason: "Principal was away", followUpOn: "2026-10-12" }).catch(() => undefined);
    await completeTask("t-1", { note: "Met the Principal and left the forms" });

    const steps = tasks.steps.map((s) => s.step);
    expect(steps).toEqual(["acknowledge", "start", "overdue-reason", "complete"]);
    expect(tasks.steps[0].body).toBeUndefined();
    expect(tasks.steps[1].body).toBeUndefined();
    expect(tasks.steps[2].body).toEqual({ reason: "Principal was away", followUpOn: "2026-10-12" });
    expect(tasks.steps[3].body).toEqual({ note: "Met the Principal and left the forms" });
  });

  it("adds evidence as a multipart file and downloads it", async () => {
    await acknowledgeTask("t-1");
    await startTask("t-1");

    const detail = await addTaskEvidence("t-1", FILE);
    const view = await openTaskFile(detail.evidence[0].fileId);

    expect(tasks.evidenceUploads[0]).toMatchObject({ id: "t-1", part: { fieldName: "file", name: "a.jpg", type: "image/jpeg" } });
    expect(detail.evidence).toHaveLength(1);
    expect(view.fileName).toBe("a.jpg");
  });

  it("lists types and assignees and creates a task for the caller", async () => {
    const types = await listTaskTypes();
    const assignees = await listAssignees();
    const created = await createTask({ type: "FOLLOW_UP", title: "Call the Principal", assigneeUserId: ME.userId, dueOn: "2026-10-08" });

    expect(types).toHaveLength(9);
    expect(assignees).toEqual([{ userId: ME.userId, name: "Manoj Manager" }]);
    expect(tasks.created[0]).toEqual({ type: "FOLLOW_UP", title: "Call the Principal", assigneeUserId: ME.userId, dueOn: "2026-10-08" });
    expect(created.task.title).toBe("Call the Principal");
  });

  it("turns a refusal into an error carrying the server's reason", async () => {
    await expect(startTask("t-1")).rejects.toMatchObject({ status: 409, message: "This task cannot be started now." });
    await expect(getTask("missing")).rejects.toMatchObject({ status: 404, message: "Task not found." });
  });
});
