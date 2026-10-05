import { allowedMonths, businessYearMonth, currentMonth, shiftMonth } from "../../src/attendance/monthRange";

describe("business month", () => {
  it("uses India time: 31 December 23:00 UTC is already January", () => {
    expect(currentMonth(new Date("2026-12-31T23:00:00Z"))).toBe("2027-01");
    expect(currentMonth(new Date("2026-12-31T18:00:00Z"))).toBe("2026-12");
    expect(businessYearMonth(new Date("2026-10-04T10:00:00Z"))).toEqual({ year: 2026, month: 10 });
  });

  it("is October for a moment that is still September in UTC", () => {
    expect(currentMonth(new Date("2026-09-30T19:00:00Z"))).toBe("2026-10");
  });
});

describe("allowed months", () => {
  const now = new Date("2026-10-04T10:00:00Z");

  it("offers January to December of the current year only", () => {
    const months = allowedMonths("current", now);
    expect(months).toHaveLength(12);
    expect(months[0]).toBe("2026-01");
    expect(months[11]).toBe("2026-12");
    expect(months.some((m) => !m.startsWith("2026-"))).toBe(false);
  });

  it("history also offers the whole previous year, and nothing older", () => {
    const months = allowedMonths("history", now);
    expect(months).toHaveLength(24);
    expect(months[0]).toBe("2025-01");
    expect(months[23]).toBe("2026-12");
    expect(months.some((m) => m.startsWith("2024-") || m.startsWith("2027-"))).toBe(false);
  });

  it("follows the business year at the turn of the year", () => {
    expect(allowedMonths("current", new Date("2026-12-31T23:00:00Z"))[0]).toBe("2027-01");
  });
});

describe("shiftMonth", () => {
  const months = allowedMonths("current", new Date("2026-10-04T10:00:00Z"));

  it("moves by one and stops at both ends", () => {
    expect(shiftMonth("2026-10", -1, months)).toBe("2026-09");
    expect(shiftMonth("2026-10", 1, months)).toBe("2026-11");
    expect(shiftMonth("2026-01", -1, months)).toBeNull();
    expect(shiftMonth("2026-12", 1, months)).toBeNull();
  });

  it("refuses a month outside the range", () => {
    expect(shiftMonth("2025-12", 1, months)).toBeNull();
  });
});

describe("the leave range (spec 020)", () => {
  const now = new Date("2026-10-05T04:00:00Z");

  it("offers the previous, current and next year and nothing else", () => {
    const months = allowedMonths("leave", now);
    expect(months).toHaveLength(36);
    expect(months[0]).toBe("2025-01");
    expect(months[35]).toBe("2027-12");
    expect(months).not.toContain("2024-12");
    expect(months).not.toContain("2028-01");
  });

  it("uses the business time zone: 31 December 23:00 UTC is already January in India", () => {
    const months = allowedMonths("leave", new Date("2026-12-31T23:00:00Z"));
    expect(months[0]).toBe("2026-01");
    expect(months[35]).toBe("2028-12");
  });

  it("clamps at both ends", () => {
    const months = allowedMonths("leave", now);
    expect(shiftMonth("2025-01", -1, months)).toBeNull();
    expect(shiftMonth("2027-12", 1, months)).toBeNull();
    expect(shiftMonth("2026-12", 1, months)).toBe("2027-01");
  });
});
