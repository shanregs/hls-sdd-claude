import { act, fireEvent, screen } from "@testing-library/react-native";
import { AppState, type AppStateStatus } from "react-native";
import { ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS } from "../../src/config/constants";
import { DIRECTOR_MODEL, MANAGER_MODEL, TEACHER_MODEL } from "../support/fixtures";
import { notification, withNotificationActions } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";
import { openMenu } from "../support/navigation";

afterEach(() => jest.restoreAllMocks());

const titled = (title: string, over = {}) => notification({ title, message: `${title} message`, ...over });

/** Makes the app think it was in the background long enough to refresh the access model, and returns the trigger. */
function backgroundRefresh() {
  const handlers: ((state: AppStateStatus) => void)[] = [];
  jest.spyOn(AppState, "addEventListener").mockImplementation(((_type: string, listener: (s: AppStateStatus) => void) => {
    handlers.push(listener);
    return { remove: () => undefined };
  }) as never);
  return async () => {
    const realNow = Date.now();
    await act(async () => handlers.forEach((h) => h("background")));
    jest.spyOn(Date, "now").mockReturnValue(realNow + ACCESS_MODEL_REFRESH_AFTER_BACKGROUND_MS);
    await act(async () => handlers.forEach((h) => h("active")));
  };
}

describe("the bell and the item follow the server's navigation (spec 021 US4, FR-012)", () => {
  it.each([
    ["Teacher", TEACHER_MODEL],
    ["Manager", MANAGER_MODEL],
    ["Director", DIRECTOR_MODEL],
  ])("a %s has the bell and ACCOUNT → Notifications, once each", async (_role, model) => {
    await openNotificationsScreen({ open: "home", model, notifications: { items: [titled("One")] } });

    await screen.findByLabelText("Notifications, 1 unread");
    expect(screen.getAllByLabelText(/^Notifications,/)).toHaveLength(1);
    await openMenu();
    expect(await screen.findAllByLabelText("Notifications")).toHaveLength(1);
  });

  it("removes the bell and the item after the next menu refresh when View is taken away, and a stale screen says not authorized", async () => {
    const refresh = backgroundRefresh();
    const { server } = await openNotificationsScreen({ notifications: { items: [titled("One")] } });
    await screen.findByLabelText(/One\./);
    server.on("GET /api/v1/me/access-model", {
      status: 200,
      body: {
        ...TEACHER_MODEL,
        navigation: TEACHER_MODEL.navigation.map((s) => ({ ...s, items: s.items.filter((i) => i.route !== "/account/notifications") })),
      },
    });

    await refresh();

    await screen.findByText("You do not have access to that page.");
    expect(screen.queryByLabelText(/^Notifications,/)).toBeNull();
    await openMenu();
    await screen.findByLabelText("Log out");
    expect(screen.queryByLabelText("Notifications")).toBeNull();
  });

  it("removes Delete and Clear read from an open list after a refresh that takes DELETE away, while Mark as read stays (SC-007)", async () => {
    const refresh = backgroundRefresh();
    const { server } = await openNotificationsScreen({ notifications: { items: [titled("One"), titled("Two", { read: true })] } });
    await screen.findByLabelText("Delete: One");
    expect(screen.getByLabelText("Clear read")).toBeTruthy();
    server.on("GET /api/v1/me/access-model", { status: 200, body: withNotificationActions(TEACHER_MODEL, ["VIEW"]) });

    await refresh();

    await screen.findByLabelText("Mark as read: One");
    expect(screen.queryByLabelText(/^Delete:/)).toBeNull();
    expect(screen.queryByLabelText("Clear read")).toBeNull();
  });

  it("has no Admin or System notification screen in the app: their accounts cannot sign in here (spec 018)", async () => {
    await openNotificationsScreen({ open: "home" });

    // Only the app's own screens exist; none is named for Admin or System.
    await openMenu();
    await screen.findByLabelText("Log out");
    expect(screen.queryByLabelText(/admin|system/i)).toBeNull();
  });
});

describe("each user sees and changes only their own notifications (spec 021 SC-004)", () => {
  it("lists, counts, reads and deletes only the signed-in user's rows and leaves another user's untouched", async () => {
    const mine = [titled("Mine one"), titled("Mine two")];
    const theirs = [titled("Theirs one"), titled("Theirs two", { read: true })];
    const snapshot = JSON.stringify(theirs);
    const { server, notifications } = await openNotificationsScreen({ notifications: { items: mine, others: theirs } });

    await screen.findByLabelText(/Mine one\./);
    expect(screen.queryByLabelText(/Theirs/)).toBeNull();
    expect(screen.getByLabelText("Notifications, 2 unread")).toBeTruthy();

    await fireEvent.press(screen.getByLabelText("Mark as read: Mine one"));
    await fireEvent.press(await screen.findByLabelText("Delete: Mine two"));
    await screen.findByLabelText("Notifications, 0 unread");

    expect(JSON.stringify(notifications.others)).toBe(snapshot);
    const ids = new Set(mine.map((n) => n.id));
    const touched = server.calls
      .map((c) => c.path.match(/^\/api\/v1\/me\/notifications\/([^/]+)(?:\/read)?$/)?.[1])
      .filter((id): id is string => !!id && !["read", "read-all", "unread-count"].includes(id));
    expect(touched.length).toBeGreaterThan(0);
    for (const id of touched) expect(ids.has(id)).toBe(true);
  });
});
