import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { DIRECTOR_MODEL, MANAGER_MODEL, TEACHER_MODEL } from "../support/fixtures";
import { openLeaveScreen } from "../support/leaveApp";
import { leaveRequest, previewDays } from "../support/leaveFixtures";
import { pullToRefresh } from "../support/leaveActions";

afterEach(() => jest.restoreAllMocks());

const LIST = "GET /api/v1/leave";

const pending = (n: number) =>
  Array.from({ length: n }, (_, i) =>
    leaveRequest({ as: "supervisor", id: `p${i}`, teacherName: `Teacher ${i}` }),
  );

/** Signs in and stays on Home (the Dashboard entry is the same screen). */
const home = (model = MANAGER_MODEL, requests = pending(3), extra: Record<string, unknown> = {}) =>
  openLeaveScreen("Dashboard", { model, leave: { requests, ...extra } });

describe("Pending leave widget on Home (spec 020 US4)", () => {
  it.each([
    ["Manager", MANAGER_MODEL],
    ["Director", DIRECTOR_MODEL],
  ])("shows the count for a %s, asking the server for a page of one", async (_name, model) => {
    const { server } = await home(model);

    expect(await screen.findByLabelText("Pending leave requests: 3")).toBeTruthy();
    const calls = server.callsTo(LIST);
    expect(calls).toHaveLength(1);
    expect(calls[0].query).toMatchObject({ status: "PENDING", size: "1" });
  });

  it("says there is nothing pending, and still opens Leave Management", async () => {
    await home(MANAGER_MODEL, []);

    await fireEvent.press(await screen.findByLabelText("No pending leave requests"));

    expect(await screen.findByText("No leave requests")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Pending", selected: true })).toBeTruthy();
  });

  it("opens Leave Management on the Pending list when tapped", async () => {
    await home();
    await fireEvent.press(await screen.findByLabelText("Pending leave requests: 3"));

    expect(await screen.findByLabelText("Pending: 3")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Pending", selected: true })).toBeTruthy();
    expect(screen.getByText("Teacher 0")).toBeTruthy();
  });

  it("reloads the count after a decision when Home is shown again", async () => {
    const { server } = await home(MANAGER_MODEL, pending(3), { detailOf: () => ({ days: previewDays(["2026-10-12"]), problems: [] }) });
    await fireEvent.press(await screen.findByLabelText("Pending leave requests: 3"));
    await fireEvent.press(await screen.findByLabelText(/^Teacher 0, Demo School One/));
    await fireEvent.press(await screen.findByLabelText("Approve"));
    await fireEvent.press(await screen.findByLabelText("Confirm approve"));
    await screen.findByText(/^Approved by Manoj/);

    await fireEvent.press(screen.getByLabelText("Open menu"));
    await fireEvent.press(await screen.findByLabelText("Dashboard"));

    expect(await screen.findByLabelText("Pending leave requests: 2")).toBeTruthy();
    expect(server.callsTo(LIST).filter((c) => c.query.size === "1").length).toBeGreaterThanOrEqual(2);
  });

  it("pull-to-refresh reloads the count", async () => {
    const { leave } = await home();
    await screen.findByLabelText("Pending leave requests: 3");

    leave.requests = leave.requests.slice(1);
    await pullToRefresh();

    expect(await screen.findByLabelText("Pending leave requests: 2")).toBeTruthy();
  });

  it("shows no widget and asks for nothing for a user whose menu does not offer Leave Management", async () => {
    const { server } = await home(TEACHER_MODEL);

    expect(await screen.findByText("Welcome, Tara")).toBeTruthy();
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByLabelText(/leave requests/i)).toBeNull();
    expect(server.callsTo(LIST)).toHaveLength(0);
  });

  it("shows only an error line in the widget when the count cannot be loaded, and the rest of Home works", async () => {
    await openLeaveScreen("Dashboard", {
      model: MANAGER_MODEL,
      prepare: (server) => server.on(LIST, () => ({ status: 500, body: {} })),
    });

    expect(await screen.findByLabelText("Could not load pending leave requests")).toBeTruthy();
    expect(screen.getByText("Welcome, Tara")).toBeTruthy();
    expect(screen.queryByLabelText(/Pending leave requests: /)).toBeNull();
  });

  it("leaves no coming-soon card for the section that now has a screen", async () => {
    await home(DIRECTOR_MODEL);
    await screen.findByLabelText("Pending leave requests: 3");

    await waitFor(() => expect(screen.queryByLabelText("OPERATIONS, coming to the app soon")).toBeNull());
    expect(screen.queryByLabelText("MASTER DATA, coming to the app soon")).toBeNull();
  });
});
