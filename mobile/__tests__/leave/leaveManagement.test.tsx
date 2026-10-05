import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { MANAGER_MODEL, DIRECTOR_MODEL } from "../support/fixtures";
import { openLeaveScreen } from "../support/leaveApp";
import { leaveRequest, previewDays } from "../support/leaveFixtures";
import { captureHardwareBack, pullToRefresh } from "../support/leaveActions";

afterEach(() => jest.restoreAllMocks());

const LIST = "GET /api/v1/leave";

/** The list screen's own calls: Home also asks for a page of one to read the Pending count. */
const listCalls = (server: { callsTo: (route: string) => { query: Record<string, string> }[] }) =>
  server.callsTo(LIST).filter((c) => c.query.size !== "1");

const sup = (over: Parameters<typeof leaveRequest>[0]) => leaveRequest({ as: "supervisor", ...over });

const requests = () => [
  sup({ id: "asha", teacherId: "t-1", teacherName: "Asha Rao", leaveType: "Casual", firstDate: "2026-10-12", lastDate: "2026-10-14" }),
  sup({ id: "bala", teacherId: "t-2", teacherName: "Bala Nair", schoolName: "Demo School Two", leaveType: "Sick", firstDate: "2026-10-20", lastDate: "2026-10-20", workingDays: 1 }),
  // Another Manager's Teacher: outside this Manager's scope.
  sup({ id: "chitra", teacherId: "t-9", teacherName: "Chitra Menon", leaveType: "Personal" }),
  sup({ id: "done", teacherId: "t-1", teacherName: "Asha Rao", status: "APPROVED", leaveType: "Other", decidedByName: "Manoj", firstDate: "2026-09-01", lastDate: "2026-09-01", workingDays: 1 }),
];

const manager = (over = {}) =>
  openLeaveScreen("Leave Management", {
    model: MANAGER_MODEL,
    leave: { requests: requests(), inScope: (r) => r.teacherId !== "t-9", ...over },
  });

describe("Leave Management (spec 020 US3)", () => {
  it("opens on Pending, with the count, only the Teachers the server returned, and no other Teacher", async () => {
    const { server } = await manager();

    expect(await screen.findByLabelText("Pending: 2")).toBeTruthy();
    expect(screen.getByText("Asha Rao")).toBeTruthy();
    expect(screen.getByText("Bala Nair")).toBeTruthy();
    expect(screen.queryByText("Chitra Menon")).toBeNull();
    // The approved request is not Pending, so it is not in the default list.
    expect(screen.queryByText("Other")).toBeNull();
    expect(listCalls(server)[0].query).toMatchObject({ status: "PENDING", page: "0", size: "25" });
  });

  it("shows Teacher, School, type, dates, working days and status on a row, with no action on it", async () => {
    await manager();
    await screen.findByText("Bala Nair");

    expect(screen.getByText("Demo School Two · Sick")).toBeTruthy();
    expect(screen.getByText("20/10/2026 – 20/10/2026")).toBeTruthy();
    expect(screen.getByLabelText(/^Bala Nair, Demo School Two, Sick, 20\/10\/2026 – 20\/10\/2026, 1 working days, Pending/)).toBeTruthy();
    for (const label of ["Approve", "Reject", "Revoke"]) expect(screen.queryByLabelText(label)).toBeNull();
  });

  it("offers the four statuses and no All, and filters with upper-case values", async () => {
    const { server } = await manager();
    await screen.findByText("Asha Rao");

    expect(screen.queryByLabelText("All")).toBeNull();
    for (const label of ["Pending", "Approved", "Rejected", "Cancelled"]) expect(screen.getByLabelText(label)).toBeTruthy();

    await fireEvent.press(screen.getByLabelText("Approved"));
    expect(await screen.findByText("Demo School One · Other")).toBeTruthy();
    expect(screen.queryByText("Bala Nair")).toBeNull();
    expect(listCalls(server)[1].query.status).toBe("APPROVED");
  });

  it("opens the detail when a row is tapped", async () => {
    await manager({ detailOf: () => ({ days: previewDays(["2026-10-12", "2026-10-13"]), problems: [] }) });
    await fireEvent.press(await screen.findByLabelText(/^Asha Rao, Demo School One, Casual/));

    expect(await screen.findByText("Days it would mark")).toBeTruthy();
    expect(screen.getByLabelText("Back to requests")).toBeTruthy();
  });

  it("loads the next page of 25 on Load more", async () => {
    const many = Array.from({ length: 30 }, (_, i) => sup({ id: `m${i}`, teacherName: `Teacher ${String(i).padStart(2, "0")}` }));
    const { server } = await manager({ requests: many, inScope: () => true });

    await screen.findByText("Teacher 00");
    expect(screen.queryByText("Teacher 25")).toBeNull();
    await fireEvent.press(screen.getByLabelText("Load more"));
    expect(await screen.findByText("Teacher 29")).toBeTruthy();
    expect(listCalls(server)[1].query).toMatchObject({ page: "1", size: "25" });
  });

  it("a Director sees every Manager's requests, as the server returns them", async () => {
    await openLeaveScreen("Leave Management", {
      model: DIRECTOR_MODEL,
      leave: { requests: requests(), inScope: () => true },
    });

    expect(await screen.findByText("Chitra Menon")).toBeTruthy();
    expect(screen.getByLabelText("Pending: 3")).toBeTruthy();
  });

  it("shows Not found and no data about the Teacher when the request is out of scope", async () => {
    const { leave } = await manager();
    const row = await screen.findByLabelText(/^Asha Rao, Demo School One, Casual/);

    // The scope changed after the list was loaded: the server now answers 404 for this request.
    leave.inScope = () => false;
    await fireEvent.press(row);

    expect(await screen.findByText("Not found")).toBeTruthy();
    expect(screen.queryByText("Asha Rao")).toBeNull();
    expect(screen.queryByText("Days it would mark")).toBeNull();
    for (const label of ["Approve", "Reject", "Revoke"]) expect(screen.queryByLabelText(label)).toBeNull();
  });

  it("says No leave requests when there are none, and shows Retry for a failed load", async () => {
    let failing = false;
    await openLeaveScreen("Leave Management", {
      model: MANAGER_MODEL,
      prepare: (server) =>
        server.on(LIST, () =>
          failing
            ? { status: 500, body: {} }
            : { status: 200, body: { content: [], page: 0, size: 25, totalElements: 0, pendingCount: 0 } },
        ),
    });
    expect(await screen.findByText("No leave requests")).toBeTruthy();

    failing = true;
    await fireEvent.press(screen.getByLabelText("Approved"));
    expect(await screen.findByText("Something went wrong")).toBeTruthy();
    failing = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByText("No leave requests")).toBeTruthy();
  });

  it("pull-to-refresh reloads the list and the count", async () => {
    const { server, leave } = await manager();
    await screen.findByLabelText("Pending: 2");

    leave.requests = leave.requests.filter((r) => r.id !== "bala");
    await pullToRefresh();

    await waitFor(() => expect(listCalls(server)).toHaveLength(2));
    expect(await screen.findByLabelText("Pending: 1")).toBeTruthy();
  });

  it("Android back from the detail returns to the list with its filter kept", async () => {
    const pressBack = captureHardwareBack();
    await manager();
    await fireEvent.press(await screen.findByLabelText("Approved"));
    await fireEvent.press(await screen.findByLabelText(/^Asha Rao, Demo School One, Other/));
    await screen.findByText("Days it would mark");

    expect(await pressBack()).toBe(true);

    expect(await screen.findByText("Demo School One · Other")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Approved", selected: true })).toBeTruthy();
    expect(screen.queryByText("Days it would mark")).toBeNull();
  });
});
