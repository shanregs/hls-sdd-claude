import { fireEvent, render, screen } from "@testing-library/react-native";
import { PaperProvider } from "react-native-paper";
import { MonthGrid } from "../../src/attendance/MonthGrid";
import { presentManagerDay } from "../../src/attendance/managerDayPresentation";
import { monthDay } from "../support/managerFixtures";

/** Spec 033 T030: the month grid of spec 019 serves Manager days too, with the same layout rules. */
const days = [
  monthDay("2026-10-01", { status: "WORKED", checkInAt: "2026-10-01T04:12:00Z", dayType: "OFFICE" }),
  monthDay("2026-10-02", { status: "FIELD", checkInAt: "2026-10-02T04:12:00Z", dayType: "FIELD" }),
  monthDay("2026-10-03", { status: "WEEKLY_OFF" }),
];
const wrap = (node: React.ReactElement) => render(<PaperProvider>{node}</PaperProvider>);

describe("MonthGrid with Manager days", () => {
  it("shows a cell per day with the Manager look and a spoken label, in a seven-column week", async () => {
    await wrap(<MonthGrid days={days} present={presentManagerDay} fontScale={1} />);

    expect(screen.getByLabelText("Thursday 1 October 2026, worked, checked in 09:42, office")).toBeTruthy();
    expect(screen.getByLabelText("Friday 2 October 2026, field day, checked in 09:42, field")).toBeTruthy();
    expect(screen.getByLabelText("Saturday 3 October 2026, weekly off")).toBeTruthy();
    // the weekday names are hidden from screen readers, so look for them including hidden elements
    expect(screen.getByText("Sun", { includeHiddenElements: true })).toBeTruthy();
    expect(screen.getByText("W")).toBeTruthy();
  });

  it("lists the days one per row at large text, and each row is pressable", async () => {
    const onSelect = jest.fn();
    await wrap(<MonthGrid days={days} present={presentManagerDay} onSelect={onSelect} fontScale={1.3} />);

    expect(screen.queryByText("Sun", { includeHiddenElements: true })).toBeNull();
    await fireEvent.press(screen.getByLabelText("Friday 2 October 2026, field day, checked in 09:42, field"));

    expect(onSelect).toHaveBeenCalledWith(days[1]);
  });

  it("shows the days but makes none pressable without onSelect", async () => {
    await wrap(<MonthGrid days={days} present={presentManagerDay} fontScale={1} />);

    expect(screen.getByLabelText("Saturday 3 October 2026, weekly off").props.accessibilityRole).toBe("text");
  });
});
