import { fireEvent, screen } from "@testing-library/react-native";
import { managerFieldModel, managerMonth, monthDay, regularization } from "../support/managerFixtures";
import { openManagerScreen } from "../support/managerApp";

/** Spec 033 T041 (US6): asking for a forgotten check-in, following it, and cancelling it. */
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

const MISSED = "Tuesday 6 October 2026, absent";
const days = [
  monthDay("2026-10-06", { status: "ABSENT" }),
  monthDay("2026-10-01", { status: "WORKED", dayType: "OFFICE", checkInAt: "2026-10-01T04:12:00Z", checkOutAt: "2026-10-01T12:30:00Z" }),
  monthDay("2026-10-09", { status: "PENDING" }),
];
const month = (locked = false) => managerMonth({ month: "2026-10", days, locked });

async function open(setup = {}) {
  return openManagerScreen("My Month", { manager: { months: { "2026-10": month() } }, ...setup });
}

async function fillForm(time = "09:15", reason = "Phone was off") {
  await fireEvent.changeText(await screen.findByLabelText("Time you arrived"), time);
  await fireEvent.changeText(screen.getByLabelText("Reason"), reason);
}

describe("asking to add a check-in", () => {
  it("is offered on a day with no check-in, and not on a worked day, a future day or in a locked month", async () => {
    await open();
    await press(MISSED);
    expect(await screen.findByText("Ask to add a check-in")).toBeTruthy();
    await press("Close");

    await press("Thursday 1 October 2026, worked, checked in 09:42, office, checked out 18:00");
    await screen.findByText("Checked out at 18:00");
    expect(screen.queryByText("Ask to add a check-in")).toBeNull();
    await press("Close");

    await press("Friday 9 October 2026, not yet");
    await screen.findAllByText("Not yet");
    expect(screen.queryByText("Ask to add a check-in")).toBeNull();
  });

  it("is not offered in a locked month or without the Create action", async () => {
    await openManagerScreen("My Month", { manager: { months: { "2026-10": month(true) } } });
    await press(MISSED);
    await screen.findByText("This month is locked, so this day cannot change.");
    expect(screen.queryByText("Ask to add a check-in")).toBeNull();
  });

  it("is not offered without the Create action", async () => {
    await open({ model: managerFieldModel({ create: false }) });
    await press(MISSED);
    await screen.findAllByText("Absent");
    expect(screen.queryByText("Ask to add a check-in")).toBeNull();
  });

  it("sends the date, time, day type and reason, then lists the request as Pending", async () => {
    const { manager } = await open();
    await press(MISSED);
    await pressText("Ask to add a check-in");
    expect(await screen.findByText("Ask to add a check-in for 06/10/2026")).toBeTruthy();

    await fillForm();
    await pressText("Send");

    expect(await screen.findByLabelText(/Check-in for 06\/10\/2026 at 09:15, Pending/)).toBeTruthy();
    expect(manager.regularizationRequests).toEqual([
      { date: "2026-10-06", proposedTime: "09:15", dayType: "OFFICE", reason: "Phone was off" },
    ]);
    expect(screen.queryByText("Ask to add a check-in for 06/10/2026")).toBeNull();
  });

  it("sends a purpose for a Field day, trimmed, and none for an Office day", async () => {
    const { manager } = await open();
    await press(MISSED);
    await pressText("Ask to add a check-in");

    await fireEvent.press(await screen.findByLabelText("Field day"));
    await fireEvent.changeText(await screen.findByLabelText("Purpose"), "  Visit to St Mary's  ");
    await fillForm("10:00", "Forgot");
    await pressText("Send");

    await screen.findByLabelText(/Check-in for 06\/10\/2026 at 10:00, Pending/);
    expect(manager.regularizationRequests[0]).toMatchObject({ dayType: "FIELD", purpose: "Visit to St Mary's" });
  });

  it("keeps Send disabled until a time and a reason are given, and limits the reason to 500 characters", async () => {
    const { manager } = await open();
    await press(MISSED);
    await pressText("Ask to add a check-in");
    await fireEvent.changeText(await screen.findByLabelText("Time you arrived"), "09:15");

    await fireEvent.press(screen.getByText("Send"));
    await fireEvent.changeText(screen.getByLabelText("Reason"), "   ");
    await fireEvent.press(screen.getByText("Send"));

    expect(manager.regularizationRequests).toHaveLength(0);
    expect(screen.getByLabelText("Reason").props.maxLength).toBe(500);
  });

  it.each([
    ["a future day", 400, "You cannot ask for a check-in on a future day."],
    ["a day that already has a check-in", 400, "That day already has a check-in."],
    ["a pending request for the date", 400, "There is already a pending request for that date."],
    ["a missing reason", 400, "Give a reason."],
    ["a locked month", 409, "Attendance for October 2026 is locked."],
  ])("shows the server's reason for %s and keeps the entries", async (_name, status, reason) => {
    await open({ manager: { months: { "2026-10": month() }, regularizationRefusal: () => ({ status, body: { reason } }) } });
    await press(MISSED);
    await pressText("Ask to add a check-in");
    await fillForm("09:15", "Phone was off");

    await pressText("Send");

    expect(await screen.findByText(reason)).toBeTruthy();
    expect(screen.getByLabelText("Time you arrived").props.value).toBe("09:15");
    expect(screen.getByLabelText("Reason").props.value).toBe("Phone was off");
  });

  it("keeps the entries and shows nothing as sent when offline", async () => {
    const { server, manager } = await open();
    await press(MISSED);
    await pressText("Ask to add a check-in");
    await fillForm();
    server.offline = true;

    await pressText("Send");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(screen.getByLabelText("Reason").props.value).toBe("Phone was off");
    expect(manager.regularizations).toHaveLength(0);
  });
});

describe("the requests", () => {
  const requests = [
    regularization({ id: "rg-1", status: "PENDING" }),
    regularization({ id: "rg-2", date: "2026-10-01", status: "APPROVED", decidedAt: "2026-10-02T05:30:00Z" }),
    regularization({ id: "rg-3", date: "2026-09-30", status: "REJECTED", decidedAt: "2026-10-01T05:30:00Z", decisionReason: "Attendance already complete" }),
  ];

  it("lists each with its status and, for a rejection, the reason", async () => {
    await open({ manager: { months: { "2026-10": month() }, regularizations: requests } });

    expect(await screen.findByLabelText(/Check-in for 02\/10\/2026 at 09:15, Pending/)).toBeTruthy();
    expect(screen.getByLabelText(/Check-in for 01\/10\/2026 at 09:15, Approved, on 02\/10\/2026 11:00/)).toBeTruthy();
    expect(screen.getByText("Reason given: Attendance already complete")).toBeTruthy();
  });

  it("says when there are none", async () => {
    await open();

    expect(await screen.findByText("You have no requests to add a forgotten check-in.")).toBeTruthy();
  });

  it("offers Cancel on a Pending request only, confirms first, then cancels and lists it Cancelled", async () => {
    const { manager } = await open({ manager: { months: { "2026-10": month() }, regularizations: requests } });
    await screen.findByLabelText(/Check-in for 02\/10\/2026 at 09:15, Pending/);
    expect(screen.getAllByText("Cancel request")).toHaveLength(1);

    await press("Cancel check-in request for 02/10/2026");
    expect(await screen.findByText("Cancel this request?")).toBeTruthy();
    expect(manager.cancelledRegularizations).toHaveLength(0);
    const buttons = await screen.findAllByText("Cancel request");
    await fireEvent.press(buttons[buttons.length - 1]);

    await screen.findByLabelText(/Check-in for 02\/10\/2026 at 09:15, Cancelled/);
    expect(manager.cancelledRegularizations).toEqual(["rg-1"]);
  });

  it("hides Cancel without the Delete action", async () => {
    await open({ model: managerFieldModel({ remove: false }), manager: { months: { "2026-10": month() }, regularizations: requests } });

    await screen.findByLabelText(/Check-in for 02\/10\/2026 at 09:15, Pending/);
    expect(screen.queryByText("Cancel request")).toBeNull();
  });
});
