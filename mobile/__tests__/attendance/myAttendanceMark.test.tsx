import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import type { StatusCode } from "../../src/api/attendanceApi";
import { monthView, statusCodes } from "../support/attendanceFixtures";
import { closeSheet, openAttendanceScreen, openDay } from "../support/attendanceApp";

afterEach(() => jest.restoreAllMocks());

const TODAY = "Monday 5 October 2026";

describe("My Attendance: marking a day (spec 019 US1)", () => {
  it("saves Present for a whole day with a note, then re-fetches so the cell and totals update", async () => {
    const { server, attendance } = await openAttendanceScreen("My Attendance");
    await openDay(`${TODAY}, not marked`);

    await fireEvent.changeText(await screen.findByLabelText("Note"), "Covered the library");
    await fireEvent.press(screen.getByLabelText("Save"));

    await waitFor(() => expect(attendance.mine.size).toBe(1));
    const put = server.callsTo("PUT /api/v1/attendance/me/marks/{date}");
    expect(put).toHaveLength(1);
    expect(put[0].params.date).toBe("2026-10-05");
    expect(put[0].body).toEqual({ statusCode: "P", dayValue: 1, note: "Covered the library" });

    // Saved only after the 2xx; the month was fetched again and shows the new mark.
    expect(await screen.findByLabelText(`${TODAY}, Present, whole day, set by you`)).toBeTruthy();
    expect(server.callsTo("GET /api/v1/attendance/me")).toHaveLength(2);
    expect(screen.queryByLabelText("Save")).toBeNull();
  });

  it("omits the note when it is empty", async () => {
    const { server } = await openAttendanceScreen("My Attendance");
    await openDay(`${TODAY}, not marked`);
    await fireEvent.press(await screen.findByLabelText("Save"));

    await waitFor(() => expect(server.callsTo("PUT /api/v1/attendance/me/marks/{date}")).toHaveLength(1));
    expect(server.callsTo("PUT /api/v1/attendance/me/marks/{date}")[0].body).toEqual({
      statusCode: "P",
      dayValue: 1,
    });
  });

  it("changes a mark to another status and a half day, sending the previous version", async () => {
    const { server } = await openAttendanceScreen("My Attendance", {
      attendance: {
        ownMonth: (month) => monthView(month, { overrides: { "2026-10-05": { mark: { version: 3 } } } }),
      },
    });
    await openDay(`${TODAY}, Present, whole day, set by you`);

    await fireEvent.press(await screen.findByLabelText("Absent"));
    await fireEvent.press(screen.getByLabelText("Half day"));
    await fireEvent.press(screen.getByLabelText("Save"));

    await waitFor(() => expect(server.callsTo("PUT /api/v1/attendance/me/marks/{date}")).toHaveLength(1));
    expect(server.callsTo("PUT /api/v1/attendance/me/marks/{date}")[0].body).toEqual({
      statusCode: "A",
      dayValue: 0.5,
      version: 3,
    });
    expect(await screen.findByLabelText(`${TODAY}, Absent, half day, set by you`)).toBeTruthy();
  });

  it("offers the server's active statuses in server order, without the non-working one and with no code built in", async () => {
    const custom: StatusCode[] = [
      { ...statusCodes()[0], shortCode: "X", name: "Teaching day" },
      { ...statusCodes()[1], shortCode: "Y", name: "Sick leave" },
      { ...statusCodes()[5], shortCode: "Z", name: "School holiday" },
    ];
    const { server } = await openAttendanceScreen("My Attendance", { attendance: { statuses: custom } });
    await openDay(`${TODAY}, not marked`);

    await screen.findByLabelText("Teaching day");
    expect(screen.getAllByLabelText(/^(Teaching day|Sick leave|School holiday|Present|Absent)$/).map((n) => n.props.accessibilityLabel)).toEqual([
      "Teaching day",
      "Sick leave",
    ]);

    await fireEvent.press(screen.getByLabelText("Save"));
    await waitFor(() => expect(server.callsTo("PUT /api/v1/attendance/me/marks/{date}")).toHaveLength(1));
    expect(server.callsTo("PUT /api/v1/attendance/me/marks/{date}")[0].body).toMatchObject({ statusCode: "X" });
  });

  it("asks the server for the status list once, with only active codes", async () => {
    const { server } = await openAttendanceScreen("My Attendance");
    await openDay(`${TODAY}, not marked`);
    await screen.findByLabelText("Present");
    await closeSheet();
    await openDay("Sunday 4 October 2026, weekly off");
    await screen.findByLabelText("Present");

    const calls = server.callsTo("GET /api/v1/attendance/status-codes");
    expect(calls).toHaveLength(1);
    expect(calls[0].query.activeOnly).toBe("true");
  });

  it("offers Save exactly when the server says the day is the Teacher's to change", async () => {
    await openAttendanceScreen("My Attendance", {
      attendance: {
        ownMonth: (month) =>
          monthView(month, {
            overrides: {
              // Marked and the server says no (for example a locked or supervisor-owned day).
              "2026-10-01": { mark: {}, editableBy: "NONE" },
              // Marked and the server says yes.
              "2026-10-02": { mark: {}, editableBy: "SELF" },
              // Unmarked but the server says no: not offered, whatever the state.
              "2026-10-03": { editableBy: "NONE" },
            },
          }),
      },
    });

    const cases: [string, boolean][] = [
      ["Thursday 1 October 2026, Present, whole day, set by you", false],
      ["Friday 2 October 2026, Present, whole day, set by you", true],
      ["Saturday 3 October 2026, not marked", false],
      ["Sunday 4 October 2026, weekly off", true],
      [`${TODAY}, not marked`, true],
      ["Tuesday 6 October 2026, future day", false],
    ];
    for (const [label, offered] of cases) {
      await openDay(label);
      await screen.findByRole("header", { name: label.split(", ")[0] });
      if (offered) expect(await screen.findByLabelText("Save")).toBeTruthy();
      else expect(screen.queryByLabelText("Save")).toBeNull();
      await closeSheet();
    }
  });
});
