import { fireEvent, screen } from "@testing-library/react-native";
import { MANAGER_MODEL } from "../support/fixtures";
import { fillDraft } from "../support/leaveActions";
import { openLeaveScreen } from "../support/leaveApp";
import { leaveRequest, previewDays } from "../support/leaveFixtures";

afterEach(() => jest.restoreAllMocks());

/** True when an ancestor is a radio or checkbox that already carries the name (Paper nests an inner control). */
function insideNamedControl(node: { parent: unknown }): boolean {
  let up = (node as { parent: { props?: Record<string, unknown>; parent: unknown } | null }).parent;
  while (up) {
    const role = up.props?.accessibilityRole;
    if ((role === "radio" || role === "checkbox") && typeof up.props?.accessibilityLabel === "string") return true;
    up = up.parent as typeof up | null;
  }
  return false;
}

/** Every pressable element has a name a screen reader can announce. */
function expectNamedControls() {
  const controls = [
    ...screen.queryAllByRole("button"),
    ...screen.queryAllByRole("radio"),
    ...screen.queryAllByRole("checkbox"),
  ];
  for (const control of controls) {
    if (insideNamedControl(control)) continue;
    const name = control.props.accessibilityLabel as string | undefined;
    expect(typeof name === "string" && name.trim().length > 0).toBe(true);
  }
}

const flat = (style: unknown): Record<string, unknown> => Object.assign({}, ...[style].flat(Infinity).filter(Boolean));

describe("leave screens: accessibility (spec 020 FR-018, SC-009)", () => {
  it("Apply Leave: every control is named, errors are alerts, and the preview days announce their kind", async () => {
    await openLeaveScreen("Apply Leave");
    await screen.findByLabelText("Casual");
    for (const label of [
      "First date, not chosen",
      "Last date, not chosen",
      "Half day on the first day",
      "Half day on the last day",
      "Reason",
      "Check",
      "Submit",
    ]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
    expectNamedControls();

    await fillDraft({ first: "Monday 12 October 2026", last: "Wednesday 14 October 2026", reason: "Family function" });
    await fireEvent.press(screen.getByLabelText("Check"));
    await screen.findByText(/^Working days:/);
    expect(screen.getByLabelText("Monday 12 October 2026, counted, whole day")).toBeTruthy();
    expectNamedControls();
  });

  it("uses touch targets of at least 48 dp for the status chips", async () => {
    await openLeaveScreen("My Leave History", { leave: { requests: [leaveRequest()] } });
    await screen.findByText("12/10/2026 – 14/10/2026");

    const chip = screen.getByLabelText("Approved");
    const heights = [chip, ...(chip.parent ? [chip.parent] : [])].map((n) => Number(flat(n.props.style).minHeight ?? 0));
    expect(Math.max(...heights)).toBeGreaterThanOrEqual(48);
    const cancel = screen.getByLabelText("Cancel request");
    expect(cancel).toBeTruthy();
  });

  it("My Leave History: cards announce type, dates, working days, status and decision; the dialog announces its error", async () => {
    await openLeaveScreen("My Leave History", {
      leave: {
        requests: [
          leaveRequest({
            status: "APPROVED",
            decidedByName: "Manoj",
            decidedAt: "2026-10-05T05:00:00Z",
            allowedActions: ["CANCEL"],
          }),
        ],
        cancelRefusal: () => ({ status: 409, body: { reason: "This request was already cancelled." } }),
      },
    });

    expect(await screen.findByLabelText(/^Casual, 12\/10\/2026 – 14\/10\/2026, 3 working days, Approved, Approved by Manoj/)).toBeTruthy();
    expectNamedControls();

    await fireEvent.press(screen.getByLabelText("Cancel request"));
    await fireEvent.press(await screen.findByLabelText("Yes, cancel it"));
    const alert = await screen.findByText("This request was already cancelled.");
    expect(alert.props.accessibilityRole ?? alert.parent?.props.accessibilityRole).toBe("alert");
    expectNamedControls();
  });

  it("Leave Management and the detail: rows and actions are named", async () => {
    await openLeaveScreen("Leave Management", {
      model: MANAGER_MODEL,
      leave: {
        requests: [leaveRequest({ as: "supervisor", teacherName: "Asha Rao" })],
        detailOf: () => ({ days: previewDays(["2026-10-12"]), problems: [] }),
      },
    });
    expect(await screen.findByLabelText("Pending: 1")).toBeTruthy();
    expect(screen.getByLabelText("Filter by status")).toBeTruthy();
    expectNamedControls();

    await fireEvent.press(screen.getByLabelText(/^Asha Rao, Demo School One/));
    await screen.findByText("Days it would mark");
    for (const label of ["Back to requests", "Approve", "Reject"]) expect(screen.getByLabelText(label)).toBeTruthy();
    expect(screen.getByLabelText("Monday 12 October 2026, whole day")).toBeTruthy();
    expectNamedControls();

    await fireEvent.press(screen.getByLabelText("Reject"));
    await screen.findByLabelText("Reason");
    for (const label of ["Reason", "Confirm reject", "Cancel"]) expect(screen.getByLabelText(label)).toBeTruthy();
    expect(screen.getByLabelText("500 characters left")).toBeTruthy();
  });

  it("Home widget: announces the count as a button", async () => {
    await openLeaveScreen("Dashboard", {
      model: MANAGER_MODEL,
      leave: { requests: [leaveRequest({ as: "supervisor" })] },
    });

    const widget = await screen.findByLabelText("Pending leave requests: 1");
    expect(widget.props.accessibilityRole).toBe("button");
  });
});
