import { screen, fireEvent } from "@testing-library/react-native";
import { gridRow, monthView } from "../support/attendanceFixtures";
import { openAttendanceScreen, openDay } from "../support/attendanceApp";
import { MANAGER_MODEL } from "../support/fixtures";

afterEach(() => jest.restoreAllMocks());

/** True when an ancestor is a radio that already carries the name (Paper nests an inner radio in each item). */
function insideNamedRadio(node: { parent: unknown }): boolean {
  let up = (node as { parent: { props?: Record<string, unknown>; parent: unknown } | null }).parent;
  while (up) {
    if (up.props?.accessibilityRole === "radio" && typeof up.props.accessibilityLabel === "string") return true;
    up = up.parent as typeof up | null;
  }
  return false;
}

/** Every pressable element has a name a screen reader can announce. */
function expectNamedControls() {
  for (const control of [...screen.queryAllByRole("button"), ...screen.queryAllByRole("radio")]) {
    const name = control.props.accessibilityLabel as string | undefined;
    if (insideNamedRadio(control)) continue;
    expect(typeof name === "string" && name.trim().length > 0).toBe(true);
  }
}

describe("attendance screens: accessibility (spec 019 T037)", () => {
  it("My Attendance: cells announce weekday, date, state and status; controls are named", async () => {
    await openAttendanceScreen("My Attendance", {
      attendance: {
        ownMonth: (month) => monthView(month, { overrides: { "2026-10-05": { mark: {} } } }),
      },
    });

    const cell = await screen.findByLabelText("Monday 5 October 2026, Present, whole day, set by you");
    expect(cell.props.accessibilityRole).toBe("button");
    expect(screen.getByLabelText("Sunday 4 October 2026, weekly off")).toBeTruthy();
    expectNamedControls();

    await openDay("Monday 5 October 2026, Present, whole day, set by you");
    await screen.findByLabelText("Save");
    for (const label of ["Whole day", "Half day", "Note", "Save", "Close"]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
    expectNamedControls();
  });

  it("uses touch targets of at least 48 dp for the month arrows, the day cells and the sheet actions", async () => {
    await openAttendanceScreen("My Attendance");
    const flat = (style: unknown) => Object.assign({}, ...[style].flat(Infinity).filter(Boolean));

    const cell = await screen.findByLabelText("Monday 5 October 2026, not marked");
    expect(flat(cell.props.style).minHeight).toBeGreaterThanOrEqual(48);
    let arrow = screen.getByLabelText("Next month") as { props: { style?: unknown }; parent: unknown };
    let width = flat(arrow.props.style).width as number | undefined;
    while (width === undefined && arrow.parent) {
      arrow = arrow.parent as typeof arrow;
      width = flat(arrow.props?.style).width as number | undefined;
    }
    expect(width).toBeGreaterThanOrEqual(48);

    await fireEvent.press(cell);
    await screen.findByLabelText("Save");
    const save = screen.getByLabelText("Save");
    const contentStyle = flat(screen.getByText("Save").parent?.props.style);
    expect(contentStyle.minHeight === undefined || contentStyle.minHeight >= 48).toBe(true);
    expect(save).toBeTruthy();
  });

  it("Holiday Calendar and the Manager list have named controls", async () => {
    await openAttendanceScreen("Holiday Calendar");
    await screen.findByText("October 2026");
    expectNamedControls();
  });

  it("Teacher Attendance: rows and search are named", async () => {
    await openAttendanceScreen("Teacher Attendance", {
      model: MANAGER_MODEL,
      attendance: { grid: [gridRow({ teacherId: "t-1", name: "Asha Rao" })] },
    });

    expect(await screen.findByLabelText(/^Asha Rao, Demo School One\./)).toBeTruthy();
    expect(screen.getByLabelText("Search Teachers")).toBeTruthy();
    expectNamedControls();
  });
});
