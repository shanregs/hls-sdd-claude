import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { DayView } from "./attendanceApi";
import { MonthCalendar } from "./MonthCalendar";

// 2026-10-04 is a Sunday, 2026-10-05 a Monday.
const SUNDAY: DayView = {
  date: "2026-10-04",
  state: "WEEKLY_OFF",
  mark: null,
  editableBy: "NONE",
};
const MONDAY: DayView = {
  date: "2026-10-05",
  state: "UNMARKED",
  mark: null,
  editableBy: "NONE",
};

describe("MonthCalendar", () => {
  it("shows a weekly off day as its weekday only, without a repeated caption", () => {
    render(<MonthCalendar days={[SUNDAY, MONDAY]} locked={false} />);

    const cell = screen.getByRole("group", { name: /Weekly off, Sun/ });
    expect(within(cell).getByText("Sun")).toBeInTheDocument();
    expect(screen.queryByText("Weekly off, Sun")).not.toBeInTheDocument();
  });

  it("still describes an unmarked day in words", () => {
    render(<MonthCalendar days={[SUNDAY, MONDAY]} locked={false} />);

    expect(screen.getByText("Unmarked")).toBeInTheDocument();
  });
});
