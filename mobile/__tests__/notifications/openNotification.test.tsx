import { fireEvent, screen } from "@testing-library/react-native";
import { MANAGER_MODEL } from "../support/fixtures";
import { notification, SERVER_TEXTS } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";

afterEach(() => jest.restoreAllMocks());

const READ = "POST /api/v1/me/notifications/{id}/read";
const titled = (title: string, over = {}) => notification({ title, message: `${title} message`, ...over });
const rowByTitle = (title: string) => screen.findByLabelText(new RegExp(`^(Unread\\. )?${title}\\.`));

describe("opening a notification (spec 021 US2)", () => {
  it("marks it read, drops the bell, and opens the screen its link leads to", async () => {
    const { server, notifications } = await openNotificationsScreen({
      notifications: { items: [titled("Leave decided", { link: "/leave/history" })] },
    });

    await fireEvent.press(await rowByTitle("Leave decided"));

    expect(await screen.findByRole("header", { name: "My Leave History" })).toBeTruthy();
    expect(notifications.reads).toHaveLength(1);
    expect(server.callsTo(READ)).toHaveLength(1);
    expect(await screen.findByLabelText("Notifications, 0 unread")).toBeTruthy();
  });

  it("opens Leave Management for a Manager whose menu offers it", async () => {
    await openNotificationsScreen({
      model: MANAGER_MODEL,
      notifications: { items: [titled("New leave request", { link: "/operations/leave" })] },
    });

    await fireEvent.press(await rowByTitle("New leave request"));

    expect(await screen.findByRole("header", { name: "Leave Management" })).toBeTruthy();
  });

  it("opens My Attendance on the month in the link", async () => {
    const { server } = await openNotificationsScreen({
      notifications: { items: [titled("Attendance updated", { link: "/my-attendance?month=2026-08" })] },
    });

    await fireEvent.press(await rowByTitle("Attendance updated"));

    expect(await screen.findByRole("header", { name: "My Attendance" })).toBeTruthy();
    expect(await screen.findByText("August 2026")).toBeTruthy();
    expect(server.callsTo("GET /api/v1/attendance/me").map((c) => c.query.month)).toContain("2026-08");
  });

  it("does not send a read call for a notification that is already read, and still follows its link", async () => {
    const { server } = await openNotificationsScreen({
      notifications: { items: [titled("Old news", { read: true, link: "/leave/history" })] },
    });

    await fireEvent.press(await rowByTitle("Old news"));

    expect(await screen.findByRole("header", { name: "My Leave History" })).toBeTruthy();
    expect(server.callsTo(READ)).toHaveLength(0);
  });

  it.each([
    ["a link the app has no screen for", "/something-else"],
    ["a link the user's menu does not offer", "/operations/leave"],
    ["no link", null],
  ])("marks it read and shows its full text for %s, opening nothing else", async (_name, link) => {
    const { notifications } = await openNotificationsScreen({
      notifications: { items: [titled("Plain notice", { link, message: "The whole message of the notice, in full." })] },
    });

    await fireEvent.press(await rowByTitle("Plain notice"));

    // The row still shows the message too; the dialog adds the full text and a Close button.
    expect((await screen.findAllByText("The whole message of the notice, in full.")).length).toBe(2);
    expect(await screen.findByLabelText("Close")).toBeTruthy();
    expect(notifications.reads).toHaveLength(1);
    expect(screen.queryByRole("alert")).toBeNull();
    await fireEvent.press(screen.getByLabelText("Close"));
    expect(screen.queryByLabelText("Close")).toBeNull();
    expect(await screen.findByLabelText("Notifications, 0 unread")).toBeTruthy();
  });

  it("sends one read request when the same notification is tapped twice while the first is still on its way", async () => {
    const { server, notifications } = await openNotificationsScreen({ notifications: { items: [titled("Twice", { link: null })] } });
    const row = await rowByTitle("Twice");
    let release: () => void = () => undefined;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.on(READ, async (request) => {
      await gate;
      notifications.reads.push(request.params.id);
      return { status: 200, body: notification({ id: request.params.id, read: true }) };
    });

    await fireEvent.press(row);
    await fireEvent.press(row);
    expect(server.callsTo(READ)).toHaveLength(1);

    release();
    await screen.findByLabelText("Close");
    expect(server.callsTo(READ)).toHaveLength(1);
  });

  it("follows the link but keeps the row unread when marking read fails", async () => {
    const { notifications } = await openNotificationsScreen({
      notifications: {
        items: [titled("Stays unread", { link: "/leave/history" })],
        refusal: (action) => (action === "read" ? { status: 500, body: { reason: "boom" } } : undefined),
      },
    });

    await fireEvent.press(await rowByTitle("Stays unread"));

    expect(await screen.findByRole("header", { name: "My Leave History" })).toBeTruthy();
    expect(notifications.reads).toHaveLength(0);
    expect(await screen.findByLabelText("Notifications, 1 unread")).toBeTruthy();
  });

  it("shows a short notice and an unread row when marking read fails and there is no link to follow", async () => {
    await openNotificationsScreen({
      notifications: {
        items: [titled("Unread still", { link: null })],
        refusal: (action) => (action === "read" ? { status: 500, body: { reason: "boom" } } : undefined),
      },
    });

    await fireEvent.press(await rowByTitle("Unread still"));

    expect(await screen.findByText("Couldn't mark it as read. Try again.")).toBeTruthy();
    await fireEvent.press(await screen.findByLabelText("Close"));
    expect(screen.getByLabelText(/^Unread\. Unread still\./)).toBeTruthy();
  });

  it("removes a notification that no longer exists and says so", async () => {
    await openNotificationsScreen({
      notifications: {
        items: [titled("Gone"), titled("Stays")],
        refusal: (action, id) => (action === "read" && id !== undefined ? { status: 404, body: { reason: "Notification not found." } } : undefined),
      },
    });

    await fireEvent.press(await rowByTitle("Gone"));

    expect(await screen.findByText("This notification no longer exists.")).toBeTruthy();
    expect(screen.queryByLabelText(/^Gone\./)).toBeNull();
    expect(screen.queryByLabelText(/^Unread\. Gone\./)).toBeNull();
  });

  it("leaves the notification in the list when the destination shows its own error", async () => {
    const { notifications } = await openNotificationsScreen({
      leave: { profileMissing: true },
      notifications: { items: [titled("Leave decided", { link: "/leave/history" })] },
    });

    await fireEvent.press(await rowByTitle("Leave decided"));

    expect(await screen.findByRole("header", { name: "My Leave History" })).toBeTruthy();
    expect(notifications.items.map((n) => n.title)).toContain("Leave decided");
  });

  it.each(SERVER_TEXTS.map((text) => [text.title as string, text] as const))(
    "either opens the matching screen or shows the text for the server's %s notification (SC-005)",
    async (_title, text) => {
      await openNotificationsScreen({ notifications: { items: [notification(text)] } });

      await fireEvent.press(await rowByTitle(text.title as string));

      const opened =
        text.link === "/leave/history"
          ? "My Leave History"
          : text.link === "/operations/leave"
            ? null // a Teacher's menu does not offer Leave Management
            : "My Attendance";
      if (opened) {
        expect(await screen.findByRole("header", { name: opened })).toBeTruthy();
      } else {
        expect(await screen.findByLabelText("Close")).toBeTruthy();
      }
      expect(screen.queryByText("Something went wrong")).toBeNull();
    },
  );
});
