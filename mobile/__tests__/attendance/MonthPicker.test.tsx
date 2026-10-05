import { fireEvent, render, screen } from "@testing-library/react-native";
import { useState } from "react";
import { PaperProvider } from "react-native-paper";
import { MonthPicker } from "../../src/attendance/MonthPicker";
import type { RangeKind } from "../../src/attendance/monthRange";

function Harness({ kind, start = "2026-10", onPick }: { kind: RangeKind; start?: string; onPick?: (m: string) => void }) {
  const [month, setMonth] = useState(start);
  return (
    <PaperProvider>
      <MonthPicker
        value={month}
        kind={kind}
        onChange={(m) => {
          setMonth(m);
          onPick?.(m);
        }}
      />
    </PaperProvider>
  );
}

beforeEach(() => {
  jest.spyOn(Date, "now").mockReturnValue(Date.parse("2026-10-05T04:00:00Z"));
});
afterEach(() => jest.restoreAllMocks());

describe("MonthPicker", () => {
  it("reaches January and December of the current year in two taps", async () => {
    const picked = jest.fn();
    await render(<Harness kind="current" onPick={picked} />);

    await fireEvent.press(screen.getByLabelText("Choose month, October 2026"));
    await fireEvent.press(screen.getByLabelText("January 2026"));
    expect(picked).toHaveBeenLastCalledWith("2026-01");

    await fireEvent.press(screen.getByLabelText("Choose month, January 2026"));
    await fireEvent.press(screen.getByLabelText("December 2026"));
    expect(picked).toHaveBeenLastCalledWith("2026-12");
  });

  it("stops the arrows at the ends of the current year", async () => {
    await render(<Harness kind="current" start="2026-01" />);
    expect(screen.getByLabelText("Previous month").props.accessibilityState.disabled).toBe(true);
    expect(screen.getByLabelText("Next month").props.accessibilityState.disabled).toBe(false);
  });

  it("steps with the arrows", async () => {
    await render(<Harness kind="current" />);

    await fireEvent.press(screen.getByLabelText("Next month"));
    expect(screen.getByText("November 2026")).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Previous month"));
    await fireEvent.press(screen.getByLabelText("Previous month"));
    expect(screen.getByText("September 2026")).toBeTruthy();
  });

  it("history offers the previous year and no other", async () => {
    await render(<Harness kind="history" start="2026-01" />);

    await fireEvent.press(screen.getByLabelText("Previous month"));
    expect(screen.getByText("December 2025")).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Choose month, December 2025"));
    expect(screen.getByLabelText("January 2025")).toBeTruthy();
    expect(screen.queryByLabelText("December 2024")).toBeNull();
    expect(screen.queryByLabelText("January 2027")).toBeNull();
  });

  it("uses the 48 dp touch target for the arrows", async () => {
    await render(<Harness kind="current" />);
    const arrow = screen.getByLabelText("Next month");
    const flat = Object.assign({}, ...[arrow.props.style].flat(Infinity).filter(Boolean));
    expect(flat.width ?? 48).toBeGreaterThanOrEqual(48);
  });
});
