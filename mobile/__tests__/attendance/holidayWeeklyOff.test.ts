import { calendarDays, holidaysOfMonth, holidaysOfYear, weeklyOffFor } from "../../src/attendance/holidayModel";
import { calendar } from "../support/attendanceFixtures";

const withOverride = () =>
  calendar({
    schoolOverrides: [{ schoolId: "school-2", schoolName: "Demo School Two", weeklyOff: ["FRI"] }],
  });

const stateOn = (days: ReturnType<typeof calendarDays>, date: string) => days.find((d) => d.date === date)?.state;

describe("weekly offs on the Holiday Calendar (spec 019 research §Holiday)", () => {
  it("uses the default weekly off when the calendar has no override", () => {
    expect(weeklyOffFor(calendar())).toEqual(["SUN"]);
    const days = calendarDays(calendar(), "2026-10");
    expect(["2026-10-04", "2026-10-11", "2026-10-18", "2026-10-25"].map((d) => stateOn(days, d))).toEqual([
      "WEEKLY_OFF",
      "WEEKLY_OFF",
      "WEEKLY_OFF",
      "WEEKLY_OFF",
    ]);
    expect(stateOn(days, "2026-10-09")).toBe("UNMARKED");
  });

  it("uses a School's own weekly off when the calendar lists one for that School", () => {
    expect(weeklyOffFor(withOverride(), "school-2")).toEqual(["FRI"]);
    const days = calendarDays(withOverride(), "2026-10", "school-2");
    expect(stateOn(days, "2026-10-09")).toBe("WEEKLY_OFF");
    expect(stateOn(days, "2026-10-04")).toBe("UNMARKED");
  });

  it("falls back to the default for a School without an override or an unknown School", () => {
    expect(weeklyOffFor(withOverride(), "school-1")).toEqual(["SUN"]);
    expect(weeklyOffFor(withOverride(), null)).toEqual(["SUN"]);
    expect(weeklyOffFor(withOverride(), undefined)).toEqual(["SUN"]);
  });

  it("marks a holiday as non-working even on a weekly-off day", () => {
    const days = calendarDays(calendar(), "2026-11");
    // 8 November 2026 is a Sunday and Deepavali.
    expect(stateOn(days, "2026-11-08")).toBe("NON_WORKING");
    expect(stateOn(days, "2026-11-01")).toBe("WEEKLY_OFF");
  });

  it("has one entry per day of the month", () => {
    expect(calendarDays(calendar(), "2026-02")).toHaveLength(28);
    expect(calendarDays(calendar(), "2028-02")).toHaveLength(29);
    expect(calendarDays(calendar(), "2026-10")).toHaveLength(31);
  });

  it("lists holidays by month and by year in date order", () => {
    const unordered = calendar({
      nonWorkingDates: [
        { date: "2026-12-25", description: "Christmas" },
        { date: "2026-01-26", description: "Republic Day" },
        { date: "2025-12-25", description: "Christmas last year" },
        { date: "2026-10-20", description: "Ayudha Puja" },
        { date: "2026-10-02", description: "Gandhi Jayanti" },
      ],
    });
    expect(holidaysOfMonth(unordered, "2026-10").map((h) => h.description)).toEqual(["Gandhi Jayanti", "Ayudha Puja"]);
    expect(holidaysOfYear(unordered, 2026).map((h) => h.description)).toEqual([
      "Republic Day",
      "Gandhi Jayanti",
      "Ayudha Puja",
      "Christmas",
    ]);
  });
});
