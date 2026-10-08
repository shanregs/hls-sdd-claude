import { fireEvent, screen, within } from "@testing-library/react-native";
import { badgeText } from "../../src/notifications/NotificationBell";
import { TEACHER_MODEL } from "../support/fixtures";
import { notification, withoutNotifications } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";
import { openMenu } from "../support/navigation";

afterEach(() => jest.restoreAllMocks());

const unreadItems = (n: number) => Array.from({ length: n }, () => notification());

describe("the header bell (spec 021 US1)", () => {
  it("shows the unread count from the server on Home", async () => {
    await openNotificationsScreen({ open: "home", notifications: { items: unreadItems(3) } });

    const bell = await screen.findByLabelText("Notifications, 3 unread");
    expect(within(bell.parent as never).queryByText("3") ?? screen.getByText("3")).toBeTruthy();
  });

  it("hides the number at zero and says 0 unread", async () => {
    await openNotificationsScreen({ open: "home", notifications: { items: [notification({ read: true })] } });

    await screen.findByLabelText("Notifications, 0 unread");
    expect(screen.queryByText("0")).toBeNull();
  });

  it("shows 99 exactly and 99+ above 99, while the label keeps the exact number", async () => {
    await openNotificationsScreen({ open: "home", notifications: { items: unreadItems(100) } });

    await screen.findByLabelText("Notifications, 100 unread");
    expect(screen.getByText("99+")).toBeTruthy();
    expect(badgeText(99)).toBe("99");
    expect(badgeText(100)).toBe("99+");
    expect(badgeText(0)).toBeNull();
    expect(badgeText(null)).toBeNull();
  });

  it("has no number and no error on screen while the count cannot be loaded", async () => {
    await openNotificationsScreen({
      open: "home",
      notifications: {
        items: unreadItems(2),
        refusal: (action) => (action === "count" ? { status: 500, body: { reason: "boom" } } : undefined),
      },
    });

    await screen.findByLabelText("Notifications, count unavailable");
    expect(screen.queryByText("2")).toBeNull();
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getByText("Welcome, Tara")).toBeTruthy();
  });

  it("opens the Notifications screen when tapped", async () => {
    await openNotificationsScreen({ open: "home", notifications: { items: unreadItems(3) } });

    await fireEvent.press(await screen.findByLabelText("Notifications, 3 unread"));

    expect((await screen.findAllByText("Notifications")).length).toBeGreaterThan(0);
    expect(screen.queryByText("Welcome, Tara")).toBeNull();
  });

  it("is a named control the screen reader can announce", async () => {
    await openNotificationsScreen({ open: "home", notifications: { items: unreadItems(1) } });

    const bell = await screen.findByLabelText("Notifications, 1 unread");
    expect(bell.props.accessibilityLabel).toBe("Notifications, 1 unread");
  });

  it("is absent, with no Notifications menu item, when the server's navigation does not offer it", async () => {
    await openNotificationsScreen({ open: "home", model: withoutNotifications(TEACHER_MODEL), notifications: { items: unreadItems(2) } });

    expect(screen.queryByLabelText(/^Notifications,/)).toBeNull();
    await openMenu();
    await screen.findByLabelText("Log out");
    expect(screen.queryByLabelText("Notifications")).toBeNull();
  });

  it("appears once, with one menu item, for a user whose roles both offer Notifications", async () => {
    const twice = {
      ...TEACHER_MODEL,
      roles: ["TEACHER", "MANAGER"],
      navigation: TEACHER_MODEL.navigation.map((section) =>
        section.section === "ACCOUNT"
          ? { ...section, items: [...section.items, ...section.items.filter((i) => i.route === "/account/notifications")] }
          : section,
      ),
    };
    await openNotificationsScreen({ open: "home", model: twice, notifications: { items: unreadItems(1) } });

    await screen.findByLabelText("Notifications, 1 unread");
    expect(screen.getAllByLabelText(/^Notifications,/)).toHaveLength(1);
    await openMenu();
    expect(await screen.findAllByLabelText("Notifications")).toHaveLength(1);
  });
});
