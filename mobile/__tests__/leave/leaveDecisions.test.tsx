import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { DECISION_REFUSAL_COUNT } from "../../src/leave/leaveMessages";
import { MANAGER_MODEL } from "../support/fixtures";
import { openLeaveScreen } from "../support/leaveApp";
import { leaveRequest, previewDays } from "../support/leaveFixtures";
import { DECISION_TEXTS } from "../support/leaveTexts";

afterEach(() => jest.restoreAllMocks());

const DETAIL = "GET /api/v1/leave/{id}";
const LIST = "GET /api/v1/leave";

const two = () => [
  leaveRequest({ as: "supervisor", id: "r1", teacherName: "Asha Rao", leaveType: "Casual" }),
  leaveRequest({ as: "supervisor", id: "r2", teacherName: "Bala Nair", leaveType: "Sick" }),
];

async function open(over: Record<string, unknown> = {}, requests = two(), filter?: string) {
  const opened = await openLeaveScreen("Leave Management", {
    model: MANAGER_MODEL,
    leave: { requests, detailOf: () => ({ days: previewDays(["2026-10-12"]), problems: [] }), ...over },
  });
  if (filter) await fireEvent.press(await screen.findByLabelText(filter));
  await fireEvent.press(await screen.findByLabelText(/^Asha Rao, Demo School One/));
  await screen.findByText("Days it would mark");
  return opened;
}

describe("Leave decisions (spec 020 FR-009, FR-010)", () => {
  it("approves with a note after a confirmation, then reloads the list and the count", async () => {
    const { server, leave } = await open();
    await fireEvent.press(screen.getByLabelText("Approve"));
    expect(server.callsTo("POST /api/v1/leave/{id}/approve")).toHaveLength(0);

    await fireEvent.changeText(await screen.findByLabelText("Note"), "Enjoy the festival");
    await fireEvent.press(screen.getByLabelText("Confirm approve"));

    await waitFor(() => expect(leave.decisions).toHaveLength(1));
    expect(leave.decisions[0]).toEqual({ action: "approve", id: "r1", body: { note: "Enjoy the festival", version: 0 } });
    // The detail shows the new state and the list behind it was reloaded (count 1 left).
    expect(await screen.findByText(/^Approved by Manoj/)).toBeTruthy();
    await waitFor(() => expect(server.callsTo(LIST).length).toBeGreaterThanOrEqual(2));
    await fireEvent.press(screen.getByLabelText("Back to requests"));
    expect(await screen.findByLabelText("Pending: 1")).toBeTruthy();
    expect(screen.queryByText("Asha Rao")).toBeNull();
  });

  it("approves with no note: the body has the version only", async () => {
    const { leave } = await open();
    await fireEvent.press(screen.getByLabelText("Approve"));
    await fireEvent.press(await screen.findByLabelText("Confirm approve"));

    await waitFor(() => expect(leave.decisions).toHaveLength(1));
    expect(leave.decisions[0].body).toEqual({ version: 0 });
  });

  it("rejects only with a reason, up to 500 characters, and posts {reason, version}", async () => {
    const { leave } = await open();
    await fireEvent.press(screen.getByLabelText("Reject"));

    expect((await screen.findByLabelText("Confirm reject")).props.accessibilityState.disabled).toBe(true);
    await fireEvent.changeText(screen.getByLabelText("Reason"), "x".repeat(600));
    expect(screen.getByLabelText("Reason").props.value).toHaveLength(500);
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Exam week");
    await fireEvent.press(screen.getByLabelText("Confirm reject"));

    await waitFor(() => expect(leave.decisions).toHaveLength(1));
    expect(leave.decisions[0]).toEqual({ action: "reject", id: "r1", body: { reason: "Exam week", version: 0 } });
    expect(await screen.findByText(/^Rejected by Manoj/)).toBeTruthy();
    expect(screen.getByText("Rejection reason: Exam week")).toBeTruthy();
  });

  it("revokes an approved request only with a reason", async () => {
    const approved = [leaveRequest({ as: "supervisor", id: "r1", teacherName: "Asha Rao", status: "APPROVED", decidedByName: "Manoj" })];
    const { leave } = await open({}, approved, "Approved");
    expect(screen.getByLabelText("Revoke")).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Revoke"));

    expect((await screen.findByLabelText("Confirm revoke")).props.accessibilityState.disabled).toBe(true);
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Staff shortage");
    await fireEvent.press(screen.getByLabelText("Confirm revoke"));

    await waitFor(() => expect(leave.decisions).toHaveLength(1));
    expect(leave.decisions[0]).toEqual({ action: "revoke", id: "r1", body: { reason: "Staff shortage", version: 0 } });
  });

  it(`covers all ${DECISION_REFUSAL_COUNT} decision refusals`, () => {
    expect(DECISION_TEXTS).toHaveLength(DECISION_REFUSAL_COUNT);
  });

  it.each(DECISION_TEXTS)("shows %s in plain wording, keeps the dialog and its text, and reloads the request", async (reason) => {
    const { server, leave } = await open({ decisionRefusal: () => ({ status: 409, body: { reason } }) });
    await fireEvent.press(screen.getByLabelText("Approve"));
    await fireEvent.changeText(await screen.findByLabelText("Note"), "Fine by me");
    await fireEvent.press(screen.getByLabelText("Confirm approve"));

    const plain = reason.includes("days set by a supervisor")
      ? "Some of these days were marked by a supervisor (15/01/2026, 16/01/2026). Correct them first, then approve."
      : reason === "month locked: 2026-01"
        ? "Attendance for January 2026 is locked."
        : reason.includes("Leave cannot be removed")
          ? "Leave cannot be removed because attendance for January 2026 is locked."
          : reason.includes("changed by someone else")
            ? "This request was changed by someone else. It now shows the latest state."
            : reason;
    expect(await screen.findByText(plain)).toBeTruthy();
    expect(leave.decisions).toHaveLength(0);
    expect(screen.getByLabelText("Note").props.value).toBe("Fine by me");
    await waitFor(() => expect(server.callsTo(DETAIL).length).toBeGreaterThanOrEqual(2));
  });

  it("detects a request changed since it was opened, shows its new state, and succeeds with the new version", async () => {
    const { leave } = await open();
    // Someone else changed the request after it was opened.
    leave.requests = leave.requests.map((r) => (r.id === "r1" ? { ...r, version: 1 } : r));

    await fireEvent.press(screen.getByLabelText("Approve"));
    await fireEvent.press(await screen.findByLabelText("Confirm approve"));
    expect(await screen.findByText("This request was changed by someone else. It now shows the latest state.")).toBeTruthy();
    expect(leave.decisions).toHaveLength(0);

    // The detail reloaded (version 1); confirming again sends the new version.
    await waitFor(() => expect(screen.getByLabelText("Confirm approve").props.accessibilityState.disabled).toBe(false));
    await fireEvent.press(screen.getByLabelText("Confirm approve"));
    await waitFor(() => expect(leave.decisions).toHaveLength(1));
    expect(leave.decisions[0].body.version).toBe(1);
  });

  it("with no connection shows nothing as decided, and decides once back online", async () => {
    const { server, leave } = await open();
    await fireEvent.press(screen.getByLabelText("Approve"));
    await screen.findByLabelText("Confirm approve");

    server.offline = true;
    await fireEvent.press(screen.getByLabelText("Confirm approve"));
    expect(await screen.findByText("No connection. Nothing was changed.")).toBeTruthy();
    expect(leave.decisions).toHaveLength(0);

    server.offline = false;
    await fireEvent.press(screen.getByLabelText("Confirm approve"));
    await waitFor(() => expect(leave.decisions).toHaveLength(1));
  });

  it("sends a decision once even when Confirm is tapped twice", async () => {
    const { leave } = await open();
    await fireEvent.press(screen.getByLabelText("Approve"));
    const confirm = await screen.findByLabelText("Confirm approve");

    fireEvent.press(confirm);
    fireEvent.press(confirm);

    await waitFor(() => expect(leave.decisions).toHaveLength(1));
  });
});
