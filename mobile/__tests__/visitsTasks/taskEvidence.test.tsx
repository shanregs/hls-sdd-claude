import { fireEvent, screen } from "@testing-library/react-native";
import { taskSummaryText } from "../../src/tasks/TaskCard";
import { actionsOf } from "../support/tasksServer";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { taskDetail } from "../support/visitTaskFixtures";

/** Spec 034 T027 (US3): photo evidence for a task, up to the limit of 5, never required. */
const MENU = "My Tasks";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

const file = (n: number) => ({
  fileId: `e-${n}`,
  name: `evidence-${n}.jpg`,
  contentType: "image/jpeg",
  sizeBytes: 2048,
  addedAt: "2026-10-04T05:00:00Z",
  addedBy: "u-1",
});

function inProgress(evidenceCount = 0) {
  const detail = taskDetail({
    task: { id: "t-1", title: "Visit the school", dueOn: "2026-10-05", status: "IN_PROGRESS" },
    evidence: Array.from({ length: evidenceCount }, (_, i) => file(i + 1)),
    actions: {} as never,
  });
  return { ...detail, actions: actionsOf(detail) };
}

async function open(detail = inProgress(), setup: Parameters<typeof openVisitTaskScreen>[1] = {}) {
  const app = await openVisitTaskScreen(MENU, { tasks: { details: [detail] }, ...setup });
  await press("All");
  await press(taskSummaryText(detail.task));
  await screen.findByText("History");
  return app;
}

describe("the evidence", () => {
  it("shows n of 5 from the server's list, each file with name, type and size, and a thumbnail for an image", async () => {
    const { tasks } = await openVisitTaskScreen(MENU, { tasks: { details: [inProgress(2)] } });
    tasks.files["e-1"] = { bytes: new Uint8Array([1, 2, 3]), contentType: "image/jpeg", name: "evidence-1.jpg" };
    await press("All");
    await press(taskSummaryText(inProgress(2).task));

    expect(await screen.findByText("Evidence (2 of 5)")).toBeTruthy();
    expect(screen.getByText("evidence-1.jpg, JPG, 2 KB")).toBeTruthy();
    expect(screen.getByText("evidence-2.jpg, JPG, 2 KB")).toBeTruthy();
    expect(await screen.findByLabelText("Photo evidence-1.jpg")).toBeTruthy();
  });

  it("offers photos only while the server's addEvidence flag is true, and a photo is optional", async () => {
    await open(taskDetail({ task: { id: "t-1", title: "Visit the school", dueOn: "2026-10-05", status: "ASSIGNED" }, actions: {} as never }));

    expect(screen.getByText("Evidence (0 of 5)")).toBeTruthy();
    expect(screen.queryByText("Take a photo")).toBeNull();
    expect(screen.queryByText("Upload photos")).toBeNull();
  });

  it("stops offering photos at 5", async () => {
    await open(inProgress(5));

    expect(screen.getByText("Evidence (5 of 5)")).toBeTruthy();
    expect(screen.queryByText("Take a photo")).toBeNull();
    expect(screen.queryByText("Choose from gallery")).toBeNull();
  });
});

describe("adding photos", () => {
  it("uploads each photo to the task as a file, and then shows the server's evidence", async () => {
    const { tasks } = await open();
    await pressText("Take a photo");
    await pressText("Choose from gallery");
    expect(screen.getByLabelText("Photos, 2 of 5")).toBeTruthy();

    await press("Upload photos");

    expect(await screen.findByText("Evidence (2 of 5)")).toBeTruthy();
    expect(tasks.evidenceUploads.map((u) => u.part)).toEqual([
      expect.objectContaining({ fieldName: "file", name: "camera.jpg", type: "image/jpeg" }),
      expect.objectContaining({ fieldName: "file", name: "gallery.png", type: "image/png" }),
    ]);
    expect(screen.queryByText("Upload photos")).toBeNull();
    expect(screen.getByText("In progress")).toBeTruthy();
  });

  it("names a photo that the server refuses at the limit, and never changes the task's status", async () => {
    const { tasks } = await open(inProgress(4));
    await pressText("Take a photo");
    // someone adds a fifth file on the web before this upload reaches the server
    tasks.refusals.evidence = { status: 409, body: { reason: "A task can have at most 5 evidence files." } };

    await press("Upload photos");

    expect(await screen.findByText("These photos were not added:")).toBeTruthy();
    expect(screen.getByText("camera.jpg: A task can have at most 5 evidence files.")).toBeTruthy();
    expect(screen.getByText("Evidence (4 of 5)")).toBeTruthy();
    expect(tasks.details[0].task.status).toBe("IN_PROGRESS");
    expect(screen.getByText("In progress")).toBeTruthy();
  });

  it("names a photo that the server refuses for its type, and the task stays as it was", async () => {
    const { tasks } = await open(inProgress(), {
      tasks: { details: [inProgress()], refusals: { evidence: { status: 400, body: { reason: "Only jpg, png, pdf, docx, xlsx and txt files can be uploaded." } } } },
    });
    await pressText("Take a photo");

    await press("Upload photos");

    expect(await screen.findByText("camera.jpg: Only jpg, png, pdf, docx, xlsx and txt files can be uploaded.")).toBeTruthy();
    expect(screen.getByText("Evidence (0 of 5)")).toBeTruthy();
    expect(tasks.details[0].evidence).toHaveLength(0);
  });

  it("says nothing was sent when offline and keeps the chosen photo", async () => {
    const { server } = await open();
    await pressText("Take a photo");
    server.offline = true;

    await press("Upload photos");

    expect(await screen.findByText(/No connection/)).toBeTruthy();
    expect(screen.getByLabelText("Photos, 1 of 5")).toBeTruthy();
  });
});
