import { fireEvent, screen } from "@testing-library/react-native";
import { openAttendanceScreen } from "../support/attendanceApp";
import { pullToRefresh } from "../support/leaveActions";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { taskDetail, visit } from "../support/visitTaskFixtures";

/** Spec 034 T039 (US5): today's visits and the task counts on Home, from the server. */
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));

const VISITS = [
  visit({ id: "a", date: "2026-10-05", prospect: { id: "p-1", name: "First School" } }),
  visit({ id: "b", date: "2026-10-05", status: "COMPLETED", effectiveStatus: "COMPLETED", outcome: "Met", prospect: { id: "p-2", name: "Second School" } }),
  visit({ id: "c", date: "2026-10-06", prospect: { id: "p-3", name: "Tomorrow School" } }),
];
const task = (id: string, dueOn: string, overdue = false) => taskDetail({ task: { id, title: `Task ${id}`, dueOn, overdue, daysOverdue: overdue ? 3 : 0 } });
const TASKS = [task("t-today", "2026-10-05"), task("t-late-1", "2026-10-01", true), task("t-late-2", "2026-09-30", true), task("t-soon", "2026-10-09")];

describe("the cards", () => {
  it("shows how many visits are planned today and how many tasks are overdue and due today, from the server", async () => {
    const { server } = await openVisitTaskScreen(null, { marketing: { visits: VISITS }, tasks: { details: TASKS } });

    expect(await screen.findByLabelText("1 visit today")).toBeTruthy();
    expect(await screen.findByLabelText("2 overdue, 1 due today")).toBeTruthy();
    const call = server.callsTo("GET /api/v1/marketing/activities")[0];
    expect(call.query).toEqual({ from: "2026-10-05", to: "2026-10-05", mine: "true" });
    expect(server.callsTo("GET /api/v1/tasks/summary")).toHaveLength(1);
  });

  it("counts only visits the server still calls planned", async () => {
    await openVisitTaskScreen(null, {
      marketing: { visits: [VISITS[1], visit({ id: "x", date: "2026-10-05", status: "CANCELLED", effectiveStatus: "CANCELLED" })] },
      tasks: { details: [] },
    });

    expect(await screen.findByLabelText("0 visits today")).toBeTruthy();
    expect(await screen.findByLabelText("0 overdue, 0 due today")).toBeTruthy();
  });

  it("opens the visits screen from the visits card", async () => {
    await openVisitTaskScreen(null, { marketing: { visits: VISITS }, tasks: { details: TASKS } });

    await press("1 visit today");

    expect(await screen.findByLabelText("Visit with First School, 05/10/2026, planned")).toBeTruthy();
    expect(screen.getByText("Today")).toBeTruthy();
  });

  it("opens the tasks screen on Overdue from the tasks card", async () => {
    const { server } = await openVisitTaskScreen(null, { marketing: { visits: VISITS }, tasks: { details: TASKS } });

    await press("2 overdue, 1 due today");

    expect(await screen.findByLabelText(/Task t-late-1, due 01\/10\/2026, assigned, 3 days overdue/)).toBeTruthy();
    expect(screen.queryByText("Task t-today")).toBeNull();
    const views = server.callsTo("GET /api/v1/tasks").map((c) => c.query.view);
    expect(views).toEqual(["overdue"]);
  });

  it("does not show the 'coming soon' cards for sections that now have a screen", async () => {
    await openVisitTaskScreen(null, { marketing: { visits: [] }, tasks: { details: [] } });

    await screen.findByLabelText("0 visits today");
    expect(screen.queryByLabelText("OPERATIONS, coming to the app soon")).toBeNull();
    expect(screen.queryByLabelText("MARKETING, coming to the app soon")).toBeNull();
  });
});

describe("when a count cannot be loaded", () => {
  it("says so on that card alone, with Try again, and loads it when tried again", async () => {
    const { marketing } = await openVisitTaskScreen(null, {
      marketing: { visits: VISITS, refusals: { list: { status: 500, body: { reason: "Could not load visits." } } } },
      tasks: { details: TASKS },
    });

    expect(await screen.findByLabelText("Visits, could not be loaded")).toBeTruthy();
    expect(await screen.findByLabelText("2 overdue, 1 due today")).toBeTruthy();
    marketing.refusals = {};

    await press("Try again for visits");

    expect(await screen.findByLabelText("1 visit today")).toBeTruthy();
  });

  it("says so for tasks while the visits card still shows", async () => {
    await openVisitTaskScreen(null, {
      marketing: { visits: VISITS },
      tasks: { details: TASKS, refusals: { summary: { status: 500, body: { reason: "No summary." } } } },
    });

    expect(await screen.findByLabelText("Tasks, could not be loaded")).toBeTruthy();
    expect(await screen.findByLabelText("1 visit today")).toBeTruthy();
  });
});

describe("who sees the cards", () => {
  it("shows neither to a user whose menu offers neither screen, and asks for neither count", async () => {
    const { server } = await openAttendanceScreen(null);

    await screen.findByText("Welcome, Tara");
    expect(screen.queryByLabelText(/visit today|visits today/)).toBeNull();
    expect(screen.queryByLabelText(/overdue, /)).toBeNull();
    expect(server.callsTo("GET /api/v1/marketing/activities")).toHaveLength(0);
    expect(server.callsTo("GET /api/v1/tasks/summary")).toHaveLength(0);
  });
});

describe("refreshing Home", () => {
  it("reloads both cards on a pull down", async () => {
    const { marketing, tasks } = await openVisitTaskScreen(null, { marketing: { visits: [VISITS[0]] }, tasks: { details: [TASKS[0]] } });
    await screen.findByLabelText("1 visit today");
    marketing.visits = [VISITS[0], visit({ id: "n", date: "2026-10-05", prospect: { id: "p-9", name: "New School" } })];
    tasks.details = [...TASKS];

    await pullToRefresh();

    expect(await screen.findByLabelText("2 visits today")).toBeTruthy();
    expect(await screen.findByLabelText("2 overdue, 1 due today")).toBeTruthy();
  });
});
