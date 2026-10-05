import { fireEvent, screen } from "@testing-library/react-native";
import { monthView } from "../support/attendanceFixtures";
import { closeSheet, openAttendanceScreen, openDay } from "../support/attendanceApp";

afterEach(() => jest.restoreAllMocks());

describe("Attendance History (spec 019 US2)", () => {
  it("offers the current and the previous year and no other", async () => {
    await openAttendanceScreen("Attendance History");
    await screen.findByText("October 2026");

    await fireEvent.press(screen.getByLabelText("Choose month, October 2026"));
    expect(screen.getByLabelText("January 2025")).toBeTruthy();
    expect(screen.getByLabelText("December 2025")).toBeTruthy();
    expect(screen.getByLabelText("January 2026")).toBeTruthy();
    expect(screen.getByLabelText("December 2026")).toBeTruthy();
    expect(screen.queryByLabelText("December 2024")).toBeNull();
    expect(screen.queryByLabelText("January 2027")).toBeNull();
  });

  it("shows a month of the previous year with the same cells and totals", async () => {
    const { server } = await openAttendanceScreen("Attendance History", {
      attendance: {
        ownMonth: (month) =>
          monthView(month, {
            today: "2026-10-05",
            overrides: { "2025-06-10": { mark: { date: "2025-06-10", setByKind: "SELF" } } },
            rollup: { workingDays: 26, daysWorked: 24, daysLeave: 2, unmarked: 0 },
          }),
      },
    });
    await screen.findByText("October 2026");

    await fireEvent.press(screen.getByLabelText("Choose month, October 2026"));
    await fireEvent.press(screen.getByLabelText("June 2025"));

    expect(await screen.findByLabelText("Tuesday 10 June 2025, Present, whole day, set by you")).toBeTruthy();
    expect(screen.getByLabelText("Days worked 24")).toBeTruthy();
    expect(server.callsTo("GET /api/v1/attendance/me").map((c) => c.query.month)).toEqual(["2026-10", "2025-06"]);
  });

  it("says Locked for a locked month", async () => {
    await openAttendanceScreen("Attendance History", {
      attendance: { ownMonth: (month) => monthView(month, { locked: true }) },
    });

    expect(await screen.findByLabelText("Locked")).toBeTruthy();
  });

  it("shows a month with no placement as not placed with zero totals and no error", async () => {
    await openAttendanceScreen("Attendance History", {
      attendance: {
        ownMonth: (month) =>
          monthView(month, {
            placedFrom: "2099-01-01",
            rollup: { workingDays: 0, daysWorked: 0, daysLeave: 0, unmarked: 0 },
          }),
      },
    });

    expect(await screen.findByLabelText("Thursday 1 October 2026, not placed in a school")).toBeTruthy();
    expect(screen.getByLabelText("Working days 0")).toBeTruthy();
    expect(screen.queryByText("Something went wrong")).toBeNull();
  });

  it("opens a day read-only: no Save, Clear or History, even for a day the server would let the Teacher change on My Attendance", async () => {
    await openAttendanceScreen("Attendance History", {
      attendance: { ownMonth: (month) => monthView(month, { today: "2026-10-05" }) },
    });

    await openDay("Monday 5 October 2026, not marked");
    expect(await screen.findByRole("header", { name: "Monday 5 October 2026" })).toBeTruthy();
    expect(screen.queryByLabelText("Save")).toBeNull();
    expect(screen.queryByLabelText("Clear mark")).toBeNull();
    expect(screen.queryByLabelText("History")).toBeNull();
    await closeSheet();
  });
});
