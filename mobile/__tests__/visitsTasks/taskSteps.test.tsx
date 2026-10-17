import { fireEvent, screen } from "@testing-library/react-native";
import { taskSummaryText } from "../../src/tasks/TaskCard";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { actionsOf } from "../support/tasksServer";
import { NO_ACTIONS, taskDetail } from "../support/visitTaskFixtures";

/** Spec 034 T025 (US3): the steps follow the server's `actions` flags, and the detail shows what the server holds. */
const MENU = "My Tasks";
const CARD = "Call the Principal, due 05/10/2026, assigned";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

const base = (overrides: Parameters<typeof taskDetail>[0] = {}) =>
  taskDetail({
    ...overrides,
    task: { id: "t-1", title: "Call the Principal", dueOn: "2026-10-05", type: "FOLLOW_UP", typeLabel: "Follow-up", ...overrides.task },
  });

async function open(detail = base()) {
  const app = await openVisitTaskScreen(MENU, { tasks: { details: [detail] } });
  // the All view lists a task whatever its status, so every fixture can be opened the same way
  await press("All");
  await press(taskSummaryText(detail.task));
  await screen.findByText("History");
  return app;
}

describe("which buttons are shown", () => {
  it("shows a step exactly when its flag is true, never from the status, the dates or the phone clock", async () => {
    await open(base({ actions: { ...NO_ACTIONS } }));

    for (const label of ["Acknowledge", "Start", "Give a reason", "Complete"]) {
      expect(screen.queryByText(label)).toBeNull();
    }
  });

  it("shows all four steps when all four flags are true, and never verify, send back, edit or cancel", async () => {
    await open(
      base({
        actions: { acknowledge: true, start: true, complete: true, overdueReason: true, addEvidence: true, verify: true, sendBack: true, edit: true, cancel: true },
      }),
    );

    for (const label of ["Acknowledge", "Start", "Give a reason", "Complete"]) {
      expect(screen.getByText(label)).toBeTruthy();
    }
    for (const label of ["Verify", "Send back", "Edit", "Cancel", "Cancel task"]) {
      expect(screen.queryByText(label)).toBeNull();
    }
  });

  it("does not use a status that looks actionable when the flags say no", async () => {
    await open(base({ task: { status: "IN_PROGRESS" }, actions: { ...NO_ACTIONS, start: false, complete: false } }));

    expect(screen.queryByText("Complete")).toBeNull();
    expect(screen.getByText("In progress")).toBeTruthy();
  });
});

describe("acknowledging and starting", () => {
  it("posts with no body and shows the server's answer only after it arrives", async () => {
    const { tasks } = await open();
    expect(screen.getByText("Assigned")).toBeTruthy();

    await pressText("Acknowledge");

    expect(await screen.findByText("Acknowledged")).toBeTruthy();
    expect(tasks.steps).toEqual([{ id: "t-1", step: "acknowledge", body: undefined }]);
    expect(screen.queryByText("Acknowledge")).toBeNull();
    expect(screen.getByText("Acknowledged by Manoj Manager on 05/10/2026 09:30")).toBeTruthy();

    await pressText("Start");
    expect(await screen.findByText("In progress")).toBeTruthy();
    expect(tasks.steps.map((s) => s.step)).toEqual(["acknowledge", "start"]);
    expect(tasks.steps[1].body).toBeUndefined();
  });

  it("shows the server's reason when the task changed on the web, reloads it and changes nothing on screen", async () => {
    const { tasks, server } = await open();
    tasks.details = [base({ task: { status: "ACKNOWLEDGED" }, actions: { ...NO_ACTIONS } })];

    await pressText("Acknowledge");

    expect(await screen.findByText("This task cannot be acknowledged now.")).toBeTruthy();
    expect(server.callsTo("GET /api/v1/tasks/{id}").length).toBeGreaterThanOrEqual(2);
    expect(await screen.findByText("Acknowledged")).toBeTruthy();
  });

  it("says nothing was sent when offline and keeps the task as it was", async () => {
    const { server } = await open();
    server.offline = true;

    await pressText("Acknowledge");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(screen.getByText("Assigned")).toBeTruthy();
    expect(screen.getByText("Acknowledge")).toBeTruthy();
  });

  it("sends one request for a double tap", async () => {
    const { tasks } = await open();
    const button = screen.getByText("Acknowledge");

    await fireEvent.press(button);
    await fireEvent.press(button);

    await screen.findByText("Acknowledged");
    expect(tasks.steps.filter((s) => s.step === "acknowledge")).toHaveLength(1);
  });
});

describe("the details", () => {
  it("shows who assigned it, the due date, place, linked record and description", async () => {
    await open(
      base({
        description: "Ask about the new timetable",
        createdByName: "Divya Director",
        task: { place: "Anna Nagar", linkedLabel: "Green Valley Public School" },
      }),
    );

    expect(screen.getByText("Follow-up, due 05/10/2026")).toBeTruthy();
    expect(screen.getByText("Assigned by Divya Director")).toBeTruthy();
    expect(screen.getByText("Ask about the new timetable")).toBeTruthy();
    expect(screen.getByText("Place: Anna Nagar")).toBeTruthy();
    expect(screen.getByText("About: Green Valley Public School")).toBeTruthy();
  });

  it("shows the overdue reason, the completion note, who verified it and the follow-up tasks", async () => {
    await open(
      base({
        task: { status: "COMPLETED", overdue: true, daysOverdue: 3, escalated: true },
        overdueReason: "The Principal was away",
        completionNote: "Met the Principal and left the forms",
        verifiedByName: "Divya Director",
        verifiedAt: "2026-10-04T06:00:00Z",
        followUpTaskIds: ["f-1", "f-2"],
        actions: { ...NO_ACTIONS },
      }),
    );

    expect(screen.getByText("3 days overdue")).toBeTruthy();
    expect(screen.getByText("Escalated to the Director")).toBeTruthy();
    expect(screen.getByText("Reason for being overdue: The Principal was away")).toBeTruthy();
    expect(screen.getByText("Completion note: Met the Principal and left the forms")).toBeTruthy();
    expect(screen.getByText("Verified by Divya Director on 04/10/2026 11:30")).toBeTruthy();
    expect(screen.getByText("2 follow-up tasks were created")).toBeTruthy();
  });

  it("lists the history oldest first with who and when", async () => {
    await open(
      base({
        history: [
          { event: "CREATED", actorUserId: null, actorName: "Divya Director", at: "2026-10-01T05:30:00Z", before: null, after: "ASSIGNED", note: null },
          { event: "ACKNOWLEDGED", actorUserId: "u-1", actorName: "Manoj Manager", at: "2026-10-02T04:00:00Z", before: "ASSIGNED", after: "ACKNOWLEDGED", note: null },
          { event: "ESCALATED", actorUserId: null, actorName: null, at: "2026-10-04T04:00:00Z", before: null, after: null, note: "3 days overdue" },
        ],
      }),
    );

    const lines = screen
      .getAllByText(/ by .* on \d\d\/\d\d\/\d{4} \d\d:\d\d/)
      .map((n) => (Array.isArray(n.props.children) ? n.props.children.join("") : String(n.props.children)));
    expect(lines).toEqual([
      "Created by Divya Director on 01/10/2026 11:00",
      "Acknowledged by Manoj Manager on 02/10/2026 09:30",
      "Escalated by the system on 04/10/2026 09:30: 3 days overdue",
    ]);
  });

  it("says a task that the server no longer finds is not found", async () => {
    const { tasks } = await openVisitTaskScreen(MENU, { tasks: { details: [base()] } });
    await screen.findByText("Call the Principal");
    tasks.details = [];

    await press(CARD);

    expect(await screen.findByText("Task not found")).toBeTruthy();
    expect(screen.getByText("Task not found.")).toBeTruthy();
    expect(actionsOf(base()).acknowledge).toBe(true);
  });
});
