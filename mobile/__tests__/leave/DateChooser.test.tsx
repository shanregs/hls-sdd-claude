import { fireEvent, render, screen } from "@testing-library/react-native";
import { useState } from "react";
import { PaperProvider } from "react-native-paper";
import { DateChooser } from "../../src/leave/DateChooser";
import { PreviewPanel } from "../../src/leave/PreviewPanel";
import { calendar } from "../support/attendanceFixtures";
import { preview, previewDays } from "../support/leaveFixtures";

function Harness({ initial = null, onPick }: { initial?: string | null; onPick?: (d: string) => void }) {
  const [value, setValue] = useState<string | null>(initial);
  return (
    <PaperProvider>
      <DateChooser
        label="First date"
        value={value}
        onChange={(d) => {
          setValue(d);
          onPick?.(d);
        }}
      />
    </PaperProvider>
  );
}

beforeEach(() => {
  jest.spyOn(Date, "now").mockReturnValue(Date.parse("2026-10-05T04:00:00Z"));
});
afterEach(() => jest.restoreAllMocks());

describe("DateChooser", () => {
  it("shows the chosen date as DD/MM/YYYY and announces it in full", async () => {
    await render(<Harness initial="2026-10-12" />);

    expect(screen.getByText("First date: 12/10/2026")).toBeTruthy();
    expect(screen.getByLabelText("First date, Monday 12 October 2026")).toBeTruthy();
  });

  it("opens on the current month and picks a day with its full date as the name", async () => {
    const picked = jest.fn();
    await render(<Harness onPick={picked} />);

    await fireEvent.press(screen.getByLabelText("First date, not chosen"));
    expect(screen.getByText("October 2026")).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Tuesday 20 October 2026"));

    expect(picked).toHaveBeenCalledWith("2026-10-20");
    expect(screen.queryByLabelText("Tuesday 20 October 2026")).toBeNull();
  });

  it("reaches the previous, current and next year and no other", async () => {
    await render(<Harness />);
    await fireEvent.press(screen.getByLabelText("First date, not chosen"));

    await fireEvent.press(screen.getByLabelText("Choose month, October 2026"));
    expect(screen.getByLabelText("January 2025")).toBeTruthy();
    expect(screen.getByLabelText("December 2027")).toBeTruthy();
    expect(screen.queryByLabelText("December 2024")).toBeNull();
    expect(screen.queryByLabelText("January 2028")).toBeNull();
  });

  it("places no limit on the date: 40 days back is offered", async () => {
    await render(<Harness />);
    await fireEvent.press(screen.getByLabelText("First date, not chosen"));
    await fireEvent.press(screen.getByLabelText("Previous month"));
    await fireEvent.press(screen.getByLabelText("Previous month"));

    expect(screen.getByLabelText("Saturday 1 August 2026")).toBeTruthy();
  });

  it("starts the week on Sunday", async () => {
    await render(<Harness />);
    await fireEvent.press(screen.getByLabelText("First date, not chosen"));

    const headers = screen
      .getAllByText(/^(Sun|Mon|Tue|Wed|Thu|Fri|Sat)$/, { includeHiddenElements: true })
      .map((n) => n.props.children);
    expect(headers).toEqual(["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]);
  });
});

describe("PreviewPanel", () => {
  it("shows the server's total, the day list and each problem in plain wording", async () => {
    await render(
      <PaperProvider>
        <PreviewPanel
          firstDate="2026-10-02"
          lastDate="2026-10-05"
          preview={preview({
            days: previewDays(["2026-10-03", "2026-10-05"], ["2026-10-05"]),
            problems: ["Attendance is locked for a month in this range (month locked: 2026-10)."],
          })}
          calendar={calendar()}
        />
      </PaperProvider>,
    );

    expect(screen.getByText("Working days: 1.5")).toBeTruthy();
    expect(screen.getByText("Attendance for October 2026 is locked.")).toBeTruthy();
    expect(screen.getByLabelText("Friday 2 October 2026, holiday, not counted")).toBeTruthy();
    expect(screen.getByLabelText("Monday 5 October 2026, counted, half day")).toBeTruthy();
    expect(screen.getByText("05/10/2026")).toBeTruthy();
  });
});
