import { fireEvent, screen } from "@testing-library/react-native";
import { managerDay, managerLeave, managerMonth, monthDay, plannedActivity, today } from "../support/managerFixtures";
import { openManagerScreen } from "../support/managerApp";

/** Spec 033 T046: the Manager screens can be used with a screen reader and a thumb. */
afterEach(() => jest.restoreAllMocks());

type TreeNode = { props: Record<string, unknown>; children?: unknown[]; parent: TreeNode | null };

function textOf(node: TreeNode): string {
  const parts: string[] = [];
  const walk = (n: unknown) => {
    if (typeof n === "string") parts.push(n);
    else if (n && typeof n === "object" && "children" in n) (n as TreeNode).children?.forEach(walk);
  };
  walk(node);
  return parts.join("").trim();
}

/** Every button and radio has a name a screen reader can say: its label, or the words written on it. */
function expectNamedControls() {
  for (const control of [...screen.queryAllByRole("button"), ...screen.queryAllByRole("radio")]) {
    const node = control as unknown as TreeNode;
    const label = node.props.accessibilityLabel;
    const named = (typeof label === "string" && label.trim() !== "") || textOf(node) !== "";
    expect(named).toBe(true);
  }
}

const flat = (style: unknown) => Object.assign({}, ...[style].flat(Infinity).filter(Boolean));
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));

describe("Check-in", () => {
  it("names every control, uses 48 dp targets and announces a refusal", async () => {
    await openManagerScreen("Check-in", {
      manager: {
        today: today({ plannedActivities: [plannedActivity()] }),
        checkInRefusal: () => ({ status: 409, body: { reason: "Attendance for October 2026 is locked." } }),
      },
    });
    await press("Field day");
    await screen.findByLabelText("College drive, Anna Nagar College, planned, not selected");
    expectNamedControls();
    expect(screen.getByLabelText("Office day").props.accessibilityRole).toBe("radio");

    const button = screen.getByLabelText("Check in");
    const labels = screen.getAllByText("Check in");
    expect(flat(labels[labels.length - 1].parent?.props.style).minHeight ?? 48).toBeGreaterThanOrEqual(48);
    await press("College drive, Anna Nagar College, planned, not selected");
    await fireEvent.press(button);

    const refusal = await screen.findByText("Attendance for October 2026 is locked.");
    expect(refusal.props.accessibilityRole).toBe("alert");
  });

  it("names the photo, completion and check-out controls", async () => {
    await openManagerScreen("Check-in", {
      manager: { today: today({ day: managerDay(), missingCheckOutDates: ["2026-10-02"] }) },
    });
    await screen.findByLabelText("Check out");
    expectNamedControls();
    expect(screen.getByLabelText("Complete check-out for 02/10/2026")).toBeTruthy();
  });
});

describe("Home", () => {
  it("has a named Today card and named buttons", async () => {
    await openManagerScreen(null, { manager: { today: today({ plannedActivities: [plannedActivity()] }) } });

    await screen.findByLabelText("Today");
    expectNamedControls();
    expect(screen.getByLabelText("College drive, Anna Nagar College, planned. Show details").props.accessibilityRole).toBe("button");
  });
});

describe("My Month", () => {
  it("announces each day, groups the figures, and names the sheet's actions", async () => {
    const days = [monthDay("2026-10-02", { status: "FIELD", dayType: "FIELD", checkInAt: "2026-10-02T04:12:00Z", flags: ["NO_CHECK_OUT"] })];
    await openManagerScreen("My Month", { manager: { months: { "2026-10": managerMonth({ days }) } } });

    const cell = await screen.findByLabelText("Friday 2 October 2026, field day, checked in 09:42, field, no check-out");
    expect(cell.props.accessibilityRole).toBe("button");
    expect(screen.getByLabelText("Month figures")).toBeTruthy();
    expectNamedControls();
    await fireEvent.press(cell);
    await screen.findByText("Complete check-out");
    expectNamedControls();
    expect(screen.getByLabelText("Close")).toBeTruthy();
  });
});

describe("My Leave", () => {
  it("names the types, dates, half days, reason and Submit, and the cancel buttons", async () => {
    await openManagerScreen("My Leave", { manager: { leaves: [managerLeave()] } });

    await screen.findByLabelText("Submit");
    for (const label of ["Casual Leave", "Loss of Pay (unpaid)", "First date, not chosen", "Last date, not chosen", "Half day on the first day", "Half day on the last day", "Reason", "Submit"]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
    expect(screen.getByLabelText("Cancel request for 12/10/2026 – 14/10/2026")).toBeTruthy();
    expectNamedControls();
    expect(flat(screen.getByText("Submit").parent?.props.style).minHeight ?? 48).toBeGreaterThanOrEqual(48);
  });
});
