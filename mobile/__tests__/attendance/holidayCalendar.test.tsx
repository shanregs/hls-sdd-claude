import { fireEvent, screen } from "@testing-library/react-native";
import { MANAGER_MODEL, TEACHER_MODEL } from "../support/fixtures";
import { openAttendanceScreen } from "../support/attendanceApp";

afterEach(() => jest.restoreAllMocks());

describe("Holiday Calendar (spec 019 US3)", () => {
  it.each([
    ["Teacher", TEACHER_MODEL],
    ["Manager", MANAGER_MODEL],
  ])("shows the current month for a %s with holidays, weekly offs and descriptions", async (_name, model) => {
    const { server } = await openAttendanceScreen("Holiday Calendar", { model });

    expect(await screen.findByText("October 2026")).toBeTruthy();
    expect(screen.getByLabelText("Friday 2 October 2026, holiday")).toBeTruthy();
    expect(screen.getByLabelText("Tuesday 20 October 2026, holiday")).toBeTruthy();
    expect(screen.getByLabelText("Sunday 4 October 2026, weekly off")).toBeTruthy();
    expect(screen.getByLabelText("Sunday 25 October 2026, weekly off")).toBeTruthy();
    expect(screen.getByLabelText("Monday 5 October 2026, working day")).toBeTruthy();

    expect(screen.getByText("Gandhi Jayanti")).toBeTruthy();
    expect(screen.getByText("Ayudha Puja")).toBeTruthy();
    expect(screen.queryByText("Republic Day")).toBeNull();
    // One request for the whole year's calendar.
    expect(server.callsTo("GET /api/v1/attendance/calendar")).toHaveLength(1);
  });

  it("reaches any month of the current year and no other year, without another request", async () => {
    const { server } = await openAttendanceScreen("Holiday Calendar");
    await screen.findByText("October 2026");

    await fireEvent.press(screen.getByLabelText("Choose month, October 2026"));
    expect(screen.queryByLabelText("December 2025")).toBeNull();
    expect(screen.queryByLabelText("January 2027")).toBeNull();
    await fireEvent.press(screen.getByLabelText("January 2026"));

    expect(await screen.findByText("Republic Day")).toBeTruthy();
    expect(screen.getByLabelText("Monday 26 January 2026, holiday")).toBeTruthy();
    expect(server.callsTo("GET /api/v1/attendance/calendar")).toHaveLength(1);
  });

  it("lists all holidays of the year in date order and returns to the same month", async () => {
    await openAttendanceScreen("Holiday Calendar");
    await screen.findByText("October 2026");

    await fireEvent.press(screen.getByLabelText("All holidays this year"));
    expect(await screen.findByText("All holidays in 2026")).toBeTruthy();
    const names = screen
      .getAllByLabelText(/, (Republic Day|Gandhi Jayanti|Ayudha Puja|Deepavali|Christmas)$/)
      .map((n) => n.props.accessibilityLabel.split(", ").pop());
    expect(names).toEqual(["Republic Day", "Gandhi Jayanti", "Ayudha Puja", "Deepavali", "Christmas"]);

    await fireEvent.press(screen.getByLabelText("Back to calendar"));
    expect(await screen.findByText("October 2026")).toBeTruthy();
  });

  it("has no way to add, change or delete a holiday or a weekly off", async () => {
    await openAttendanceScreen("Holiday Calendar");
    await screen.findByText("October 2026");

    expect(screen.queryByLabelText(/\b(add|edit|delete|remove|save)\b/i)).toBeNull();
    // Days are shown but are not buttons.
    expect(screen.queryByRole("button", { name: /holiday$/ })).toBeNull();
    await fireEvent.press(screen.getByLabelText("All holidays this year"));
    await screen.findByText("All holidays in 2026");
    expect(screen.queryByLabelText(/\b(add|edit|delete|remove|save)\b/i)).toBeNull();
  });

  it("shows an error with Retry for a failed load and recovers", async () => {
    let failing = true;
    await openAttendanceScreen("Holiday Calendar", {
      prepare: (server, attendance) =>
        server.on("GET /api/v1/attendance/calendar", () =>
          failing ? { status: 500, body: {} } : { status: 200, body: attendance.calendar },
        ),
    });

    expect(await screen.findByText("Something went wrong")).toBeTruthy();
    expect(screen.queryByLabelText(/holiday$/)).toBeNull();

    failing = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByText("October 2026")).toBeTruthy();
    expect(screen.getByText("Gandhi Jayanti")).toBeTruthy();
  });
});
