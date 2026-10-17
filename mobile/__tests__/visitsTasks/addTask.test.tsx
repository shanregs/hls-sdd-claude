import { fireEvent, screen } from "@testing-library/react-native";
import { chooseDate } from "../support/leaveActions";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { directorVisitTaskModel, ME, visitTaskModel } from "../support/visitTaskFixtures";

/** Spec 034 T045 (US7): adding a task for oneself, offered only when the server lists the caller as an assignee. */
const MENU = "My Tasks";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));

describe("who is offered Add a task for me", () => {
  it("is offered to a Manager whose menu grants Create and whom the server lists as an assignee", async () => {
    await openVisitTaskScreen(MENU, { tasks: { details: [] } });

    expect(await screen.findByLabelText("Add a task for me")).toBeTruthy();
  });

  it("is hidden, and no assignee request is made, without the Create grant", async () => {
    const { server } = await openVisitTaskScreen(MENU, {
      model: visitTaskModel({ tasks: { create: false } }),
      tasks: { details: [] },
    });

    await screen.findByText("Nothing here.");
    expect(screen.queryByLabelText("Add a task for me")).toBeNull();
    expect(server.callsTo("GET /api/v1/tasks/assignees")).toHaveLength(0);
  });

  it("is hidden from a user the server lists with other people but not themselves, like a Director", async () => {
    await openVisitTaskScreen("Manager Tasks", {
      model: directorVisitTaskModel(),
      tasks: { details: [], assignees: [{ userId: "u-7", name: "Some Manager" }, { userId: "u-8", name: "Another Manager" }] },
    });

    await screen.findByText("Nothing here.");
    expect(screen.queryByLabelText("Add a task for me")).toBeNull();
  });
});

describe("the form", () => {
  async function openForm(setup: Parameters<typeof openVisitTaskScreen>[1] = {}) {
    const app = await openVisitTaskScreen(MENU, { tasks: { details: [] }, ...setup });
    await press("Add a task for me");
    await screen.findByLabelText("Title");
    return app;
  }

  it("lists the server's task types and limits the title to 120 characters", async () => {
    await openForm();

    for (const label of ["School visit", "Follow-up", "Principal feedback", "Substitute arrangement"]) {
      expect(await screen.findByLabelText(label)).toBeTruthy();
    }
    expect(screen.getByLabelText("Title").props.maxLength).toBe(120);
    expect(screen.getByText(/At least 3 are needed/)).toBeTruthy();
  });

  it("keeps Send disabled until a type, a title of at least 3 characters and a due date are given", async () => {
    await openForm();
    const disabled = () => screen.getByLabelText("Send").props.accessibilityState?.disabled;
    expect(disabled()).toBe(true);

    await press("Follow-up");
    await fireEvent.changeText(screen.getByLabelText("Title"), "Ca");
    await chooseDate("Due date" as never, "not chosen", "Tuesday 6 October 2026");
    expect(disabled()).toBe(true);
    await fireEvent.changeText(screen.getByLabelText("Title"), "Call the Principal");
    expect(disabled()).toBe(false);
  });

  it("posts the task assigned to the caller alone, then opens it and lists it", async () => {
    const { tasks } = await openForm();
    await press("Follow-up");
    await fireEvent.changeText(screen.getByLabelText("Title"), "  Call the Principal  ");
    await fireEvent.changeText(screen.getByLabelText("Description"), "About the new timetable");
    await fireEvent.changeText(screen.getByLabelText("Place"), "Anna Nagar");
    await chooseDate("Due date" as never, "not chosen", "Tuesday 6 October 2026");

    await press("Send");

    expect(await screen.findByText("Call the Principal")).toBeTruthy();
    expect(screen.getByText("Follow-up, due 06/10/2026")).toBeTruthy();
    expect(tasks.created).toEqual([
      {
        type: "FOLLOW_UP",
        title: "Call the Principal",
        description: "About the new timetable",
        assigneeUserId: ME.userId,
        dueOn: "2026-10-06",
        place: "Anna Nagar",
      },
    ]);
    expect(screen.getByText("Assigned by Manoj Manager")).toBeTruthy();
    await press("Back to the tasks");
    await press("Upcoming (1)");
    expect(await screen.findByText("Call the Principal")).toBeTruthy();
  });

  it("leaves out the description and place when none are given", async () => {
    const { tasks } = await openForm();
    await press("Training");
    await fireEvent.changeText(screen.getByLabelText("Title"), "Run the training");
    await chooseDate("Due date" as never, "not chosen", "Tuesday 6 October 2026");

    await press("Send");

    await screen.findByText("Run the training");
    expect(tasks.created[0]).toEqual({ type: "TRAINING", title: "Run the training", assigneeUserId: ME.userId, dueOn: "2026-10-06" });
  });

  it.each([
    ["a due date in the past", 400, "The due date cannot be in the past."],
    ["a rule", 400, "The title must be 3 to 120 characters."],
  ])("shows the server's reason for %s and keeps every entry", async (_name, status, reason) => {
    const { tasks } = await openForm();
    tasks.refusals.create = { status, body: { reason } };
    await press("Follow-up");
    await fireEvent.changeText(screen.getByLabelText("Title"), "Call the Principal");
    await chooseDate("Due date" as never, "not chosen", "Tuesday 6 October 2026");

    await press("Send");

    expect(await screen.findByText(reason)).toBeTruthy();
    expect(tasks.details).toHaveLength(0);
    expect(screen.getByLabelText("Title").props.value).toBe("Call the Principal");
    expect(screen.getByLabelText("Due date, Tuesday 6 October 2026")).toBeTruthy();
  });

  it("says nothing was sent when offline and keeps the entries", async () => {
    const { server, tasks } = await openForm();
    await press("Follow-up");
    await fireEvent.changeText(screen.getByLabelText("Title"), "Call the Principal");
    await chooseDate("Due date" as never, "not chosen", "Tuesday 6 October 2026");
    server.offline = true;

    await press("Send");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(tasks.created).toHaveLength(0);
    expect(screen.getByLabelText("Title").props.value).toBe("Call the Principal");
  });
});
