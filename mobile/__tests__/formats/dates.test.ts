import {
  formatIsoDate,
  formatLongDate,
  formatMonth,
  parseIsoDate,
  weekdayIndex,
} from "../../src/formats/dates";

describe("date helpers", () => {
  it("names a month", () => {
    expect(formatMonth("2026-10")).toBe("October 2026");
    expect(formatMonth("2026-01")).toBe("January 2026");
    expect(formatMonth("2026-12")).toBe("December 2026");
    expect(formatMonth("2026-13")).toBe("");
    expect(formatMonth("nope")).toBe("");
  });

  it("finds the weekday, Sunday first, without any time zone", () => {
    expect(weekdayIndex("2026-10-05")).toBe(1); // Monday
    expect(weekdayIndex("2026-10-10")).toBe(6); // Saturday
    expect(weekdayIndex("2026-10-04")).toBe(0); // Sunday
  });

  it("formats a long date and DD/MM/YYYY", () => {
    expect(formatLongDate("2026-10-06")).toBe("Tuesday 6 October 2026");
    expect(formatIsoDate("2026-10-06")).toBe("06/10/2026");
    expect(formatLongDate("bad")).toBe("");
    expect(formatIsoDate("bad")).toBe("");
  });

  it("parses only well-formed dates", () => {
    expect(parseIsoDate("2026-02-28")).toEqual({ year: 2026, month: 2, day: 28 });
    expect(parseIsoDate("2026-2-28")).toBeNull();
  });
});
