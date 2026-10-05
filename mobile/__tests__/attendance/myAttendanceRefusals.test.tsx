import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { TEACHER_SAVE_REFUSAL_COUNT } from "../../src/attendance/refusalMessages";
import { monthView } from "../support/attendanceFixtures";
import { openAttendanceScreen, openDay } from "../support/attendanceApp";

afterEach(() => jest.restoreAllMocks());

const TODAY = "Monday 5 October 2026";

/** The 409 texts a Teacher can meet (spec 008 MarkService) and the plain wording shown for each. */
const REFUSALS: [string, string][] = [
  ["You cannot mark a date in the future.", "You cannot mark a day in the future."],
  [
    "This date is more than 3 days ago. Ask your Manager to record it.",
    "This day is more than 3 days ago. Ask your Manager to record it.",
  ],
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
    "This day was set by your Manager. Ask them to correct it.",
    "Your Manager set this day. Ask them to correct it.",
  ],
  [
    "This record was changed by someone else. Reload and try again.",
    "This day was changed by someone else while you were editing. It now shows the latest value.",
  ],
];

describe("My Attendance: refusals (spec 019 FR-004)", () => {
  it(`covers all ${TEACHER_SAVE_REFUSAL_COUNT} refusals a Teacher can meet`, () => {
    expect(REFUSALS).toHaveLength(TEACHER_SAVE_REFUSAL_COUNT);
  });

  it.each(REFUSALS)("shows plain wording for %s and leaves the day and the draft unchanged", async (reason, plain) => {
    const { attendance } = await openAttendanceScreen("My Attendance", {
      attendance: { refuseMine: () => ({ status: 409, body: { reason } }) },
    });
    await openDay(`${TODAY}, not marked`);

    await fireEvent.press(await screen.findByLabelText("Absent"));
    await fireEvent.changeText(screen.getByLabelText("Note"), "Doctor visit");
    await fireEvent.press(screen.getByLabelText("Save"));

    expect(await screen.findByText(plain)).toBeTruthy();
    expect(attendance.mine.size).toBe(0);
    // The draft is still on screen.
    expect(screen.getByRole("radio", { name: "Absent", checked: true })).toBeTruthy();
    expect(screen.getByLabelText("Note").props.value).toBe("Doctor visit");
    expect(screen.getByLabelText("Save")).toBeTruthy();
  });

  it("re-loads the month after a change made by someone else and shows the new value", async () => {
    let changed = false;
    const { server } = await openAttendanceScreen("My Attendance", {
      attendance: {
        ownMonth: (month) =>
          monthView(month, {
            overrides: changed
              ? {
                  "2026-10-05": {
                    mark: { code: "L", codeName: "Leave", category: "LEAVE", setByKind: "SUPERVISOR", setByName: "Manoj" },
                    editableBy: "NONE",
                  },
                }
              : {},
          }),
        refuseMine: () => {
          changed = true;
          return { status: 409, body: { reason: "This record was changed by someone else. Reload and try again." } };
        },
      },
    });
    await openDay(`${TODAY}, not marked`);
    await fireEvent.press(await screen.findByLabelText("Save"));

    expect(await screen.findByText(/changed by someone else/)).toBeTruthy();
    await waitFor(() => expect(server.callsTo("GET /api/v1/attendance/me")).toHaveLength(2));
    // The sheet now describes the latest value, and the cell behind it too.
    expect(await screen.findByText(/Set by Manoj/)).toBeTruthy();
    expect(screen.getByLabelText(`${TODAY}, Leave, whole day, set by Manoj`)).toBeTruthy();
  });

  it("shows who set a supervisor's mark and to ask the Manager to correct it", async () => {
    await openAttendanceScreen("My Attendance", {
      attendance: {
        ownMonth: (month) =>
          monthView(month, {
            overrides: {
              "2026-10-05": {
                mark: { code: "A", codeName: "Absent", category: "LEAVE", setByKind: "SUPERVISOR", setByName: "Manoj" },
                editableBy: "NONE",
              },
            },
          }),
      },
    });
    await openDay(`${TODAY}, Absent, whole day, set by Manoj`);

    expect(await screen.findByText(/Set by Manoj on/)).toBeTruthy();
    expect(screen.getByText(/Ask your Manager to correct it/)).toBeTruthy();
    expect(screen.queryByLabelText("Save")).toBeNull();
  });
});
