import { MANAGER_DARK, MANAGER_LIGHT, presentManagerDay } from "../../src/attendance/managerDayPresentation";
import type { MonthStatus } from "../../src/api/managerAttendanceApi";
import { monthDay } from "../support/managerFixtures";

/** Spec 033 T032: every status and flag has a look in both themes, announced in plain words and readable. */
const STATUSES: MonthStatus[] = [
  "WORKED",
  "FIELD",
  "NO_CHECK_OUT",
  "LEAVE",
  "HOLIDAY",
  "WEEKLY_OFF",
  "NOT_EMPLOYED",
  "PENDING",
  "MISSED",
  "ABSENT",
];

function channel(value: number): number {
  const c = value / 255;
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}
function luminance(hex: string): number {
  const n = parseInt(hex.slice(1), 16);
  return 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255);
}
function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

describe("presentManagerDay", () => {
  it.each(STATUSES)("gives %s a letter, a spoken label and colours in both themes", (status) => {
    for (const mode of ["light", "dark"] as const) {
      const look = presentManagerDay(monthDay("2026-10-06", { status }), mode);
      expect(look.letter.length).toBeGreaterThan(0);
      expect(look.label).toMatch(/^Tuesday 6 October 2026, /);
      expect(look.background).toBeTruthy();
      expect(look.textColor).toBeTruthy();
    }
  });

  it("reads a day's status, times, kind of day and flags aloud", () => {
    const look = presentManagerDay(
      monthDay("2026-10-06", {
        status: "FIELD",
        dayType: "FIELD",
        checkInAt: "2026-10-06T04:12:00Z",
        flags: ["NO_CHECK_OUT", "LATE"],
      }),
      "light",
    );

    expect(look.label).toBe("Tuesday 6 October 2026, field day, checked in 09:42, field, no check-out, late");
    expect(look.letter).toBe("F*");
  });

  it("says a past day with a check-in and no check-out once, whether the status or the flag says it", () => {
    const status = presentManagerDay(
      monthDay("2026-10-06", { status: "NO_CHECK_OUT", dayType: "FIELD", checkInAt: "2026-10-06T04:12:00Z", flags: ["NO_CHECK_OUT"] }),
      "light",
    );

    expect(status.label).toBe("Tuesday 6 October 2026, no check-out, checked in 09:42, field");
    expect(status.letter).toBe("C");
  });

  it("calls a past working day with no check-in absent", () => {
    const look = presentManagerDay(monthDay("2026-10-06", { status: "ABSENT" }), "light");

    expect(look.label).toBe("Tuesday 6 October 2026, absent");
    expect(look.letter).toBe("A");
  });

  it("marks half-day leave and says whole or half in the label", () => {
    const half = presentManagerDay(monthDay("2026-10-06", { status: "LEAVE", leave: { fraction: 0.5, unpaid: false } }), "light");
    const whole = presentManagerDay(monthDay("2026-10-06", { status: "LEAVE", leave: { fraction: 1, unpaid: true } }), "light");

    expect(half.half).toBe(true);
    expect(half.label).toContain("half day leave");
    expect(whole.half).toBe(false);
    expect(whole.label).toContain("leave");
  });

  it("never tells a day by colour alone: the statuses have different letters", () => {
    const letters = STATUSES.map((s) => presentManagerDay(monthDay("2026-10-06", { status: s }), "light").letter);
    expect(new Set(letters).size).toBe(letters.length);
  });

  it.each(STATUSES.filter((s) => s !== "PENDING"))("keeps %s text readable on its background (4.5:1) in both themes", (status) => {
    expect(contrast(MANAGER_LIGHT[status].text, MANAGER_LIGHT[status].background)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(MANAGER_DARK[status].text, MANAGER_DARK[status].background)).toBeGreaterThanOrEqual(4.5);
  });
});
