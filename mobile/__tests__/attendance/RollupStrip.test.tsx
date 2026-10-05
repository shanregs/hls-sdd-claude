import { render, screen } from "@testing-library/react-native";
import { PaperProvider } from "react-native-paper";
import { DayHistoryList } from "../../src/attendance/DayHistoryList";
import { RollupStrip } from "../../src/attendance/RollupStrip";
import { rollup } from "../support/attendanceFixtures";

describe("RollupStrip", () => {
  it("shows the server's figures exactly as sent, fractions included", async () => {
    await render(
      <PaperProvider>
        <RollupStrip rollup={rollup({ workingDays: 21, daysWorked: 14.5, daysLeave: 2.5, unmarked: 4 })} />
      </PaperProvider>,
    );

    expect(screen.getByLabelText("Working days 21")).toBeTruthy();
    expect(screen.getByLabelText("Days worked 14.5")).toBeTruthy();
    expect(screen.getByLabelText("Leave 2.5")).toBeTruthy();
    expect(screen.getByLabelText("Unmarked 4")).toBeTruthy();
    expect(screen.queryByLabelText("Locked")).toBeNull();
  });

  it("shows Locked when the month is locked", async () => {
    await render(
      <PaperProvider>
        <RollupStrip rollup={rollup({ locked: true })} />
      </PaperProvider>,
    );

    expect(screen.getByLabelText("Locked")).toBeTruthy();
  });
});

describe("DayHistoryList", () => {
  it("says so when there are no earlier changes", async () => {
    await render(
      <PaperProvider>
        <DayHistoryList entries={[]} />
      </PaperProvider>,
    );

    expect(screen.getByText("No earlier changes for this day.")).toBeTruthy();
  });

  it("describes a create, a correction and a clear with who, when and the note", async () => {
    const base = { schoolName: "S", note: null, setByKind: "SUPERVISOR" as const, setAt: "2026-10-03T05:00:00Z" };
    await render(
      <PaperProvider>
        <DayHistoryList
          entries={[
            { ...base, action: "CLEARED", code: null, codeName: null, dayValue: null, setByName: "Manoj" },
            { ...base, action: "CORRECTED", code: "A", codeName: "Absent", dayValue: 0.5, setByName: "Manoj", note: "Fever" },
            { ...base, action: "CREATED", code: "P", codeName: "Present", dayValue: 1, setByName: "Asha" },
          ]}
        />
      </PaperProvider>,
    );

    expect(screen.getByText("Cleared: Cleared")).toBeTruthy();
    expect(screen.getByText("Corrected: Absent, half day")).toBeTruthy();
    expect(screen.getByText("Marked: Present, whole day")).toBeTruthy();
    expect(screen.getByText("Note: Fever")).toBeTruthy();
    expect(screen.getAllByText(/^by Manoj · 03\/10\/2026/)).toHaveLength(2);
  });
});
