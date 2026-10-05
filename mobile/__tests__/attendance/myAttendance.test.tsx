import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { monthView } from "../support/attendanceFixtures";
import { closeSheet, openAttendanceScreen, openDay } from "../support/attendanceApp";

afterEach(() => jest.restoreAllMocks());

describe("My Attendance (spec 019 US1)", () => {
  it("opens on the server's current month even when the phone clock is a day off", async () => {
    // Server: 1 October, 07:30 India time. Phone: 30 hours earlier, still in September.
    const { server } = await openAttendanceScreen("My Attendance", {
      today: "2026-10-01",
      phoneOffsetHours: -30,
      attendance: { ownMonth: (month) => monthView(month, { today: "2026-10-01" }) },
    });

    expect(await screen.findByText("October 2026")).toBeTruthy();
    const calls = server.callsTo("GET /api/v1/attendance/me");
    expect(calls).toHaveLength(1);
    expect(calls[0].query.month).toBe("2026-10");
  });

  it("shows each day with its state and status, half days, and the server's totals", async () => {
    await openAttendanceScreen("My Attendance", {
      attendance: {
        ownMonth: (month) =>
          monthView(month, {
            holidays: ["2026-10-02"],
            overrides: {
              "2026-10-05": { mark: {} },
              "2026-10-03": { mark: { code: "A", codeName: "Absent", category: "LEAVE", dayValue: 0.5 } },
            },
            rollup: { workingDays: 22, daysWorked: 11.5, daysLeave: 2, unmarked: 7 },
          }),
      },
    });

    expect(await screen.findByLabelText("Monday 5 October 2026, Present, whole day, set by you")).toBeTruthy();
    expect(screen.getByLabelText("Saturday 3 October 2026, Absent, half day, set by you")).toBeTruthy();
    expect(screen.getByLabelText("Sunday 4 October 2026, weekly off")).toBeTruthy();
    expect(screen.getByLabelText("Friday 2 October 2026, holiday")).toBeTruthy();
    expect(screen.getByLabelText("Tuesday 6 October 2026, future day")).toBeTruthy();
    expect(screen.getByLabelText("Thursday 1 October 2026, not marked")).toBeTruthy();

    expect(screen.getByLabelText("Working days 22")).toBeTruthy();
    expect(screen.getByLabelText("Days worked 11.5")).toBeTruthy();
    expect(screen.getByLabelText("Leave 2")).toBeTruthy();
    expect(screen.getByLabelText("Unmarked 7")).toBeTruthy();
  });

  it("shows a placement gap as not placed and a locked month as Locked", async () => {
    await openAttendanceScreen("My Attendance", {
      attendance: { ownMonth: (month) => monthView(month, { placedFrom: "2026-10-03", locked: true }) },
    });

    expect(await screen.findByLabelText("Thursday 1 October 2026, not placed in a school")).toBeTruthy();
    expect(screen.getByLabelText("Locked")).toBeTruthy();
  });

  it("offers Save on a weekly-off day the server marks SELF, and not on one it marks NONE", async () => {
    await openAttendanceScreen("My Attendance", {
      attendance: {
        ownMonth: (month) =>
          monthView(month, {
            today: "2026-10-05",
            overrides: { "2026-10-11": { state: "WEEKLY_OFF", editableBy: "NONE" } },
          }),
      },
    });

    await openDay("Sunday 4 October 2026, weekly off");
    expect(await screen.findByLabelText("Save")).toBeTruthy();
    await closeSheet();

    await openDay("Sunday 11 October 2026, weekly off");
    expect(await screen.findByRole("header", { name: "Sunday 11 October 2026" })).toBeTruthy();
    expect(screen.queryByLabelText("Save")).toBeNull();
  });

  it("changes month and returns, one request per month", async () => {
    const { server } = await openAttendanceScreen("My Attendance");
    await screen.findByText("October 2026");

    await fireEvent.press(screen.getByLabelText("Previous month"));
    expect(await screen.findByText("September 2026")).toBeTruthy();
    await screen.findByLabelText("Tuesday 1 September 2026, not marked");

    await fireEvent.press(screen.getByLabelText("Next month"));
    expect(await screen.findByText("October 2026")).toBeTruthy();
    await screen.findByLabelText("Thursday 1 October 2026, not marked");

    expect(server.callsTo("GET /api/v1/attendance/me").map((c) => c.query.month)).toEqual([
      "2026-10",
      "2026-09",
      "2026-10",
    ]);
  });

  it("reaches January and December of the current year and no other year", async () => {
    await openAttendanceScreen("My Attendance");
    await screen.findByText("October 2026");

    await fireEvent.press(screen.getByLabelText("Choose month, October 2026"));
    expect(screen.getByLabelText("January 2026")).toBeTruthy();
    expect(screen.getByLabelText("December 2026")).toBeTruthy();
    expect(screen.queryByLabelText("December 2025")).toBeNull();
    expect(screen.queryByLabelText("January 2027")).toBeNull();

    await fireEvent.press(screen.getByLabelText("January 2026"));
    expect(await screen.findByText("January 2026")).toBeTruthy();
    expect(screen.getByLabelText("Previous month").props.accessibilityState?.disabled).toBe(true);
  });

  it("shows the server's text when the profile is not set up", async () => {
    await openAttendanceScreen("My Attendance", {
      attendance: {
        ownMonth: () => ({ status: 404, body: { message: "Your profile has not been set up yet." } }),
      },
    });

    expect(await screen.findByText("Your profile has not been set up yet.")).toBeTruthy();
    await waitFor(() => expect(screen.queryByLabelText("Working days 22")).toBeNull());
  });
});
