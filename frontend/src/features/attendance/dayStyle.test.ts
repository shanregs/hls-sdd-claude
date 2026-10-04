import { describe, expect, it } from "vitest";
import { dayAppearance } from "./dayStyle";

describe("dayAppearance", () => {
  it("labels a weekly off day with its weekday", () => {
    expect(dayAppearance({ date: "2026-10-04", state: "WEEKLY_OFF" })).toEqual({
      kind: "off",
      text: "Sun",
    });
    expect(dayAppearance({ date: "2026-10-03", state: "WEEKLY_OFF" })).toEqual({
      kind: "off",
      text: "Sat",
    });
  });

  it("labels a non-working date H as a holiday", () => {
    expect(dayAppearance({ date: "2026-10-02", state: "NON_WORKING" })).toEqual(
      { kind: "holiday", text: "H" },
    );
  });

  it("treats a mark in a non-working status as a holiday and keeps its code", () => {
    expect(
      dayAppearance({
        date: "2026-10-05",
        state: "MARKED",
        code: "H",
        category: "NON_WORKING",
        dayValue: 1,
      }),
    ).toEqual({ kind: "holiday", text: "H" });
  });

  it("colours leave and absent marks as leave whatever their code letter", () => {
    for (const code of ["L", "A", "SICK"]) {
      expect(
        dayAppearance({
          date: "2026-10-05",
          state: "MARKED",
          code,
          category: "LEAVE",
          dayValue: 1,
        }).kind,
      ).toBe("leave");
    }
  });

  it("keeps worked marks plain and marks a half day", () => {
    expect(
      dayAppearance({
        date: "2026-10-05",
        state: "MARKED",
        code: "P",
        category: "WORKED",
        dayValue: 0.5,
      }),
    ).toEqual({ kind: "plain", text: "P½" });
  });

  it("leaves unmarked, future and not-placed days plain and empty", () => {
    for (const state of ["UNMARKED", "FUTURE", "NOT_PLACED"]) {
      expect(dayAppearance({ date: "2026-10-05", state })).toEqual({
        kind: "plain",
        text: "",
      });
    }
  });
});
