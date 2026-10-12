import { fireEvent, screen } from "@testing-library/react-native";
import { TEACHER_MODEL } from "../support/fixtures";
import { notification, withNotificationActions } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";

afterEach(() => jest.restoreAllMocks());

const titled = (title: string, over = {}) => notification({ title, message: `${title} message`, ...over });

describe("mark and delete (spec 021 US3)", () => {
  it("marks one read without following its link: it is no longer highlighted and the bell drops by one", async () => {
    const { notifications } = await openNotificationsScreen({
      notifications: { items: [titled("Alpha", { link: "/leave/history" }), titled("Beta")] },
    });
    await screen.findByLabelText("Notifications, 2 unread");

    await fireEvent.press(await screen.findByLabelText("Mark as read: Alpha"));

    await screen.findByLabelText("Notifications, 1 unread");
    expect(screen.getByLabelText(/^Alpha\./)).toBeTruthy();
    expect(screen.queryByLabelText("Mark as read: Alpha")).toBeNull();
    expect(screen.getByLabelText("Mark as read: Beta")).toBeTruthy();
    expect(notifications.reads).toHaveLength(1);
    expect(screen.queryByRole("header", { name: "My Leave History" })).toBeNull();
    expect(await screen.findByText("1 unread")).toBeTruthy();
  });

  it("marks all as read: the count is zero, the bell shows 0 unread and the action goes away", async () => {
    const { notifications } = await openNotificationsScreen({
      notifications: { items: [titled("Alpha"), titled("Beta"), titled("Gamma", { read: true })] },
    });
    await screen.findByLabelText("Notifications, 2 unread");

    await fireEvent.press(screen.getByLabelText("Mark all as read"));

    await screen.findByLabelText("Notifications, 0 unread");
    expect(notifications.readAlls).toBe(1);
    expect(screen.queryByLabelText(/^Unread\./)).toBeNull();
    expect(screen.queryByLabelText("Mark all as read")).toBeNull();
    expect(await screen.findByText("0 unread")).toBeTruthy();
  });

  it("deletes one without a confirmation: the row disappears and the count is corrected if it was unread", async () => {
    const { notifications } = await openNotificationsScreen({
      notifications: { items: [titled("Alpha"), titled("Beta", { read: true })] },
    });
    await screen.findByLabelText("Notifications, 1 unread");

    await fireEvent.press(screen.getByLabelText("Delete: Alpha"));

    await screen.findByLabelText("Notifications, 0 unread");
    expect(screen.queryByLabelText("Cancel")).toBeNull();
    expect(screen.queryByLabelText(/Alpha\./)).toBeNull();
    expect(notifications.deletes).toHaveLength(1);

    await fireEvent.press(screen.getByLabelText("Delete: Beta"));
    await screen.findByText("No notifications");
    expect(screen.getByLabelText("Notifications, 0 unread")).toBeTruthy();
  });

  it("offers no Delete and no Clear read when the item's actions lack DELETE, while reading and marking read still work", async () => {
    await openNotificationsScreen({
      model: withNotificationActions(TEACHER_MODEL, ["VIEW"]),
      notifications: { items: [titled("Alpha"), titled("Beta", { read: true })] },
    });
    await screen.findByLabelText(/Alpha\./);

    expect(screen.queryByLabelText(/^Delete:/)).toBeNull();
    expect(screen.queryByLabelText("Clear read")).toBeNull();
    expect(screen.getByLabelText("Mark as read: Alpha")).toBeTruthy();
    expect(screen.getByLabelText("Mark all as read")).toBeTruthy();

    await fireEvent.press(screen.getByLabelText("Mark all as read"));
    await screen.findByLabelText("Notifications, 0 unread");
  });
});
