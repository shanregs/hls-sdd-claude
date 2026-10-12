import { fireEvent, screen } from "@testing-library/react-native";
import { formatDateTime } from "../../src/formats/dates";
import { notification } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";
import { installPollingControl, type PollingControl } from "../support/pollingControl";

const LIST = "GET /api/v1/me/notifications";

let control: PollingControl;
beforeEach(() => {
  control = installPollingControl();
});
afterEach(() => jest.restoreAllMocks());

const titled = (title: string, over = {}) => notification({ title, message: `${title} message`, ...over });

describe("the Notifications list (spec 021 US2)", () => {
  it("shows only the signed-in user's own rows, newest first, unread marked, with title, message, time and the exact unread count", async () => {
    const own = [
      titled("Newest unread", { createdAt: "2026-10-05T08:30:00Z" }),
      titled("Older read", { createdAt: "2026-10-04T08:30:00Z", read: true }),
    ];
    await openNotificationsScreen({
      notifications: { items: own, others: [titled("Someone else's", { createdAt: "2026-10-05T09:30:00Z" })] },
    });

    expect(await screen.findByText("1 unread")).toBeTruthy();
    expect(screen.getByLabelText(`Unread. Newest unread. ${formatDateTime("2026-10-05T08:30:00Z")}`)).toBeTruthy();
    expect(screen.getByLabelText(`Older read. ${formatDateTime("2026-10-04T08:30:00Z")}`)).toBeTruthy();
    expect(screen.getByText("Newest unread message")).toBeTruthy();
    expect(screen.queryByText("Someone else's")).toBeNull();
    const titles = screen.getAllByRole("button").map((b) => b.props.accessibilityLabel as string);
    expect(titles.findIndex((l) => l.includes("Newest unread"))).toBeLessThan(titles.findIndex((l) => l.includes("Older read")));
  });

  it("shows the time as the server's instant in DD/MM/YYYY HH:mm, not a relative time", async () => {
    await openNotificationsScreen({ notifications: { items: [titled("Timed", { createdAt: "2026-10-05T08:30:00Z" })] } });

    const shown = await screen.findByText(formatDateTime("2026-10-05T08:30:00Z"));
    expect(shown.props.children).toMatch(/^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/);
    expect(screen.queryByText(/ago|today|yesterday/i)).toBeNull();
  });

  it("shows the exact unread count in the header above 99", async () => {
    await openNotificationsScreen({ notifications: { items: Array.from({ length: 120 }, (_, i) => titled(`Item ${i}`)) } });

    expect(await screen.findByText("120 unread")).toBeTruthy();
  });

  it("filters to unread only and back to all", async () => {
    const { server } = await openNotificationsScreen({
      notifications: { items: [titled("Unread one"), titled("Read one", { read: true })] },
    });
    await screen.findByLabelText(/Read one\./);

    await fireEvent.press(screen.getByLabelText("Unread only"));

    await screen.findByLabelText(/^Unread\. Unread one\./);
    expect(screen.queryByLabelText(/^Read one\./)).toBeNull();
    expect(server.callsTo(LIST).some((c) => c.query.unread === "true")).toBe(true);

    await fireEvent.press(screen.getByLabelText("All"));
    await screen.findByLabelText(/^Read one\./);
    expect(server.callsTo(LIST).filter((c) => c.query.unread === undefined).length).toBeGreaterThanOrEqual(2);
  });

  it("loads older notifications in pages of 25", async () => {
    const { server } = await openNotificationsScreen({
      notifications: { items: Array.from({ length: 30 }, (_, i) => titled(`Item ${i}`)) },
    });
    await screen.findByLabelText(/Item 0\./);
    expect(screen.queryByLabelText(/Item 29\./)).toBeNull();

    await fireEvent.press(screen.getByLabelText("Load more"));

    await screen.findByLabelText(/Item 29\./);
    expect(screen.queryByLabelText("Load more")).toBeNull();
    const pages = server.callsTo(LIST).map((c) => [c.query.page, c.query.size]);
    expect(pages).toContainEqual(["0", "25"]);
    expect(pages).toContainEqual(["1", "25"]);
  });

  it("shows the empty states", async () => {
    await openNotificationsScreen({ notifications: { items: [] } });
    expect(await screen.findByText("No notifications")).toBeTruthy();

    await fireEvent.press(screen.getByLabelText("Unread only"));
    expect(await screen.findByText("No unread notifications")).toBeTruthy();
  });

  it("shows an error with Retry, drops the rows, and recovers on Retry", async () => {
    let failing = true;
    await openNotificationsScreen({
      notifications: {
        items: [titled("Back again")],
        refusal: (action) => (action === "list" && failing ? { status: 500, body: { reason: "boom" } } : undefined),
      },
    });
    expect(await screen.findByText("We couldn't load your notifications.")).toBeTruthy();
    expect(screen.queryByLabelText(/Back again/)).toBeNull();

    failing = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByLabelText(/Back again\./)).toBeTruthy();
  });

  it("shows a no-connection state with Retry", async () => {
    const { server } = await openNotificationsScreen({ notifications: { items: [titled("Later")] }, open: "home" });
    await screen.findByLabelText("Notifications, 1 unread");
    server.offline = true;
    await fireEvent.press(screen.getByLabelText("Notifications, 1 unread"));

    expect(await screen.findByText("Check your internet connection and try again.")).toBeTruthy();
    server.offline = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByLabelText(/Later\./)).toBeTruthy();
  });

  it("does not move the rows when a notification arrives, while the bell does", async () => {
    const { notifications } = await openNotificationsScreen({ notifications: { items: [titled("Already here")] } });
    await screen.findByLabelText(/Already here\./);
    expect(screen.getByText("1 unread")).toBeTruthy();

    notifications.add(titled("Just arrived"));
    await control.tick();

    await screen.findByText("2 unread");
    expect(screen.queryByLabelText(/Just arrived/)).toBeNull();
    expect(screen.getByLabelText("Notifications, 2 unread")).toBeTruthy();
  });
});
