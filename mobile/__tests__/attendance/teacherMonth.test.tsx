import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import type { DayHistoryEntry } from "../../src/api/attendanceApi";
import { gridRow, monthView } from "../support/attendanceFixtures";
import { closeSheet, openAttendanceScreen, openDay } from "../support/attendanceApp";
import { MANAGER_MODEL } from "../support/fixtures";

afterEach(() => jest.restoreAllMocks());

const asha = gridRow({ teacherId: "t-asha", name: "Asha Rao" });

const history: DayHistoryEntry[] = [
  {
    action: "CORRECTED",
    code: "A",
    codeName: "Absent",
    dayValue: 0.5,
    schoolName: "Demo School One",
    note: null,
    setByName: "Manoj",
    setByKind: "SUPERVISOR",
    setAt: "2026-10-03T05:00:00Z",
  },
  {
    action: "CREATED",
    code: "P",
    codeName: "Present",
    dayValue: 1,
    schoolName: "Demo School One",
    note: "Late bus",
    setByName: "Asha Rao",
    setByKind: "SELF",
    setAt: "2026-10-02T04:00:00Z",
  },
];

async function openAsha(attendance: Parameters<typeof openAttendanceScreen>[1] = {}) {
  const opened = await openAttendanceScreen("Teacher Attendance", {
    model: MANAGER_MODEL,
    ...attendance,
    attendance: {
      grid: [asha],
      teacherMonth: (id, month) =>
        monthView(month, {
          teacherId: id,
          viewer: "SUPERVISOR",
          overrides: { "2026-10-02": { mark: { setByKind: "SELF", setByName: "Asha Rao" } } },
        }),
      histories: { "t-asha/2026-10-02": history },
      ...attendance.attendance,
    },
  });
  await fireEvent.press(await screen.findByLabelText(/^Asha Rao,/));
  await screen.findByRole("header", { name: "Asha Rao" });
  return opened;
}

describe("A Teacher's month for a Manager (spec 019 US4)", () => {
  it("loads the Teacher's month and shows the grid and totals", async () => {
    const { server } = await openAsha();

    expect(await screen.findByLabelText("Friday 2 October 2026, Present, whole day, set by Asha Rao")).toBeTruthy();
    const calls = server.callsTo("GET /api/v1/attendance/teachers/{id}");
    expect(calls).toHaveLength(1);
    expect(calls[0].params.id).toBe("t-asha");
    expect(calls[0].query.month).toBe("2026-10");
    expect(screen.getByLabelText(/^Working days/)).toBeTruthy();
  });

  it("offers Save, and Clear when there is a mark, wherever the server says SUPERVISOR, weekly off included", async () => {
    await openAsha();

    await openDay("Sunday 4 October 2026, weekly off");
    expect(await screen.findByLabelText("Save")).toBeTruthy();
    expect(screen.queryByLabelText("Clear mark")).toBeNull();
    await closeSheet();

    await openDay("Friday 2 October 2026, Present, whole day, set by Asha Rao");
    expect(await screen.findByLabelText("Save")).toBeTruthy();
    expect(screen.getByLabelText("Clear mark")).toBeTruthy();
    expect(screen.getByLabelText("History")).toBeTruthy();
  });

  it("offers neither for a locked month, a day before the placement or a future day", async () => {
    await openAsha({
      attendance: {
        teacherMonth: (id, month) => monthView(month, { teacherId: id, viewer: "SUPERVISOR", placedFrom: "2026-10-03" }),
      },
    });

    for (const label of ["Thursday 1 October 2026, not placed in a school", "Tuesday 6 October 2026, future day"]) {
      await openDay(label);
      await screen.findByRole("header", { name: label.split(", ")[0] });
      expect(screen.queryByLabelText("Save")).toBeNull();
      expect(screen.queryByLabelText("Clear mark")).toBeNull();
      await closeSheet();
    }
  });

  it("offers neither when the month is locked", async () => {
    await openAsha({
      attendance: {
        teacherMonth: (id, month) => monthView(month, { teacherId: id, viewer: "SUPERVISOR", locked: true }),
      },
    });

    expect(await screen.findByLabelText("Locked")).toBeTruthy();
    await openDay("Monday 5 October 2026, not marked");
    await screen.findByRole("header", { name: "Monday 5 October 2026" });
    expect(screen.queryByLabelText("Save")).toBeNull();
  });

  it("marks a day with the right request and shows the new mark after the re-fetch", async () => {
    const { server, attendance } = await openAsha();

    await openDay("Thursday 1 October 2026, not marked");
    await fireEvent.press(await screen.findByLabelText("Absent"));
    await fireEvent.press(screen.getByLabelText("Half day"));
    await fireEvent.changeText(screen.getByLabelText("Note"), "Medical");
    await fireEvent.press(screen.getByLabelText("Save"));

    await waitFor(() => expect(attendance.teachers.size).toBe(1));
    const put = server.callsTo("PUT /api/v1/attendance/teachers/{id}/marks/{date}");
    expect(put[0].params).toEqual({ id: "t-asha", date: "2026-10-01" });
    expect(put[0].body).toEqual({ statusCode: "A", dayValue: 0.5, note: "Medical" });
    expect(await screen.findByLabelText("Thursday 1 October 2026, Absent, half day, set by Manoj")).toBeTruthy();
    expect(server.callsTo("GET /api/v1/attendance/teachers/{id}")).toHaveLength(2);
  });

  it("corrects a day sending the previous version", async () => {
    const { server } = await openAsha({
      attendance: {
        teacherMonth: (id, month) =>
          monthView(month, {
            teacherId: id,
            viewer: "SUPERVISOR",
            overrides: { "2026-10-02": { mark: { version: 4, setByKind: "SELF", setByName: "Asha Rao" } } },
          }),
      },
    });

    await openDay("Friday 2 October 2026, Present, whole day, set by Asha Rao");
    await fireEvent.press(await screen.findByLabelText("Leave"));
    await fireEvent.press(screen.getByLabelText("Save"));

    await waitFor(() =>
      expect(server.callsTo("PUT /api/v1/attendance/teachers/{id}/marks/{date}")).toHaveLength(1),
    );
    expect(server.callsTo("PUT /api/v1/attendance/teachers/{id}/marks/{date}")[0].body).toEqual({
      statusCode: "L",
      dayValue: 1,
      version: 4,
    });
  });

  it("clears a day with DELETE and the day is unmarked after the re-fetch", async () => {
    const { server } = await openAsha();

    await openDay("Friday 2 October 2026, Present, whole day, set by Asha Rao");
    await fireEvent.press(await screen.findByLabelText("Clear mark"));

    await waitFor(() => expect(server.callsTo("DELETE /api/v1/attendance/teachers/{id}/marks/{date}")).toHaveLength(1));
    expect(server.callsTo("DELETE /api/v1/attendance/teachers/{id}/marks/{date}")[0].params).toEqual({
      id: "t-asha",
      date: "2026-10-02",
    });
    expect(await screen.findByLabelText("Friday 2 October 2026, not marked")).toBeTruthy();
  });

  it("lists a day's earlier values newest first with who and when", async () => {
    await openAsha();

    await openDay("Friday 2 October 2026, Present, whole day, set by Asha Rao");
    await fireEvent.press(await screen.findByLabelText("History"));

    const entries = await screen.findAllByLabelText(/^(Corrected|Marked|Cleared): /);
    expect(entries.map((n) => n.props.accessibilityLabel)).toEqual([
      expect.stringMatching(/^Corrected: Absent, half day, by Manoj on 03\/10\/2026/),
      expect.stringMatching(/^Marked: Present, whole day, by Asha Rao on 02\/10\/2026/),
    ]);
    expect(screen.getByText("Note: Late bus")).toBeTruthy();
  });

  it("returns to the list with the month and search kept when the back button is used", async () => {
    await openAsha();
    await fireEvent.press(screen.getByLabelText("Previous month"));
    await screen.findByText("September 2026");

    await fireEvent.press(screen.getByLabelText("Back to Teachers"));

    expect(await screen.findByText("September 2026")).toBeTruthy();
    expect(screen.getByLabelText("Search Teachers")).toBeTruthy();
    expect(screen.getByText("Asha Rao")).toBeTruthy();
  });
});
