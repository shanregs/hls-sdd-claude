import { monthBounds } from "../../src/attendance/monthRange";
import { businessToday, recordServerDate, resetServerClock } from "../../src/attendance/serverClock";

/** Spec 034 T010: today's date in the business time zone, and the first and last day of a month. */
afterEach(() => {
  resetServerClock();
  jest.restoreAllMocks();
});

describe("businessToday", () => {
  it("is already the next day in India when it is evening in UTC", () => {
    expect(businessToday(new Date("2026-12-31T20:00:00Z"))).toBe("2027-01-01");
    expect(businessToday(new Date("2026-10-05T04:00:00Z"))).toBe("2026-10-05");
  });

  it("follows the server clock, not a wrong phone clock", () => {
    jest.spyOn(Date, "now").mockReturnValue(new Date("2026-10-09T04:00:00Z").getTime());
    recordServerDate(new Date("2026-10-05T04:00:00Z").toUTCString());

    expect(businessToday()).toBe("2026-10-05");
  });
});

describe("monthBounds", () => {
  it.each([
    ["2026-10", "2026-10-01", "2026-10-31"],
    ["2026-02", "2026-02-01", "2026-02-28"],
    ["2028-02", "2028-02-01", "2028-02-29"],
    ["2026-11", "2026-11-01", "2026-11-30"],
  ])("gives %s as %s to %s", (month, from, to) => {
    expect(monthBounds(month)).toEqual({ from, to });
  });
});
