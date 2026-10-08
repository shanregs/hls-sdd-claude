import { fireEvent, screen } from "@testing-library/react-native";
import { notification } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";
import type { NotificationAction } from "../support/notificationsServer";

afterEach(() => jest.restoreAllMocks());

const titled = (title: string, over = {}) => notification({ title, message: `${title} message`, ...over });

interface Case {
  name: string;
  action: NotificationAction;
  press: () => Promise<void>;
  text500: string;
  /** Asserts the screen is exactly as before the action. */
  unchanged: () => void;
  /** Asserts the action has worked. */
  done: () => Promise<void>;
}

const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));

const CASES: Case[] = [
  {
    name: "mark as read",
    action: "read",
    press: () => press("Mark as read: Target"),
    text500: "Couldn't mark it as read. Try again.",
    unchanged: () => expect(screen.getByLabelText(/^Unread\. Target\./)).toBeTruthy(),
    done: async () => void (await screen.findByLabelText("Notifications, 1 unread")),
  },
  {
    name: "mark all as read",
    action: "readAll",
    press: () => press("Mark all as read"),
    text500: "Couldn't mark them as read. Try again.",
    unchanged: () => expect(screen.getByLabelText("Notifications, 2 unread")).toBeTruthy(),
    done: async () => void (await screen.findByLabelText("Notifications, 0 unread")),
  },
  {
    name: "delete",
    action: "delete",
    press: () => press("Delete: Target"),
    text500: "Couldn't delete it. Try again.",
    unchanged: () => expect(screen.getByLabelText(/^Unread\. Target\./)).toBeTruthy(),
    done: async () => {
      await screen.findByLabelText("Notifications, 1 unread");
      expect(screen.queryByLabelText(/Target\./)).toBeNull();
    },
  },
  {
    name: "clear read",
    action: "clear",
    press: async () => {
      await press("Clear read");
      await press("Clear");
    },
    text500: "Couldn't clear the read notifications. Try again.",
    unchanged: () => expect(screen.getByLabelText(/^Done\./)).toBeTruthy(),
    done: async () => {
      await screen.findByText("2 unread");
      expect(screen.queryByLabelText(/^Done\./)).toBeNull();
    },
  },
];

const items = () => [titled("Target"), titled("Other"), titled("Done", { read: true })];

describe("a refused or failed action is never shown as done (spec 021 FR-011, SC-006)", () => {
  it.each(CASES)("$name: a server error shows the plain message, changes nothing, and works when tried again", async (c) => {
    let failing = true;
    await openNotificationsScreen({
      notifications: {
        items: items(),
        refusal: (action) => (action === c.action && failing ? { status: 500, body: { reason: "boom" } } : undefined),
      },
    });
    await screen.findByLabelText(/Target\./);

    await c.press();

    expect(await screen.findByText(c.text500)).toBeTruthy();
    c.unchanged();

    failing = false;
    if (c.action === "clear") {
      await press("Clear");
    } else {
      await c.press();
    }
    await c.done();
  });

  it.each(CASES)("$name: with no connection it says nothing was changed and works once back online", async (c) => {
    const { server } = await openNotificationsScreen({ notifications: { items: items() } });
    await screen.findByLabelText(/Target\./);

    server.offline = true;
    await c.press();

    expect(await screen.findByText("No connection. Nothing was changed.")).toBeTruthy();
    c.unchanged();

    server.offline = false;
    if (c.action === "clear") {
      await press("Clear");
    } else {
      await c.press();
    }
    await c.done();
  });

  it.each(CASES.filter((c) => c.action === "delete" || c.action === "clear"))(
    "$name: a 403 says the user may not delete, and changes nothing",
    async (c) => {
      await openNotificationsScreen({
        notifications: {
          items: items(),
          refusal: (action) => (action === c.action ? { status: 403, body: { reason: "Forbidden" } } : undefined),
        },
      });
      await screen.findByLabelText(/Target\./);

      await c.press();

      expect(await screen.findByText("You're not allowed to delete notifications.")).toBeTruthy();
      c.unchanged();
    },
  );

  it("removes a notification that was deleted elsewhere and says so", async () => {
    const { notifications } = await openNotificationsScreen({ notifications: { items: items() } });
    await screen.findByLabelText(/Target\./);
    notifications.items = notifications.items.filter((n) => n.title !== "Target");

    await press("Delete: Target");

    expect(await screen.findByText("This notification no longer exists.")).toBeTruthy();
    expect(screen.queryByLabelText(/Target\./)).toBeNull();
  });
});
