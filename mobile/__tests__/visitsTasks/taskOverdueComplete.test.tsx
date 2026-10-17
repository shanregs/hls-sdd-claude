import { fireEvent, screen } from "@testing-library/react-native";
import { taskSummaryText } from "../../src/tasks/TaskCard";
import { chooseDate } from "../support/leaveActions";
import { actionsOf } from "../support/tasksServer";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { taskDetail } from "../support/visitTaskFixtures";

/** Spec 034 T026 (US3): the overdue reason and completing a task, with the lengths the server asks for. */
const MENU = "My Tasks";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

function overdueTask(status: "ACKNOWLEDGED" | "IN_PROGRESS" = "IN_PROGRESS") {
  const detail = taskDetail({
    task: { id: "t-1", title: "Visit the school", dueOn: "2026-10-01", overdue: true, daysOverdue: 4, status },
    actions: {} as never,
  });
  return { ...detail, actions: actionsOf(detail) };
}

async function open(detail = overdueTask(), setup: Parameters<typeof openVisitTaskScreen>[1] = {}) {
  const app = await openVisitTaskScreen(MENU, { tasks: { details: [detail] }, ...setup });
  await press("All");
  await press(taskSummaryText(detail.task));
  await screen.findByText("History");
  return app;
}

describe("an overdue task", () => {
  it("offers Give a reason and not Complete until a reason has been given", async () => {
    await open();

    expect(screen.getByText("Give a reason")).toBeTruthy();
    expect(screen.queryByText("Complete")).toBeNull();
  });

  it("keeps Send disabled until the reason has 5 characters, saying how many more are needed", async () => {
    await open();
    await pressText("Give a reason");

    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(true);
    expect(screen.getByText("5 more characters needed")).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Away");
    expect(screen.getByText("1 more character needed")).toBeTruthy();
    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(true);
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Away.");
    expect(screen.queryByText(/more character/)).toBeNull();
    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(false);
  });

  it("posts the reason with the optional follow-up date, then shows the reason and offers Complete", async () => {
    const { tasks } = await open();
    await pressText("Give a reason");
    await fireEvent.changeText(screen.getByLabelText("Reason"), "  The Principal was away  ");
    await chooseDate("Follow-up date" as never, "not chosen", "Monday 12 October 2026");

    await press("Send");

    expect(await screen.findByText("Reason for being overdue: The Principal was away")).toBeTruthy();
    expect(tasks.steps).toEqual([
      { id: "t-1", step: "overdue-reason", body: { reason: "The Principal was away", followUpOn: "2026-10-12" } },
    ]);
    expect(screen.getByText("Complete")).toBeTruthy();
    expect(screen.queryByText("Give a reason")).toBeNull();
  });

  it("sends the reason without a date when none is chosen", async () => {
    const { tasks } = await open();
    await pressText("Give a reason");
    await fireEvent.changeText(screen.getByLabelText("Reason"), "The Principal was away");

    await press("Send");

    await screen.findByText("Reason for being overdue: The Principal was away");
    expect(tasks.steps[0].body).toEqual({ reason: "The Principal was away" });
  });
});

describe("completing a task", () => {
  const ready = () => {
    const detail = taskDetail({ task: { id: "t-1", title: "Visit the school", dueOn: "2026-10-05", status: "IN_PROGRESS" }, actions: {} as never });
    return { ...detail, actions: actionsOf(detail) };
  };

  it("keeps Send disabled until the note has 10 characters, saying how many more are needed", async () => {
    await open(ready());
    await pressText("Complete");

    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(true);
    expect(screen.getByText("10 more characters needed")).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Note"), "Met them");
    expect(screen.getByText("2 more characters needed")).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Note"), "Met the Principal");
    expect(screen.queryByText(/more character/)).toBeNull();
    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(false);
  });

  it("posts the trimmed note and shows the task Completed, with no photo needed", async () => {
    const { tasks } = await open(ready());
    await pressText("Complete");
    await fireEvent.changeText(screen.getByLabelText("Note"), "  Met the Principal and left the forms  ");

    await press("Send");

    expect(await screen.findByText("Completion note: Met the Principal and left the forms")).toBeTruthy();
    expect(screen.getByText("Completed")).toBeTruthy();
    expect(tasks.steps).toEqual([{ id: "t-1", step: "complete", body: { note: "Met the Principal and left the forms" } }]);
    expect(tasks.evidenceUploads).toHaveLength(0);
  });

  it("completes an overdue task after the reason, and the task then shows Completed", async () => {
    const { tasks } = await open();
    await pressText("Give a reason");
    await fireEvent.changeText(screen.getByLabelText("Reason"), "The Principal was away");
    await press("Send");
    await screen.findByText("Complete");

    await pressText("Complete");
    await fireEvent.changeText(screen.getByLabelText("Note"), "Met the Principal on Monday");
    await press("Send");

    expect(await screen.findByText("Completion note: Met the Principal on Monday")).toBeTruthy();
    expect(tasks.steps.map((s) => s.step)).toEqual(["overdue-reason", "complete"]);
  });
});

describe("what the server may refuse", () => {
  it.each([
    ["the task changed on the web", 409, "The task was changed on the web."],
    ["the task is not the Manager's", 404, "Task not found."],
    ["a rule", 400, "The note must be at least 10 characters."],
  ])("shows the server's reason when %s, keeps the note and reloads the task", async (_name, status, reason) => {
    const detail = taskDetail({ task: { id: "t-1", title: "Visit the school", dueOn: "2026-10-05", status: "IN_PROGRESS" }, actions: {} as never });
    const { tasks, server } = await open({ ...detail, actions: actionsOf(detail) });
    await pressText("Complete");
    await fireEvent.changeText(screen.getByLabelText("Note"), "Met the Principal");
    tasks.refusals.complete = { status, body: { reason } };

    await press("Send");

    expect(await screen.findByText(reason)).toBeTruthy();
    expect(screen.getByLabelText("Note").props.value).toBe("Met the Principal");
    expect(screen.queryByText("Completion note: Met the Principal")).toBeNull();
    if (status !== 400) expect(server.callsTo("GET /api/v1/tasks/{id}").length).toBeGreaterThanOrEqual(2);
  });

  it("says nothing was sent when offline, keeps the entries and shows nothing as done", async () => {
    const detail = taskDetail({ task: { id: "t-1", title: "Visit the school", dueOn: "2026-10-05", status: "IN_PROGRESS" }, actions: {} as never });
    const { server, tasks } = await open({ ...detail, actions: actionsOf(detail) });
    await pressText("Complete");
    await fireEvent.changeText(screen.getByLabelText("Note"), "Met the Principal");
    server.offline = true;

    await press("Send");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(screen.getByLabelText("Note").props.value).toBe("Met the Principal");
    expect(tasks.steps).toHaveLength(0);
  });

  it("sends one request for a double tap", async () => {
    const detail = taskDetail({ task: { id: "t-1", title: "Visit the school", dueOn: "2026-10-05", status: "IN_PROGRESS" }, actions: {} as never });
    const { tasks } = await open({ ...detail, actions: actionsOf(detail) });
    await pressText("Complete");
    await fireEvent.changeText(screen.getByLabelText("Note"), "Met the Principal");
    const send = screen.getByLabelText("Send");

    await fireEvent.press(send);
    await fireEvent.press(send);

    await screen.findByText("Completed");
    expect(tasks.steps.filter((s) => s.step === "complete")).toHaveLength(1);
  });
});
