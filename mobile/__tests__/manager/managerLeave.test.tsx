import { fireEvent, screen } from "@testing-library/react-native";
import { chooseDate, pullToRefresh } from "../support/leaveActions";
import { managerFieldModel, managerLeave } from "../support/managerFixtures";
import { openManagerScreen } from "../support/managerApp";

/** Spec 033 T036 to T038 (US5): the Manager asks for leave and follows the decision. */
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

/** The confirmation's own button; the list's Cancel request button has the same words. */
async function confirmCancel() {
  const buttons = await screen.findAllByText("Cancel request");
  await fireEvent.press(buttons[buttons.length - 1]);
}

async function fill({ type = "Casual Leave", reason = "Family function" } = {}) {
  await press(type);
  await chooseDate("First date", "not chosen", "Monday 12 October 2026");
  await chooseDate("Last date", "not chosen", "Wednesday 14 October 2026");
  await fireEvent.changeText(screen.getByLabelText("Reason"), reason);
}

describe("the request form", () => {
  it("offers exactly the server's types in its order, marking the unpaid one", async () => {
    await openManagerScreen("My Leave");

    expect(await screen.findByLabelText("Casual Leave")).toBeTruthy();
    expect(screen.getByLabelText("Loss of Pay (unpaid)")).toBeTruthy();
    // the types appear once each, in the server's order
    const rendered = screen.toJSON() as unknown;
    const text = JSON.stringify(rendered, (_key, value) => (value && typeof value === "object" && "_owner" in value ? undefined : value));
    expect(text.indexOf("Casual Leave")).toBeLessThan(text.indexOf("Loss of Pay (unpaid)"));
  });

  it("keeps Submit disabled until a type, both dates and a reason are given, and limits the reason to 500 characters", async () => {
    await openManagerScreen("My Leave");
    const submit = await screen.findByLabelText("Submit");
    expect(submit.props.accessibilityState?.disabled).toBe(true);

    await press("Casual Leave");
    await chooseDate("First date", "not chosen", "Monday 12 October 2026");
    expect((await screen.findByLabelText("Submit")).props.accessibilityState?.disabled).toBe(true);
    await chooseDate("Last date", "not chosen", "Wednesday 14 October 2026");
    await fireEvent.changeText(screen.getByLabelText("Reason"), "   ");
    expect(screen.getByLabelText("Submit").props.accessibilityState?.disabled).toBe(true);
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Family function");
    expect(screen.getByLabelText("Submit").props.accessibilityState?.disabled).toBe(false);
    expect(screen.getByLabelText("Reason").props.maxLength).toBe(500);
  });

  it("sends the request straight to the server with no preview, then shows it Pending at the top and clears the form", async () => {
    const { manager, server } = await openManagerScreen("My Leave");

    await fill();
    await fireEvent.press(await screen.findByLabelText("Half day on the last day"));
    await press("Submit");

    expect(await screen.findByText("Request submitted")).toBeTruthy();
    expect(manager.leaveRequests).toEqual([
      {
        leaveTypeId: "lt-1",
        firstDate: "2026-10-12",
        lastDate: "2026-10-14",
        firstHalf: false,
        lastHalf: true,
        reason: "Family function",
      },
    ]);
    expect(server.calls.filter((c) => c.path.includes("preview"))).toHaveLength(0);
    expect(await screen.findByLabelText(/Casual Leave, 12\/10\/2026 – 14\/10\/2026, half day at the end, Pending/)).toBeTruthy();
    // cleared, so the same request cannot be sent twice
    expect(screen.getByLabelText("Reason").props.value).toBe("");
    expect(screen.getByLabelText("Submit").props.accessibilityState?.disabled).toBe(true);
  });

  it("sends one request for a double tap", async () => {
    const { manager } = await openManagerScreen("My Leave");
    await fill();
    const button = await screen.findByLabelText("Submit");

    await fireEvent.press(button);
    await fireEvent.press(button);

    await screen.findByText("Request submitted");
    expect(manager.leaveRequests).toHaveLength(1);
  });

  it("does not show the form without the Create action", async () => {
    await openManagerScreen("My Leave", { model: managerFieldModel({ create: false }) });

    expect(await screen.findByText("Your requests")).toBeTruthy();
    expect(screen.queryByText("Request leave")).toBeNull();
    expect(screen.queryByLabelText("Submit")).toBeNull();
  });
});

describe("what the server may refuse", () => {
  const refusals: [string, number, string][] = [
    ["reversed dates", 400, "The last date cannot be before the first date."],
    ["a range with no working day", 400, "There is no working day in this range."],
    ["a date before joining", 400, "The first date is before your joining date."],
    ["no joining date", 400, "No joining date is recorded for you."],
    ["a missing reason", 400, "Give a reason."],
    ["a half day on a middle day", 400, "A half day can be only the first or the last day."],
    ["an overlap with another request", 409, "This overlaps another pending or approved request."],
  ];

  it.each(refusals)("shows the server's reason for %s, creates nothing and keeps every entry", async (_name, status, reason) => {
    const { manager } = await openManagerScreen("My Leave", {
      manager: { leaveRefusal: () => ({ status, body: { reason } }) },
    });
    await fill();

    await press("Submit");

    expect(await screen.findByText(reason)).toBeTruthy();
    expect(manager.leaves).toHaveLength(0);
    expect(screen.queryByText("Request submitted")).toBeNull();
    expect(screen.getByLabelText("Reason").props.value).toBe("Family function");
    expect(screen.getByLabelText("First date, Monday 12 October 2026")).toBeTruthy();
  });

  it("says nothing was sent when offline, and sends it on the next try", async () => {
    const { manager, server } = await openManagerScreen("My Leave");
    await fill();
    server.offline = true;

    await press("Submit");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(screen.getByLabelText("Reason").props.value).toBe("Family function");
    expect(manager.leaves).toHaveLength(0);

    server.offline = false;
    await press("Submit");
    expect(await screen.findByText("Request submitted")).toBeTruthy();
    expect(manager.leaves).toHaveLength(1);
  });
});

describe("the list", () => {
  const requests = [
    managerLeave({ id: "ml-1", status: "PENDING" }),
    managerLeave({
      id: "ml-2",
      status: "REJECTED",
      firstDate: "2026-09-01",
      lastDate: "2026-09-02",
      decidedAt: "2026-09-01T05:30:00Z",
      decisionReason: "Audit week",
    }),
    managerLeave({ id: "ml-3", status: "APPROVED", firstDate: "2026-08-10", lastDate: "2026-08-10", decidedAt: "2026-08-08T05:30:00Z" }),
    managerLeave({ id: "ml-4", status: "REVOKED", firstDate: "2026-07-01", lastDate: "2026-07-03", decisionReason: "Needed on site" }),
    managerLeave({ id: "ml-5", status: "CANCELLED", firstDate: "2026-06-01", lastDate: "2026-06-01" }),
  ];

  it("shows the Manager's requests newest first with type, dates, status and the decision with its reason", async () => {
    await openManagerScreen("My Leave", { manager: { leaves: requests } });

    expect(await screen.findByLabelText(/Casual Leave, 12\/10\/2026 – 14\/10\/2026, Pending/)).toBeTruthy();
    expect(screen.getByLabelText(/01\/09\/2026 – 02\/09\/2026, Rejected, Rejected on 01\/09\/2026 11:00, Audit week/)).toBeTruthy();
    expect(screen.getByText("Reason given: Audit week")).toBeTruthy();
    expect(screen.getByText("Reason given: Needed on site")).toBeTruthy();
    expect(screen.getByLabelText(/10\/08\/2026 – 10\/08\/2026, Approved/)).toBeTruthy();
    expect(screen.getByLabelText(/01\/07\/2026 – 03\/07\/2026, Revoked/)).toBeTruthy();
    expect(screen.getByLabelText(/01\/06\/2026 – 01\/06\/2026, Cancelled/)).toBeTruthy();
  });

  it("says when there are no requests", async () => {
    await openManagerScreen("My Leave");

    expect(await screen.findByText("You have not asked for leave yet.")).toBeTruthy();
  });

  it("offers Cancel on a Pending request only, confirms first, then cancels and reloads", async () => {
    const { manager } = await openManagerScreen("My Leave", { manager: { leaves: requests } });
    await screen.findByLabelText(/Casual Leave, 12\/10\/2026 – 14\/10\/2026, Pending/);
    expect(screen.getAllByText("Cancel request")).toHaveLength(1);

    await press("Cancel request for 12/10/2026 – 14/10/2026");
    expect(await screen.findByText("Cancel this request?")).toBeTruthy();
    expect(manager.cancelledLeaves).toHaveLength(0);
    await confirmCancel();

    await screen.findByLabelText(/Casual Leave, 12\/10\/2026 – 14\/10\/2026, Cancelled/);
    expect(manager.cancelledLeaves).toEqual(["ml-1"]);
  });

  it("keeps the request when the confirmation is dismissed", async () => {
    const { manager } = await openManagerScreen("My Leave", { manager: { leaves: requests } });
    await press("Cancel request for 12/10/2026 – 14/10/2026");

    await pressText("Keep request");

    expect(manager.cancelledLeaves).toHaveLength(0);
    expect(screen.queryByText("Cancel this request?")).toBeNull();
  });

  it("shows the server's reason when a cancel is refused, and leaves the list as it was", async () => {
    await openManagerScreen("My Leave", {
      manager: { leaves: requests, cancelRefusal: () => ({ status: 409, body: { reason: "This request was already decided." } }) },
    });
    await press("Cancel request for 12/10/2026 – 14/10/2026");

    await confirmCancel();

    expect(await screen.findByText("This request was already decided.")).toBeTruthy();
    expect(screen.getByLabelText(/Casual Leave, 12\/10\/2026 – 14\/10\/2026, Pending/)).toBeTruthy();
  });

  it("hides Cancel without the Delete action", async () => {
    await openManagerScreen("My Leave", { model: managerFieldModel({ remove: false }), manager: { leaves: requests } });

    await screen.findByLabelText(/Casual Leave, 12\/10\/2026 – 14\/10\/2026, Pending/);
    expect(screen.queryByText("Cancel request")).toBeNull();
  });

  it("reloads from the server on a pull down, picking up a decision made on the web", async () => {
    const { manager } = await openManagerScreen("My Leave", { manager: { leaves: [managerLeave({ status: "PENDING" })] } });
    await screen.findByLabelText(/12\/10\/2026 – 14\/10\/2026, Pending/);
    manager.leaves = [managerLeave({ status: "APPROVED", decidedAt: "2026-10-06T05:30:00Z" })];

    await pullToRefresh();

    expect(await screen.findByLabelText(/12\/10\/2026 – 14\/10\/2026, Approved/)).toBeTruthy();
  });

  it("shows a failed load with Try again and no stale list", async () => {
    await openManagerScreen("My Leave", {
      prepare: (server) =>
        server.on("GET /api/v1/manager-attendance/me/leave", { status: 500, body: { reason: "Could not load your leave." } }),
    });

    expect(await screen.findByText("Could not load your leave.")).toBeTruthy();
    expect(screen.getByText("Try again")).toBeTruthy();
    expect(screen.queryByText("Your requests")).toBeNull();
  });
});
