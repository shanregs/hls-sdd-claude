import { fireEvent, screen } from "@testing-library/react-native";
import { managerFieldModel, managerMonth, monthDay, today } from "../support/managerFixtures";
import { openManagerScreen } from "../support/managerApp";

/** Spec 033 T031 (US4): the Manager's own month, as the server describes it. */
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

const OCT = (days = [] as ReturnType<typeof monthDay>[], overrides = {}) =>
  managerMonth({ month: "2026-10", days, ...overrides });

const DAYS = [
  monthDay("2026-10-01", { status: "WORKED", dayType: "OFFICE", checkInAt: "2026-10-01T04:12:00Z", checkOutAt: "2026-10-01T12:30:00Z" }),
  monthDay("2026-10-02", { status: "FIELD", dayType: "FIELD", checkInAt: "2026-10-02T04:12:00Z", purpose: "Visit to St Mary's", flags: ["NO_CHECK_OUT"] }),
  monthDay("2026-10-03", { status: "WEEKLY_OFF" }),
  monthDay("2026-10-05", { status: "LEAVE", leave: { fraction: 0.5, unpaid: true } }),
  monthDay("2026-10-06", { status: "MISSED" }),
  monthDay("2026-10-07", { status: "HOLIDAY" }),
  monthDay("2026-10-08", { status: "NOT_EMPLOYED" }),
  monthDay("2026-10-09", { status: "PENDING" }),
  monthDay("2026-10-12", { status: "ABSENT" }),
  monthDay("2026-10-13", { status: "NO_CHECK_OUT", dayType: "OFFICE", checkInAt: "2026-10-13T04:12:00Z", flags: ["NO_CHECK_OUT"] }),
];

async function openMonth(month = OCT(DAYS), setup = {}) {
  return openManagerScreen("My Month", { manager: { months: { "2026-10": month } }, ...setup });
}

describe("the month", () => {
  it("opens on the current month and shows each day's status from the server, in words", async () => {
    await openMonth();

    expect(await screen.findByLabelText("Thursday 1 October 2026, worked, checked in 09:42, office, checked out 18:00")).toBeTruthy();
    expect(screen.getByLabelText("Friday 2 October 2026, field day, checked in 09:42, field, no check-out")).toBeTruthy();
    expect(screen.getByLabelText("Saturday 3 October 2026, weekly off")).toBeTruthy();
    expect(screen.getByLabelText("Monday 5 October 2026, leave, half day leave")).toBeTruthy();
    expect(screen.getByLabelText("Tuesday 6 October 2026, no check-in")).toBeTruthy();
    expect(screen.getByLabelText("Wednesday 7 October 2026, holiday")).toBeTruthy();
    expect(screen.getByLabelText("Thursday 8 October 2026, not employed")).toBeTruthy();
    expect(screen.getByLabelText("Friday 9 October 2026, not yet")).toBeTruthy();
    expect(screen.getByLabelText("Monday 12 October 2026, absent")).toBeTruthy();
    expect(screen.getByLabelText("Tuesday 13 October 2026, no check-out, checked in 09:42, office")).toBeTruthy();
  });

  it("shows exactly the server's figures and adds nothing up itself", async () => {
    await openMonth(
      OCT(DAYS, {
        figures: { workingDays: 22, daysWorked: 17, leavePaid: 2, leaveUnpaid: 1, unpaid: 1, fieldDays: 5, pending: [] },
      }),
    );

    // the days above show one worked day, but the figures are the server's
    expect(await screen.findByLabelText("Working days 22")).toBeTruthy();
    expect(screen.getByLabelText("Days worked 17")).toBeTruthy();
    expect(screen.getByLabelText("Leave (paid) 2")).toBeTruthy();
    expect(screen.getByLabelText("Leave (unpaid) 1")).toBeTruthy();
    expect(screen.getByLabelText("Unpaid 1")).toBeTruthy();
    expect(screen.getByLabelText("Field days 5")).toBeTruthy();
  });

  it("shows the server's message in place of the figures when it has none", async () => {
    await openMonth(OCT(DAYS, { figures: null, figuresUnavailable: "No joining date is recorded for you." }));

    expect(await screen.findByText("No joining date is recorded for you.")).toBeTruthy();
    expect(screen.queryByLabelText("Month figures")).toBeNull();
  });

  it("says a locked month is locked", async () => {
    await openMonth(OCT(DAYS, { locked: true }));

    expect(await screen.findByLabelText("Locked")).toBeTruthy();
  });

  it("shows an empty state when the server lists no days", async () => {
    await openMonth(OCT([]));

    expect(await screen.findByText("No days to show for this month.")).toBeTruthy();
  });

  it("loads another month when the picker moves, and shows a failed load with Retry", async () => {
    const { server } = await openMonth();
    await screen.findByLabelText("Saturday 3 October 2026, weekly off");
    server.on("GET /api/v1/manager-attendance/me/months/{month}", { status: 500, body: { reason: "Could not load this month." } });

    await press("Previous month");

    expect(await screen.findByText("Could not load this month.")).toBeTruthy();
    expect(server.calls.some((c) => c.path.endsWith("/months/2026-09"))).toBe(true);
    expect(screen.queryByLabelText("Saturday 3 October 2026, weekly off")).toBeNull();
    expect(screen.getByLabelText("Retry")).toBeTruthy();
  });

  it("says there is no connection when the server cannot be reached", async () => {
    await openManagerScreen("My Month", {
      prepare: (s) =>
        s.on("GET /api/v1/manager-attendance/me/months/{month}", () => {
          throw new TypeError("Network request failed");
        }),
    });

    expect(await screen.findByText("No connection")).toBeTruthy();
  });
});

describe("a day's sheet", () => {
  it("shows the times, kind of day, purpose and flags as the server holds them", async () => {
    await openMonth();

    await press("Friday 2 October 2026, field day, checked in 09:42, field, no check-out");

    expect(await screen.findByText("Checked in at 09:42, Field")).toBeTruthy();
    expect(screen.getByText("Purpose: Visit to St Mary's")).toBeTruthy();
    expect(screen.getByText("No check-out")).toBeTruthy();
  });

  it("describes leave as whole or half, paid or unpaid", async () => {
    await openMonth();

    await press("Monday 5 October 2026, leave, half day leave");

    expect(await screen.findByText("Half day leave (unpaid)")).toBeTruthy();
  });

  it("shows the photo with its details when the day has one", async () => {
    const day = monthDay("2026-10-02", { status: "FIELD", dayType: "FIELD", checkInAt: "2026-10-02T04:12:00Z", hasPhoto: true });
    await openMonth(OCT([day]), {
      manager: {
        months: { "2026-10": OCT([day]) },
        photo: { bytes: new Uint8Array(1024), contentType: "image/jpeg", fileName: "gate.jpg" },
      },
    });

    await press("Friday 2 October 2026, field day, checked in 09:42, field");

    expect(await screen.findByText("gate.jpg, JPG, 1 KB")).toBeTruthy();
  });

  it("completes a missing check-out from the day, then closes and reloads the month", async () => {
    const { manager } = await openMonth();
    await press("Friday 2 October 2026, field day, checked in 09:42, field, no check-out");

    await pressText("Complete check-out");
    await fireEvent.changeText(await screen.findByLabelText("Check-out time"), "17:45");
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Left the phone behind");
    await pressText("Send");

    expect(manager.completions).toEqual([{ date: "2026-10-02", time: "17:45", reason: "Left the phone behind" }]);
    await screen.findByLabelText("Friday 2 October 2026, field day, checked in 09:42, field, no check-out");
  });

  it("offers Complete check-out on a day whose status is No check-out", async () => {
    await openMonth();

    await press("Tuesday 13 October 2026, no check-out, checked in 09:42, office");

    expect(await screen.findByText("Complete check-out")).toBeTruthy();
  });

  it("offers no change in a locked month", async () => {
    await openMonth(OCT(DAYS, { locked: true }));

    await press("Friday 2 October 2026, field day, checked in 09:42, field, no check-out");

    expect(await screen.findByText("This month is locked, so this day cannot change.")).toBeTruthy();
    expect(screen.queryByText("Complete check-out")).toBeNull();
  });

  it("offers no change without the Create action", async () => {
    await openMonth(OCT(DAYS), { model: managerFieldModel({ create: false }) });

    await press("Friday 2 October 2026, field day, checked in 09:42, field, no check-out");

    expect(await screen.findByText("No check-out")).toBeTruthy();
    expect(screen.queryByText("Complete check-out")).toBeNull();
  });

  it("does not offer a check-out request on a day that has a check-in", async () => {
    await openMonth();

    await press("Thursday 1 October 2026, worked, checked in 09:42, office, checked out 18:00");

    expect(await screen.findByText("Checked out at 18:00")).toBeTruthy();
    expect(screen.queryByText("Ask to add a check-in")).toBeNull();
    expect(today()).toBeTruthy();
  });
});
