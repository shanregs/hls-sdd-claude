import { describe, expect, it } from "vitest";
import {
  formatDate,
  formatIndianNumber,
  formatPercent,
  formatRupees,
  formatValue,
} from "../format";

describe("dashboard formats (spec 028)", () => {
  it("groups rupees the Indian way", () => {
    expect(formatRupees(125000)).toBe("₹1,25,000");
    expect(formatRupees(999)).toBe("₹999");
    expect(formatRupees(1000)).toBe("₹1,000");
    expect(formatRupees(12345678)).toBe("₹1,23,45,678");
    expect(formatRupees(0)).toBe("₹0");
  });

  it("groups counts and keeps a negative sign", () => {
    expect(formatIndianNumber(100000)).toBe("1,00,000");
    expect(formatIndianNumber(-2500)).toBe("-2,500");
  });

  it("writes dates as DD/MM/YYYY from an ISO date or timestamp", () => {
    expect(formatDate("2026-10-21")).toBe("21/10/2026");
    expect(formatDate("2026-01-05T06:30:12Z")).toBe("05/01/2026");
  });

  it("writes a percent with at most one decimal", () => {
    expect(formatPercent(62.5)).toBe("62.5%");
    expect(formatPercent(40)).toBe("40%");
    expect(formatPercent(33.333)).toBe("33.3%");
  });

  it("formats a value by its unit", () => {
    expect(
      formatValue({ label: "a", value: 150000, unit: "RUPEES", link: null }),
    ).toBe("₹1,50,000");
    expect(
      formatValue({ label: "a", value: 7, unit: "COUNT", link: null }),
    ).toBe("7");
    expect(
      formatValue({ label: "a", value: "3 of 4", unit: "TEXT", link: null }),
    ).toBe("3 of 4");
  });
});
