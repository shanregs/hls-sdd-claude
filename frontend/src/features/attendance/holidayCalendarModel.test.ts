import { describe, expect, it } from "vitest";
import {
  holidaysIn,
  kindOf,
  monthsOfYear,
  monthWeeks,
} from "./holidayCalendarModel";

const FACTS = {
  defaultWeeklyOff: ["SUN"],
  nonWorkingDates: [
    { date: "2026-10-19", description: "Ayudha Pooja" },
    { date: "2026-10-02", description: "Gandhi Jayanthi" },
    { date: "2026-11-08", description: "Deepavali" },
  ],
};

describe("holidayCalendarModel", () => {
  it("marks holidays, weekly off days and plain days", () => {
    expect(kindOf("2026-10-02", FACTS)).toBe("holiday");
    expect(kindOf("2026-10-04", FACTS)).toBe("off"); // a Sunday
    expect(kindOf("2026-10-05", FACTS)).toBe("plain");
  });

  it("shows a holiday that falls on a weekly off day as a holiday", () => {
    // 08/11/2026 is a Sunday.
    expect(kindOf("2026-11-08", FACTS)).toBe("holiday");
  });

  it("lists a period's holidays oldest first", () => {
    expect(holidaysIn("2026-10", FACTS).map((h) => h.description)).toEqual([
      "Gandhi Jayanthi",
      "Ayudha Pooja",
    ]);
    expect(holidaysIn("2026", FACTS)).toHaveLength(3);
    expect(holidaysIn("2027", FACTS)).toEqual([]);
  });

  it("lays a month out in Monday-first weeks padded with blanks", () => {
    const weeks = monthWeeks("2026-10"); // 01/10/2026 is a Thursday
    expect(weeks.every((w) => w.length === 7)).toBe(true);
    expect(weeks[0].slice(0, 4)).toEqual([null, null, null, "2026-10-01"]);
    expect(weeks.flat().filter(Boolean)).toHaveLength(31);
  });

  it("gives the twelve months of a year", () => {
    const months = monthsOfYear(2026);
    expect(months).toHaveLength(12);
    expect(months[0]).toBe("2026-01");
    expect(months[11]).toBe("2026-12");
  });
});
