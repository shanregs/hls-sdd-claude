import { fireEvent, screen } from "@testing-library/react-native";
import { pullToRefresh } from "../support/leaveActions";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { actionsOf } from "../support/tasksServer";
import { directorVisitTaskModel, taskDetail } from "../support/visitTaskFixtures";

/** Spec 034 T024 (US3): the task views, their counts and their rows, as the server returns them. */
const MENU = "My Tasks";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

const task = (id: string, title: string, task: Parameters<typeof taskDetail>[0] extends infer T ? (T extends { task?: infer R } ? R : never) : never) =>
  taskDetail({ task: { id, title, ...task } });

const SET = [
  task("t-today", "Submit this month's content", { dueOn: "2026-10-05", type: "CONTENT_SUBMISSION", typeLabel: "Content preparation and submission" }),
  task("t-soon", "Call the Principal", { dueOn: "2026-10-09", type: "FOLLOW_UP", typeLabel: "Follow-up" }),
  task("t-late", "Visit the school and meet the Principal", { dueOn: "2026-10-01", overdue: true, daysOverdue: 4 }),
  task("t-esc", "Collect feedback", { dueOn: "2026-09-28", overdue: true, daysOverdue: 7, escalated: true }),
  task("t-wait", "Train the new Teacher", { dueOn: "2026-10-03", status: "COMPLETED" }),
  task("t-done", "Observe a class", { dueOn: "2026-09-20", status: "VERIFIED" }),
];

describe("the views", () => {
  it("opens on Today and asks the server for the first page of 25", async () => {
    const { server } = await openVisitTaskScreen(MENU, { tasks: { details: SET } });

    expect(await screen.findByLabelText("Submit this month's content, due 05/10/2026, assigned")).toBeTruthy();
    expect(server.callsTo("GET /api/v1/tasks")[0].query).toEqual({ view: "today", page: "0", size: "25" });
    expect(screen.queryByText("Call the Principal")).toBeNull();
  });

  it("shows the server's counts on the view chips", async () => {
    await openVisitTaskScreen(MENU, { tasks: { details: SET } });

    expect(await screen.findByLabelText("Today (1)")).toBeTruthy();
    expect(screen.getByLabelText("Upcoming (1)")).toBeTruthy();
    expect(screen.getByLabelText("Overdue (2)")).toBeTruthy();
    expect(screen.getByLabelText("Awaiting verification (1)")).toBeTruthy();
    expect(screen.getByLabelText("Done (1)")).toBeTruthy();
    expect(screen.getByLabelText("All")).toBeTruthy();
  });

  it.each([
    ["Upcoming (1)", ["Call the Principal"], ["Submit this month's content"]],
    ["Overdue (2)", ["Visit the school and meet the Principal", "Collect feedback"], ["Call the Principal"]],
    ["Awaiting verification (1)", ["Train the new Teacher"], ["Collect feedback"]],
    ["Done (1)", ["Observe a class"], ["Train the new Teacher"]],
  ])("%s shows only that view", async (chip, shown, hidden) => {
    await openVisitTaskScreen(MENU, { tasks: { details: SET } });
    await screen.findByLabelText("Today (1)");

    await press(chip);

    for (const title of shown) expect(await screen.findByText(title)).toBeTruthy();
    for (const title of hidden) expect(screen.queryByText(title)).toBeNull();
  });

  it("shows overdue days and the escalated mark only as the server returns them", async () => {
    await openVisitTaskScreen(MENU, { tasks: { details: SET } });
    await press("Overdue (2)");

    expect(await screen.findByLabelText("Visit the school and meet the Principal, due 01/10/2026, assigned, 4 days overdue")).toBeTruthy();
    expect(screen.getByLabelText("Collect feedback, due 28/09/2026, assigned, 7 days overdue, escalated")).toBeTruthy();
    await press("Upcoming (1)");
    expect(await screen.findByLabelText("Call the Principal, due 09/10/2026, assigned")).toBeTruthy();
    expect(screen.queryByText("Escalated")).toBeNull();
  });

  it("says when a view is empty", async () => {
    await openVisitTaskScreen(MENU, { tasks: { details: [] } });

    expect(await screen.findByText("Nothing here.")).toBeTruthy();
  });

  it("opens on the view a Home card asks for", async () => {
    const { server } = await openVisitTaskScreen(MENU, { tasks: { details: SET } });
    expect(server.callsTo("GET /api/v1/tasks")[0].query.view).toBe("today");
  });

  it("keeps the chosen view while a task is opened and closed", async () => {
    await openVisitTaskScreen(MENU, { tasks: { details: SET } });
    await press("Overdue (2)");
    await press("Collect feedback, due 28/09/2026, assigned, 7 days overdue, escalated");

    await screen.findByText("History");
    await press("Back to the tasks");

    expect(await screen.findByText("Collect feedback")).toBeTruthy();
    expect(screen.queryByText("Call the Principal")).toBeNull();
  });
});

describe("paging", () => {
  const many = Array.from({ length: 30 }, (_, i) => task(`t-${i}`, `Task number ${i + 1}`, { dueOn: "2026-10-05" }));

  it("shows 25 and loads the next page on Load more, then stops offering it", async () => {
    const { server } = await openVisitTaskScreen(MENU, { tasks: { details: many } });
    await screen.findByText("Task number 1");
    expect(screen.queryByText("Task number 26")).toBeNull();

    await pressText("Load more");

    expect(await screen.findByText("Task number 30")).toBeTruthy();
    expect(screen.queryByText("Load more")).toBeNull();
    expect(server.callsTo("GET /api/v1/tasks").map((c) => c.query.page)).toEqual(expect.arrayContaining(["0", "1"]));
  });

  it("keeps the earlier pages and says so when a later page cannot be loaded", async () => {
    const { tasks } = await openVisitTaskScreen(MENU, { tasks: { details: many } });
    await screen.findByText("Task number 1");
    tasks.refusals.list = { status: 500, body: { reason: "More tasks could not be loaded today." } };

    await pressText("Load more");

    expect(await screen.findByText("More tasks could not be loaded today.")).toBeTruthy();
    expect(screen.getByText("Task number 25")).toBeTruthy();
  });
});

describe("when the tasks cannot be loaded", () => {
  it("shows the server's reason with Retry and no stale rows", async () => {
    const { tasks } = await openVisitTaskScreen(MENU, { tasks: { details: SET } });
    await screen.findByText("Submit this month's content");
    tasks.refusals.list = { status: 500, body: { reason: "The tasks could not be loaded." } };

    await press("Upcoming (1)");

    expect(await screen.findByText("The tasks could not be loaded.")).toBeTruthy();
    expect(screen.getByLabelText("Retry")).toBeTruthy();
    expect(screen.queryByText("Call the Principal")).toBeNull();
  });

  it("says there is no connection", async () => {
    await openVisitTaskScreen(MENU, {
      prepare: (server) =>
        server.on("GET /api/v1/tasks", () => {
          throw new TypeError("Network request failed");
        }),
    });

    expect(await screen.findByText("No connection")).toBeTruthy();
  });

  it("reloads the list and the counts from the server on a pull down", async () => {
    const { tasks } = await openVisitTaskScreen(MENU, { tasks: { details: [SET[0]] } });
    await screen.findByText("Submit this month's content");
    tasks.details = [SET[0], task("t-new", "Added on the web", { dueOn: "2026-10-05" })];

    await pullToRefresh();

    expect(await screen.findByText("Added on the web")).toBeTruthy();
    expect(await screen.findByLabelText("Today (2)")).toBeTruthy();
  });
});

describe("a Director's view", () => {
  it("lists other Managers' tasks with no step button, because every flag the server sends is false", async () => {
    const others = taskDetail({
      task: { id: "t-other", title: "Another Manager's task", assigneeUserId: "u-9", assigneeName: "Other Manager", dueOn: "2026-10-05" },
      actions: { ...actionsOf(taskDetail({ task: { assigneeUserId: "u-9" } })) },
    });
    await openVisitTaskScreen("Manager Tasks", { model: directorVisitTaskModel(), tasks: { details: [others] } });

    await press("Another Manager's task, due 05/10/2026, assigned");

    expect(await screen.findByText("History")).toBeTruthy();
    for (const label of ["Acknowledge", "Start", "Give a reason", "Complete", "Verify", "Send back", "Cancel", "Edit"]) {
      expect(screen.queryByText(label)).toBeNull();
    }
    expect(screen.queryByText("Upload photos")).toBeNull();
  });
});
