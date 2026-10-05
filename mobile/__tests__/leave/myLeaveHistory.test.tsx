import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { openLeaveScreen } from "../support/leaveApp";
import { leaveRequest } from "../support/leaveFixtures";
import { fillDraft, pullToRefresh } from "../support/leaveActions";

afterEach(() => jest.restoreAllMocks());

const LIST = "GET /api/v1/me/leave";

const requests = () => [
  leaveRequest({ id: "pending", firstDate: "2026-10-20", lastDate: "2026-10-22", leaveType: "Casual" }),
  leaveRequest({
    id: "approved",
    status: "APPROVED",
    leaveType: "Sick",
    firstDate: "2026-09-01",
    lastDate: "2026-09-02",
    workingDays: 2,
    decidedByName: "Manoj",
    decidedAt: "2026-08-30T05:00:00Z",
    decisionNote: "Get well soon",
  }),
  leaveRequest({
    id: "rejected",
    status: "REJECTED",
    leaveType: "Personal",
    firstDate: "2026-08-10",
    lastDate: "2026-08-10",
    workingDays: 1,
    decidedByName: "Manoj",
    decidedAt: "2026-08-08T05:00:00Z",
    decisionNote: "Exam week",
  }),
  // Another Teacher's request: the server never returns it to this Teacher.
  leaveRequest({ id: "other", teacherId: "t-9", teacherName: "Other", leaveType: "Other" }),
];

describe("My Leave History (spec 020 US2)", () => {
  it("lists only the server's requests newest first with type, dates, working days, status and decision", async () => {
    const { server } = await openLeaveScreen("My Leave History", { leave: { requests: requests() } });

    expect(await screen.findByText("20/10/2026 – 22/10/2026")).toBeTruthy();
    expect(screen.getByText("01/09/2026 – 02/09/2026")).toBeTruthy();
    expect(screen.getByText("10/08/2026 – 10/08/2026")).toBeTruthy();
    // Each card's spoken label carries its status (the chips above share the words).
    expect(screen.getByLabelText(/^Casual, 20\/10\/2026 – 22\/10\/2026, 3 working days, Pending/)).toBeTruthy();
    expect(screen.getByLabelText(/^Sick, 01\/09\/2026 – 02\/09\/2026, 2 working days, Approved/)).toBeTruthy();
    expect(screen.getByLabelText(/^Personal, 10\/08\/2026 – 10\/08\/2026, 1 working days, Rejected/)).toBeTruthy();
    expect(screen.getByText("2 working days")).toBeTruthy();
    expect(screen.getByText(/^Approved by Manoj on 30\/08\/2026/)).toBeTruthy();
    expect(screen.queryByText("Other")).toBeNull();
    // Order is the server's: pending first, then approved, then rejected.
    const dates = screen.getAllByText(/^\d{2}\/\d{2}\/\d{4} – /).map((n) => n.props.children);
    expect(dates).toEqual(["20/10/2026 – 22/10/2026", "01/09/2026 – 02/09/2026", "10/08/2026 – 10/08/2026"]);

    const calls = server.callsTo(LIST);
    expect(calls).toHaveLength(1);
    expect(calls[0].query.status).toBeUndefined();
    expect(calls[0].query).toMatchObject({ page: "0", size: "25" });
  });

  it("shows the supervisor's reason for a rejected request after a tap", async () => {
    await openLeaveScreen("My Leave History", { leave: { requests: requests() } });
    await screen.findByText("10/08/2026 – 10/08/2026");
    expect(screen.queryByText("Rejection reason: Exam week")).toBeNull();

    await fireEvent.press(screen.getByLabelText(/^Personal, 10\/08\/2026 – 10\/08\/2026/));

    expect(await screen.findByText("Rejection reason: Exam week")).toBeTruthy();
    expect(screen.getByText("Reason: Family function")).toBeTruthy();
  });

  it("filters by status with upper-case values, and All shows every request again", async () => {
    const { server } = await openLeaveScreen("My Leave History", { leave: { requests: requests() } });
    await screen.findByText("20/10/2026 – 22/10/2026");

    await fireEvent.press(screen.getByLabelText("Approved"));
    await waitFor(() => expect(screen.queryByText("20/10/2026 – 22/10/2026")).toBeNull());
    expect(screen.getByText("01/09/2026 – 02/09/2026")).toBeTruthy();
    expect(server.callsTo(LIST)[1].query.status).toBe("APPROVED");

    await fireEvent.press(screen.getByLabelText("All"));
    expect(await screen.findByText("20/10/2026 – 22/10/2026")).toBeTruthy();
    expect(server.callsTo(LIST)[2].query.status).toBeUndefined();
  });

  it("loads the next page of 25 on Load more", async () => {
    const many = Array.from({ length: 30 }, (_, i) =>
      leaveRequest({ id: `m${i}`, leaveType: `Type ${String(i).padStart(2, "0")}`, status: "REJECTED" }),
    );
    const { server } = await openLeaveScreen("My Leave History", { leave: { requests: many } });

    await screen.findByText("Type 00");
    expect(screen.getByText("Type 24")).toBeTruthy();
    expect(screen.queryByText("Type 25")).toBeNull();

    await fireEvent.press(screen.getByLabelText("Load more"));
    expect(await screen.findByText("Type 29")).toBeTruthy();
    expect(server.callsTo(LIST)[1].query).toMatchObject({ page: "1", size: "25" });
    expect(screen.queryByLabelText("Load more")).toBeNull();
  });

  it("shows the notice after a submit and the new request on top, loaded from the server", async () => {
    const { server } = await openLeaveScreen("Apply Leave", { leave: { requests: requests() } });
    await fillDraft({ type: "Personal", first: "Monday 12 October 2026", last: "Wednesday 14 October 2026", reason: "Wedding" });
    await fireEvent.press(screen.getByLabelText("Check"));
    await screen.findByText(/^Working days:/);
    await fireEvent.press(screen.getByLabelText("Submit"));

    expect(await screen.findByText("Request submitted")).toBeTruthy();
    expect(await screen.findByText("12/10/2026 – 14/10/2026")).toBeTruthy();
    const dates = screen.getAllByText(/^\d{2}\/\d{2}\/\d{4} – /).map((n) => n.props.children);
    expect(dates[0]).toBe("12/10/2026 – 14/10/2026");
    expect(server.callsTo(LIST)).toHaveLength(1);
  });

  it("says No leave requests when there are none", async () => {
    await openLeaveScreen("My Leave History");

    expect(await screen.findByText("No leave requests")).toBeTruthy();
  });

  it("shows an error with Retry for a failed load, never old rows, and recovers", async () => {
    let failing = false;
    await openLeaveScreen("My Leave History", {
      leave: { requests: requests() },
      prepare: (server) => {
        const real = { status: 200, body: { content: [], page: 0, size: 25, totalElements: 0 } };
        server.on(LIST, (request) => (failing ? { status: 500, body: {} } : request.query.status ? real : { status: 200, body: { content: requests().slice(0, 1), page: 0, size: 25, totalElements: 1 } }));
      },
    });
    await screen.findByText("20/10/2026 – 22/10/2026");

    failing = true;
    await fireEvent.press(screen.getByLabelText("Approved"));
    expect(await screen.findByText("Something went wrong")).toBeTruthy();
    expect(screen.queryByText("20/10/2026 – 22/10/2026")).toBeNull();

    failing = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByText("No leave requests")).toBeTruthy();
  });

  it("pull-to-refresh sends a new request", async () => {
    const { server } = await openLeaveScreen("My Leave History", { leave: { requests: requests() } });
    await screen.findByText("20/10/2026 – 22/10/2026");

    await pullToRefresh();

    await waitFor(() => expect(server.callsTo(LIST)).toHaveLength(2));
  });
});
