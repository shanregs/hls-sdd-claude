import { fireEvent, render, screen } from "@testing-library/react-native";
import { PaperProvider } from "react-native-paper";
import type { DayView } from "../../src/api/attendanceApi";
import { DaySheet, type DaySheetProps } from "../../src/attendance/DaySheet";
import { mark, statusCodes } from "../support/attendanceFixtures";

const unmarked: DayView = { date: "2026-10-05", state: "UNMARKED", mark: null, editableBy: "SELF" };

async function show(over: Partial<DaySheetProps> = {}) {
  const props: DaySheetProps = {
    day: unmarked,
    viewer: "self",
    editable: true,
    statuses: statusCodes().filter((s) => s.category !== "NON_WORKING"),
    onSave: jest.fn(async () => null),
    onClose: jest.fn(),
    ...over,
  };
  await render(
    <PaperProvider>
      <DaySheet {...props} />
    </PaperProvider>,
  );
  return props;
}

describe("DaySheet", () => {
  it("shows no form when the day is not editable and says who set a supervisor's mark", async () => {
    await show({
      editable: false,
      day: {
        ...unmarked,
        state: "MARKED",
        editableBy: "NONE",
        mark: mark({ setByKind: "SUPERVISOR", setByName: "Manoj" }),
      },
    });

    expect(screen.queryByLabelText("Save")).toBeNull();
    expect(screen.queryByLabelText("Whole day")).toBeNull();
    expect(screen.getByText(/Set by Manoj on/)).toBeTruthy();
    expect(screen.getByText(/Ask your Manager to correct it/)).toBeTruthy();
  });

  it("offers only a whole day and a half day", async () => {
    await show();

    expect(screen.getAllByLabelText(/^(Whole day|Half day)$/)).toHaveLength(2);
    expect(screen.queryByLabelText(/quarter|1\.5|2/i)).toBeNull();
  });

  it("preselects the first status and saves it as a whole day", async () => {
    const props = await show();

    await fireEvent.press(screen.getByLabelText("Save"));
    expect(props.onSave).toHaveBeenCalledWith({ statusCode: "P", dayValue: 1 });
  });

  it("keeps the draft and shows the reason when the save is refused, and does not close", async () => {
    const props = await show({ onSave: jest.fn(async () => "This month is locked, so attendance cannot change.") });

    await fireEvent.press(screen.getByLabelText("Absent"));
    await fireEvent.press(screen.getByLabelText("Half day"));
    await fireEvent.changeText(screen.getByLabelText("Note"), "Fever");
    await fireEvent.press(screen.getByLabelText("Save"));

    expect(await screen.findByText("This month is locked, so attendance cannot change.")).toBeTruthy();
    expect(screen.getByRole("radio", { name: "Absent", checked: true })).toBeTruthy();
    expect(screen.getByRole("radio", { name: "Half day", checked: true })).toBeTruthy();
    expect(screen.getByLabelText("Note").props.value).toBe("Fever");
    expect(props.onClose).not.toHaveBeenCalled();
  });

  it("offers Clear only for a marked day when a clear action is given", async () => {
    const onClear = jest.fn(async () => null);
    await show({ onClear });
    expect(screen.queryByLabelText("Clear mark")).toBeNull();
  });

  it("clears a mark through the clear action", async () => {
    const onClear = jest.fn(async () => null);
    await show({ onClear, day: { ...unmarked, state: "MARKED", mark: mark() } });

    await fireEvent.press(screen.getByLabelText("Clear mark"));
    expect(onClear).toHaveBeenCalledTimes(1);
  });

  it("shows a retry when the statuses could not be loaded", async () => {
    const onRetryStatuses = jest.fn();
    await show({ statuses: null, statusesFailed: true, onRetryStatuses });

    await fireEvent.press(screen.getByLabelText("Retry statuses"));
    expect(onRetryStatuses).toHaveBeenCalled();
    expect(screen.getByLabelText("Save").props.accessibilityState.disabled).toBe(true);
  });

  it("gives every control an accessible name", async () => {
    await show({ onHistory: jest.fn() });

    for (const label of ["Present", "Absent", "Whole day", "Half day", "Note", "Save", "History", "Close"]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
  });
});
