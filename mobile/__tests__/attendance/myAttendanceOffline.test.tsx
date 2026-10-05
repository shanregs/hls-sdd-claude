import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { monthView } from "../support/attendanceFixtures";
import { openAttendanceScreen, openDay } from "../support/attendanceApp";

afterEach(() => jest.restoreAllMocks());

const TODAY = "Monday 5 October 2026";

describe("My Attendance: no connection and failed loads (spec 019 SC-006, FR-020)", () => {
  it("keeps the draft, shows nothing as saved, and saves once the connection is back", async () => {
    const { server, attendance } = await openAttendanceScreen("My Attendance");
    await openDay(`${TODAY}, not marked`);

    await fireEvent.press(await screen.findByLabelText("Absent"));
    await fireEvent.press(screen.getByLabelText("Half day"));
    await fireEvent.changeText(screen.getByLabelText("Note"), "Fever");

    server.offline = true;
    await fireEvent.press(screen.getByLabelText("Save"));

    expect(await screen.findByText("No connection. Your change was not saved.")).toBeTruthy();
    expect(attendance.mine.size).toBe(0);
    expect(screen.getByRole("radio", { name: "Absent", checked: true })).toBeTruthy();
    expect(screen.getByRole("radio", { name: "Half day", checked: true })).toBeTruthy();
    expect(screen.getByLabelText("Note").props.value).toBe("Fever");
    expect(screen.getByLabelText(`${TODAY}, not marked`)).toBeTruthy();

    server.offline = false;
    await fireEvent.press(screen.getByLabelText("Save"));

    await waitFor(() => expect(attendance.mine.get("2026-10-05")).toEqual({ statusCode: "A", dayValue: 0.5, note: "Fever" }));
    expect(await screen.findByLabelText(`${TODAY}, Absent, half day, set by you`)).toBeTruthy();
  });

  it("shows No connection with Retry when a month cannot be loaded, and never keeps the old month as current", async () => {
    const { server } = await openAttendanceScreen("My Attendance");
    await screen.findByLabelText(`${TODAY}, not marked`);

    server.offline = true;
    await fireEvent.press(screen.getByLabelText("Previous month"));

    expect(await screen.findByText("No connection")).toBeTruthy();
    expect(screen.queryByLabelText(`${TODAY}, not marked`)).toBeNull();
    expect(screen.queryByLabelText(/^Working days/)).toBeNull();

    server.offline = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByLabelText("Tuesday 1 September 2026, not marked")).toBeTruthy();
  });

  it("shows an error with Retry for a failed month, then recovers", async () => {
    let failing = true;
    await openAttendanceScreen("My Attendance", {
      attendance: {
        ownMonth: (month) => (failing ? { status: 500, body: {} } : monthView(month)),
      },
    });

    expect(await screen.findByText("Something went wrong")).toBeTruthy();
    expect(screen.queryByLabelText(/^Working days/)).toBeNull();

    failing = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByLabelText(`${TODAY}, not marked`)).toBeTruthy();
  });
});
