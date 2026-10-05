import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { CANCEL_REFUSAL_COUNT } from "../../src/leave/leaveMessages";
import { openLeaveScreen } from "../support/leaveApp";
import { leaveRequest } from "../support/leaveFixtures";
import { CANCEL_TEXTS } from "../support/leaveTexts";

afterEach(() => jest.restoreAllMocks());

const LIST = "GET /api/v1/me/leave";
const CANCEL = "POST /api/v1/me/leave/{id}/cancel";

const mixed = () => [
  leaveRequest({ id: "pending", leaveType: "Casual" }),
  // The server still allows cancelling this approved request (its first day is ahead).
  leaveRequest({ id: "approved-ahead", status: "APPROVED", leaveType: "Sick", allowedActions: ["CANCEL"] }),
  // Started: the server offers nothing.
  leaveRequest({ id: "approved-started", status: "APPROVED", leaveType: "Personal", allowedActions: [] }),
  leaveRequest({ id: "rejected", status: "REJECTED", leaveType: "Other" }),
  leaveRequest({ id: "cancelled", status: "CANCELLED", cancelledBy: "TEACHER", leaveType: "Casual" }),
];

describe("My Leave History: cancel (spec 020 FR-006)", () => {
  it("offers Cancel exactly when the server lists it, whatever the dates or the phone's clock", async () => {
    await openLeaveScreen("My Leave History", { phoneOffsetHours: 24 * 100, leave: { requests: mixed() } });
    await screen.findByText("Sick");

    expect(screen.getAllByLabelText("Cancel request")).toHaveLength(2);
    // The two with a button are the pending one and the approved one the server still allows.
    const labels = screen.getAllByLabelText(/^(Casual|Sick|Personal|Other),/).map((n) => n.props.accessibilityLabel as string);
    expect(labels).toHaveLength(5);
  });

  it("asks first, sends nothing until confirmed, then reloads and shows it cancelled", async () => {
    const { server, leave } = await openLeaveScreen("My Leave History", { leave: { requests: [leaveRequest({ id: "pending" })] } });
    await fireEvent.press(await screen.findByLabelText("Cancel request"));

    expect(await screen.findByLabelText("Yes, cancel it")).toBeTruthy();
    expect(server.callsTo(CANCEL)).toHaveLength(0);

    await fireEvent.press(screen.getByLabelText("Yes, cancel it"));

    await waitFor(() => expect(server.callsTo(CANCEL)).toHaveLength(1));
    expect(server.callsTo(CANCEL)[0].params.id).toBe("pending");
    expect(server.callsTo(CANCEL)[0].body).toBeUndefined();
    expect(leave.cancelled).toEqual(["pending"]);
    await waitFor(() => expect(server.callsTo(LIST)).toHaveLength(2));
    expect(await screen.findByLabelText(/, Cancelled/)).toBeTruthy();
    expect(screen.queryByLabelText("Cancel request")).toBeNull();
  });

  it("closes the dialog without sending when the Teacher keeps the request", async () => {
    const { server } = await openLeaveScreen("My Leave History", { leave: { requests: [leaveRequest({ id: "pending" })] } });
    await fireEvent.press(await screen.findByLabelText("Cancel request"));
    await fireEvent.press(await screen.findByLabelText("Keep request"));

    expect(screen.queryByLabelText("Yes, cancel it")).toBeNull();
    expect(server.callsTo(CANCEL)).toHaveLength(0);
    expect(screen.getByLabelText("Cancel request")).toBeTruthy();
  });

  it(`covers all ${CANCEL_REFUSAL_COUNT} cancel refusals`, () => {
    expect(CANCEL_TEXTS).toHaveLength(CANCEL_REFUSAL_COUNT);
  });

  it.each(CANCEL_TEXTS)("shows %s in plain wording and reloads the request as it now is", async (reason) => {
    const { server } = await openLeaveScreen("My Leave History", {
      leave: { requests: [leaveRequest({ id: "pending" })], cancelRefusal: () => ({ status: 409, body: { reason } }) },
    });
    await fireEvent.press(await screen.findByLabelText("Cancel request"));
    await fireEvent.press(await screen.findByLabelText("Yes, cancel it"));

    expect(await screen.findByText(reason)).toBeTruthy();
    await waitFor(() => expect(server.callsTo(LIST).length).toBeGreaterThanOrEqual(2));
  });

  it("explains a stale request in plain words", async () => {
    await openLeaveScreen("My Leave History", {
      leave: {
        requests: [leaveRequest({ id: "pending" })],
        cancelRefusal: () => ({ status: 409, body: { reason: "This record was changed by someone else. Reload and try again." } }),
      },
    });
    await fireEvent.press(await screen.findByLabelText("Cancel request"));
    await fireEvent.press(await screen.findByLabelText("Yes, cancel it"));

    expect(await screen.findByText("This request was changed by someone else. It now shows the latest state.")).toBeTruthy();
  });

  it("with no connection shows nothing as cancelled, and cancels once back online", async () => {
    const { server, leave } = await openLeaveScreen("My Leave History", { leave: { requests: [leaveRequest({ id: "pending" })] } });
    await fireEvent.press(await screen.findByLabelText("Cancel request"));
    await screen.findByLabelText("Yes, cancel it");

    server.offline = true;
    await fireEvent.press(screen.getByLabelText("Yes, cancel it"));
    expect(await screen.findByText("No connection. The request was not cancelled.")).toBeTruthy();
    expect(leave.cancelled).toHaveLength(0);

    server.offline = false;
    await fireEvent.press(screen.getByLabelText("Yes, cancel it"));
    await waitFor(() => expect(leave.cancelled).toEqual(["pending"]));
  });
});
