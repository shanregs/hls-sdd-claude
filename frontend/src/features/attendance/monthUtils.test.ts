import { describe, expect, it } from "vitest";
import {
  currentMonth,
  daysInMonth,
  formatDate,
  isFutureDate,
  monthLabel,
  shiftMonth,
  todayKey,
  WEEKDAY_CODES,
  WEEKDAY_LABELS,
  weekdayIndex,
} from "./monthUtils";

describe("monthUtils", () => {
  it("knows the real length of 28, 29, 30 and 31 day months", () => {
    expect(daysInMonth("2026-02")).toBe(28);
    expect(daysInMonth("2028-02")).toBe(29);
    expect(daysInMonth("2026-04")).toBe(30);
    expect(daysInMonth("2026-10")).toBe(31);
  });

  it("shifts months across year boundaries", () => {
    expect(shiftMonth("2026-12", 1)).toBe("2027-01");
    expect(shiftMonth("2026-01", -1)).toBe("2025-12");
    expect(shiftMonth("2026-10", -3)).toBe("2026-07");
  });

  it("returns Sunday-first weekday indexes", () => {
    expect(weekdayIndex("2026-10-01")).toBe(4); // Thursday
    expect(weekdayIndex("2026-10-04")).toBe(0); // Sunday
    expect(weekdayIndex("2026-10-05")).toBe(1); // Monday
    expect(weekdayIndex("2026-10-10")).toBe(6); // Saturday
  });

  it("lists the weekday labels and codes starting with Sunday", () => {
    expect(WEEKDAY_LABELS[0]).toBe("Sun");
    expect(WEEKDAY_LABELS[6]).toBe("Sat");
    expect(WEEKDAY_CODES[0]).toBe("SUN");
    expect(
      WEEKDAY_CODES.map((c) => c.slice(0, 1) + c.slice(1).toLowerCase()),
    ).toEqual(WEEKDAY_LABELS);
  });

  it("formats dates as DD/MM/YYYY and months as names", () => {
    expect(formatDate("2026-10-04")).toBe("04/10/2026");
    expect(monthLabel("2026-10")).toBe("October 2026");
  });

  it("uses the Indian date for today", () => {
    // 19:00 UTC on 3 Oct is already 4 Oct in India.
    const now = new Date("2026-10-03T19:00:00Z");
    expect(todayKey(now)).toBe("2026-10-04");
    expect(currentMonth(now)).toBe("2026-10");
  });

  it("tells future dates from past ones", () => {
    expect(isFutureDate("2026-10-05", "2026-10-04")).toBe(true);
    expect(isFutureDate("2026-10-04", "2026-10-04")).toBe(false);
  });
});
