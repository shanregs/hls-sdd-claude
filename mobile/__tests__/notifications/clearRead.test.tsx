import { fireEvent, screen } from "@testing-library/react-native";
import { TEACHER_MODEL } from "../support/fixtures";
import { notification, withNotificationActions } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";

afterEach(() => jest.restoreAllMocks());

const titled = (title: string, over = {}) => notification({ title, message: `${title} message`, ...over });

describe("Clear read (spec 021 FR-008)", () => {
  it("asks to confirm, removes nothing on Cancel, and removes only read ones on Confirm", async () => {
    const { notifications } = await openNotificationsScreen({
      notifications: { items: [titled("Unread one"), titled("Read one", { read: true }), titled("Read two", { read: true })] },
    });
    await screen.findByLabelText(/Read one\./);

    await fireEvent.press(screen.getByLabelText("Clear read"));
    expect(await screen.findByText("Clear read notifications?")).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Cancel"));
    expect(notifications.clears).toBe(0);
    expect(screen.getByLabelText(/Read one\./)).toBeTruthy();
    expect(screen.queryByText("Clear read notifications?")).toBeNull();

    await fireEvent.press(screen.getByLabelText("Clear read"));
    await fireEvent.press(await screen.findByLabelText("Clear"));

    await screen.findByText("1 unread");
    expect(notifications.clears).toBe(1);
    expect(screen.queryByLabelText(/Read one\./)).toBeNull();
    expect(screen.queryByLabelText(/Read two\./)).toBeNull();
    expect(screen.getByLabelText(/^Unread\. Unread one\./)).toBeTruthy();
    expect(screen.getByLabelText("Notifications, 1 unread")).toBeTruthy();
  });

  it("is not offered without the Delete permission", async () => {
    await openNotificationsScreen({
      model: withNotificationActions(TEACHER_MODEL, ["VIEW"]),
      notifications: { items: [titled("Read one", { read: true })] },
    });
    await screen.findByLabelText(/Read one\./);

    expect(screen.queryByLabelText("Clear read")).toBeNull();
  });

  it("is disabled when no read notification is loaded", async () => {
    await openNotificationsScreen({ notifications: { items: [titled("Only unread")] } });
    await screen.findByLabelText(/Only unread\./);

    expect(screen.getByLabelText("Clear read").props.accessibilityState.disabled).toBe(true);
  });
});
