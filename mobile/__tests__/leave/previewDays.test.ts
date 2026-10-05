import { datesInRange, previewDayList } from "../../src/leave/previewDays";
import { calendar } from "../support/attendanceFixtures";
import { preview, previewDays } from "../support/leaveFixtures";

describe("datesInRange", () => {
  it("lists every date inclusive, across a month end", () => {
    expect(datesInRange("2026-10-30", "2026-11-02")).toEqual(["2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02"]);
    expect(datesInRange("2026-10-05", "2026-10-05")).toEqual(["2026-10-05"]);
    expect(datesInRange("2026-10-06", "2026-10-05")).toEqual([]);
  });
});

describe("previewDayList", () => {
  const range = ["2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05"];

  it("marks counted days with their value and labels the others from the calendar only", () => {
    // 1 Oct unexplained (before placement), 2 Oct holiday (Gandhi Jayanti), 3 and 5 Oct counted, 4 Oct Sunday.
    const list = previewDayList(
      "2026-10-01",
      "2026-10-05",
      preview({ days: previewDays(["2026-10-03", "2026-10-05"], ["2026-10-05"]) }),
      calendar(),
    );
    expect(list.map((d) => d.date)).toEqual(range);
    expect(list.map((d) => d.kind)).toEqual(["NOT_COUNTED", "HOLIDAY", "COUNTED", "WEEKLY_OFF", "COUNTED"]);
    expect(list[2].label).toBe("Counted, whole day");
    expect(list[4].label).toBe("Counted, half day");
    expect(list[0].spoken).toBe("Thursday 1 October 2026, not counted");
    expect(list[1].spoken).toBe("Friday 2 October 2026, holiday, not counted");
  });

  it("calls a holiday a holiday even when it falls on a weekly off", () => {
    const list = previewDayList("2026-11-08", "2026-11-08", preview({ days: [] }), calendar());
    expect(list[0].kind).toBe("HOLIDAY");
  });

  it("reads Not counted for every uncounted date when the calendar is not available", () => {
    const list = previewDayList("2026-10-02", "2026-10-04", preview({ days: [] }), null);
    expect(list.map((d) => d.kind)).toEqual(["NOT_COUNTED", "NOT_COUNTED", "NOT_COUNTED"]);
  });

  it("never works out the total itself", () => {
    const list = previewDayList("2026-10-03", "2026-10-05", preview({ days: previewDays(["2026-10-03"]), workingDays: 9 }), calendar());
    expect(list.filter((d) => d.kind === "COUNTED")).toHaveLength(1);
  });
});
