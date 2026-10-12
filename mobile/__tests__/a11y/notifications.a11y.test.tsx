import { fireEvent, screen } from "@testing-library/react-native";
import { notification } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";

afterEach(() => jest.restoreAllMocks());

const titled = (title: string, over = {}) => notification({ title, message: `${title} message`, ...over });

/** Every pressable element has a name a screen reader can announce. */
function expectNamedControls() {
  const controls = [...screen.queryAllByRole("button"), ...screen.queryAllByRole("radio"), ...screen.queryAllByRole("checkbox")];
  expect(controls.length).toBeGreaterThan(0);
  for (const control of controls) {
    const name = control.props.accessibilityLabel as string | undefined;
    expect(typeof name === "string" && name.trim().length > 0).toBe(true);
  }
}

const flat = (style: unknown): Record<string, unknown> => Object.assign({}, ...[style].flat(Infinity).filter(Boolean));

describe("notification screens: accessibility (spec 021 FR-015, SC-008)", () => {
  it("the bell announces the unread count", async () => {
    await openNotificationsScreen({ open: "home", notifications: { items: [titled("One"), titled("Two")] } });

    const bell = await screen.findByLabelText("Notifications, 2 unread");
    expect(bell.props.accessibilityRole).toBe("button");
  });

  it("the list: header, filters, rows and actions are named, and unread rows say so in words", async () => {
    await openNotificationsScreen({
      notifications: { items: [titled("Unread one"), titled("Read one", { read: true })] },
    });

    expect(await screen.findByRole("header", { name: "1 unread" })).toBeTruthy();
    for (const label of ["All", "Unread only", "Mark all as read", "Clear read", "Mark as read: Unread one", "Delete: Unread one", "Delete: Read one"]) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }
    expect(screen.getByLabelText(/^Unread\. Unread one\. \d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/)).toBeTruthy();
    expect(screen.getByLabelText(/^Read one\. \d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/)).toBeTruthy();
    expectNamedControls();
  });

  it("uses touch targets of at least 48 dp for the rows, the filter chips and the actions", async () => {
    await openNotificationsScreen({ notifications: { items: [titled("Unread one")] } });
    const row = await screen.findByLabelText(/^Unread\. Unread one\./);

    expect(Number(flat(row.props.style).minHeight ?? 0)).toBeGreaterThanOrEqual(48);
    const chip = screen.getByLabelText("Unread only");
    const heights = [chip, ...(chip.parent ? [chip.parent] : [])].map((n) => Number(flat(n.props.style).minHeight ?? 0));
    expect(Math.max(...heights)).toBeGreaterThanOrEqual(48);
  });

  it("the detail dialog and the clear confirmation have headers and named controls; errors are alerts", async () => {
    await openNotificationsScreen({
      notifications: {
        items: [titled("No link", { link: null }), titled("Read one", { read: true })],
        refusal: (action) => (action === "clear" ? { status: 500, body: { reason: "boom" } } : undefined),
      },
    });

    await fireEvent.press(await screen.findByLabelText(/^Unread\. No link\./));
    expect(await screen.findByRole("header", { name: "No link" })).toBeTruthy();
    expect(screen.getByLabelText("Close")).toBeTruthy();
    expectNamedControls();
    await fireEvent.press(screen.getByLabelText("Close"));

    await fireEvent.press(screen.getByLabelText("Clear read"));
    expect(await screen.findByRole("header", { name: "Clear read notifications?" })).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Clear"));
    const alert = await screen.findByText("Couldn't clear the read notifications. Try again.");
    expect(alert.props.accessibilityRole ?? alert.parent?.props.accessibilityRole).toBe("alert");
    expectNamedControls();
  });

  it("a failed action is announced as an alert on the screen", async () => {
    await openNotificationsScreen({
      notifications: {
        items: [titled("Unread one")],
        refusal: (action) => (action === "readAll" ? { status: 500, body: { reason: "boom" } } : undefined),
      },
    });
    await fireEvent.press(await screen.findByLabelText("Mark all as read"));

    expect(await screen.findByRole("alert")).toBeTruthy();
  });
});
