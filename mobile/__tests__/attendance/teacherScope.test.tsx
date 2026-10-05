import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { MANAGER_SAVE_REFUSAL_COUNT } from "../../src/attendance/refusalMessages";
import { gridRow, monthView } from "../support/attendanceFixtures";
import { openAttendanceScreen, openDay } from "../support/attendanceApp";
import { MANAGER_MODEL } from "../support/fixtures";

afterEach(() => jest.restoreAllMocks());

const asha = gridRow({ teacherId: "t-asha", name: "Asha Rao" });

/** The 409 texts a Manager can meet when saving, and the plain wording shown for each. */
const REFUSALS: [string, string][] = [
  ["You cannot mark a date in the future.", "You cannot mark a day in the future."],
  [
    "This month is locked. Attendance can only change after it is reopened.",
    "This month is locked, so attendance cannot change. It can change only after your Admin reopens it.",
  ],
  [
    "Attendance needs a School placement on that date.",
    "You were not placed in a school on this date, so it cannot be marked.",
  ],
  [
    "This Teacher has exited, so attendance cannot be marked on or after the exit date.",
    "This Teacher has left, so attendance cannot be marked on or after the exit date.",
  ],
  ["The status Absent is no longer in use.", "That status is no longer in use. Choose another status."],
  [
    "This record was changed by someone else. Reload and try again.",
    "This day was changed by someone else while you were editing. It now shows the latest value.",
  ],
];

const open = (extra: Parameters<typeof openAttendanceScreen>[1] = {}) =>
  openAttendanceScreen("Teacher Attendance", {
    model: MANAGER_MODEL,
    ...extra,
    attendance: { grid: [asha], ...extra.attendance },
  });

describe("A Manager sees only their own Teachers (spec 019 FR-014)", () => {
  it("shows Not found, and nothing about the Teacher, when the server does not return one", async () => {
    await open({
      attendance: {
        teacherMonth: () => ({ status: 404, body: { message: "Teacher Asha Rao belongs to another Manager." } }),
      },
    });
    await fireEvent.press(await screen.findByLabelText(/^Asha Rao,/));

    expect(await screen.findByText("Not found")).toBeTruthy();
    expect(screen.queryByText(/another Manager/)).toBeNull();
    expect(screen.queryByLabelText(/^Working days/)).toBeNull();
    expect(screen.queryByLabelText(/October 2026/)).toBeTruthy(); // the month picker only
  });

  it("only ever asks for a Teacher that the server listed", async () => {
    const { server } = await open();
    await fireEvent.press(await screen.findByLabelText(/^Asha Rao,/));
    await screen.findByLabelText(/^Working days/);
    await fireEvent.press(screen.getByLabelText("Previous month"));
    await screen.findByText("September 2026");

    const ids = server.callsTo("GET /api/v1/attendance/teachers/{id}").map((c) => c.params.id);
    expect(ids.length).toBeGreaterThan(1);
    expect(new Set(ids)).toEqual(new Set(["t-asha"]));
  });

  it("never shows a Teacher id or name that was not in the server's list", async () => {
    await open({
      attendance: { grid: [asha], teacherMonth: (id, month) => monthView(month, { teacherId: id, name: "Asha Rao", viewer: "SUPERVISOR" }) },
    });

    expect(await screen.findByText("Asha Rao")).toBeTruthy();
    expect(screen.queryByText("Chitra Menon")).toBeNull();
  });

  it(`covers all ${MANAGER_SAVE_REFUSAL_COUNT} refusals a Manager can meet`, () => {
    expect(REFUSALS).toHaveLength(MANAGER_SAVE_REFUSAL_COUNT);
  });

  it.each(REFUSALS)("%s: shows plain wording, keeps the draft and leaves the day unchanged", async (reason, plain) => {
    const { attendance } = await open({
      attendance: { refuseTeacher: () => ({ status: 409, body: { reason } }) },
    });
    await fireEvent.press(await screen.findByLabelText(/^Asha Rao,/));
    await openDay("Thursday 1 October 2026, not marked");

    await fireEvent.press(await screen.findByLabelText("Leave"));
    await fireEvent.press(screen.getByLabelText("Save"));

    expect(await screen.findByText(plain)).toBeTruthy();
    expect(attendance.teachers.size).toBe(0);
    expect(screen.getByRole("radio", { name: "Leave", checked: true })).toBeTruthy();
    await waitFor(() => expect(screen.getByLabelText("Save")).toBeTruthy());
  });

  it("refuses clearing in a locked month with plain wording and keeps the mark", async () => {
    const reason = "This month is locked. Attendance can only change after it is reopened.";
    const { attendance } = await open({
      attendance: {
        refuseTeacher: () => ({ status: 409, body: { reason } }),
        teacherMonth: (id, month) =>
          monthView(month, {
            teacherId: id,
            viewer: "SUPERVISOR",
            overrides: { "2026-10-02": { mark: { setByKind: "SELF", setByName: "Asha Rao" } } },
          }),
      },
    });
    await fireEvent.press(await screen.findByLabelText(/^Asha Rao,/));
    await openDay("Friday 2 October 2026, Present, whole day, set by Asha Rao");
    await fireEvent.press(await screen.findByLabelText("Clear mark"));

    expect(await screen.findByText(/This month is locked/)).toBeTruthy();
    expect(attendance.cleared).toHaveLength(0);
    expect(screen.getByLabelText("Friday 2 October 2026, Present, whole day, set by Asha Rao")).toBeTruthy();
  });
});
