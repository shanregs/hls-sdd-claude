import type { DayView } from "../../src/api/attendanceApi";
import { presentDay } from "../../src/attendance/dayPresentation";

const mark = (over: Partial<NonNullable<DayView["mark"]>> = {}): NonNullable<DayView["mark"]> => ({
  date: "2026-10-05",
  code: "P",
  codeName: "Present",
  category: "WORKED",
  dayValue: 1,
  schoolId: "s1",
  schoolName: "Demo School One",
  setByKind: "SELF",
  setByUserId: "u1",
  setByName: "Tara Teacher",
  setAt: "2026-10-05T04:00:00Z",
  note: null,
  version: 1,
  ...over,
});

const day = (state: DayView["state"], over: Partial<DayView> = {}): DayView => ({
  date: "2026-10-05",
  state,
  mark: null,
  editableBy: "NONE",
  ...over,
});

describe("presentDay", () => {
  it("announces a whole-day mark set by the viewer", () => {
    const look = presentDay(day("MARKED", { mark: mark() }), "light", "self");
    expect(look.letter).toBe("P");
    expect(look.half).toBe(false);
    expect(look.label).toBe("Monday 5 October 2026, Present, whole day, set by you");
  });

  it("marks a half day and says so", () => {
    const look = presentDay(day("MARKED", { mark: mark({ dayValue: 0.5 }) }), "light", "self");
    expect(look.half).toBe(true);
    expect(look.label).toContain("half day");
  });

  it("names who set a supervisor mark, for the Teacher and for a Manager", () => {
    const supervisorMark = mark({ setByKind: "SUPERVISOR", setByName: "Manoj Manager" });
    expect(presentDay(day("MARKED", { mark: supervisorMark }), "light", "self").label).toContain("set by Manoj Manager");
    expect(presentDay(day("MARKED", { mark: supervisorMark }), "light", "supervisor").label).toContain(
      "set by Manoj Manager",
    );
    expect(presentDay(day("MARKED", { mark: mark() }), "light", "supervisor").label).toContain("set by Tara Teacher");
  });

  it("colours leave, training and worked marks differently", () => {
    const worked = presentDay(day("MARKED", { mark: mark() }), "light", "self").background;
    const leave = presentDay(day("MARKED", { mark: mark({ code: "A", codeName: "Absent", category: "LEAVE" }) }), "light", "self").background;
    const training = presentDay(day("MARKED", { mark: mark({ code: "T", codeName: "Training", category: "TRAINING" }) }), "light", "self").background;
    expect(new Set([worked, leave, training]).size).toBe(3);
  });

  it.each([
    ["UNMARKED", "–", "not marked"],
    ["NOT_PLACED", "·", "not placed in a school"],
    ["WEEKLY_OFF", "Mon", "weekly off"],
    ["NON_WORKING", "H", "holiday"],
    ["FUTURE", "", "future day"],
  ] as const)("shows %s", (state, letter, spoken) => {
    const look = presentDay(day(state), "light", "self");
    expect(look.letter).toBe(letter);
    expect(look.label).toBe(`Monday 5 October 2026, ${spoken}`);
  });

  it("has distinct light and dark colours with readable text for every state", () => {
    for (const state of ["UNMARKED", "NOT_PLACED", "WEEKLY_OFF", "NON_WORKING"] as const) {
      const light = presentDay(day(state), "light", "self");
      const dark = presentDay(day(state), "dark", "self");
      expect(light.textColor).not.toBe(dark.textColor);
      expect(light.borderColor).not.toBe(dark.borderColor);
    }
    const dark = presentDay(day("MARKED", { mark: mark() }), "dark", "self");
    expect(dark.background).not.toBe(presentDay(day("MARKED", { mark: mark() }), "light", "self").background);
  });

  it("falls back to unmarked when a marked day carries no mark", () => {
    expect(presentDay(day("MARKED"), "light", "self").label).toContain("not marked");
  });
});
