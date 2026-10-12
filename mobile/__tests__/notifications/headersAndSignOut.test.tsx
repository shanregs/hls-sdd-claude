import { fireEvent, screen } from "@testing-library/react-native";
import type { FakeServer } from "../support/fakeServer";
import { notification } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";
import { logOutViaMenu } from "../support/navigation";

afterEach(() => jest.restoreAllMocks());

const titled = (title: string, over = {}) => notification({ title, message: `${title} message`, ...over });
const notificationCalls = (server: FakeServer) =>
  server.calls.filter((c) => c.path.startsWith("/api/v1/me/notifications"));

describe("location and client headers on every notification request (spec 021 FR-013)", () => {
  it("carries X-HLS-Client and the location on the count, list, read, mark all, delete and clear", async () => {
    const { server } = await openNotificationsScreen({
      notifications: { items: [titled("One"), titled("Two", { read: true }), titled("Three", { read: true })] },
    });
    await screen.findByLabelText(/One\./);
    await fireEvent.press(screen.getByLabelText("Mark as read: One"));
    await screen.findByLabelText("Notifications, 0 unread");
    await fireEvent.press(screen.getByLabelText("Delete: Two"));
    await screen.findByLabelText("Delete: Three");
    await fireEvent.press(screen.getByLabelText("Clear read"));
    await fireEvent.press(await screen.findByLabelText("Clear"));
    await screen.findByText("No notifications");

    const calls = notificationCalls(server);
    expect(calls.length).toBeGreaterThanOrEqual(5);
    for (const call of calls) {
      expect(call.headers["X-HLS-Client"]).toBeTruthy();
      expect(call.headers["X-HLS-Location"] ?? call.headers["X-HLS-Location-Status"]).toBeTruthy();
    }
  });

  it("behaves the same with location denied, sending the reason instead", async () => {
    jest.requireMock("expo-location").__state.permission = "denied";
    const { server } = await openNotificationsScreen({ notifications: { items: [titled("One")] } });

    expect(await screen.findByLabelText(/One\./)).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Mark as read: One"));
    await screen.findByLabelText("Notifications, 0 unread");

    for (const call of notificationCalls(server)) {
      expect(call.headers["X-HLS-Location"]).toBeUndefined();
      expect(call.headers["X-HLS-Location-Status"]).toBeTruthy();
    }
  });
});

describe("nothing is left behind when the user changes (spec 021 FR-014, US4 AC6)", () => {
  it("shows no earlier count, row or detail to the next user on the same device", async () => {
    const { server, notifications } = await openNotificationsScreen({
      notifications: { items: [titled("Private note", { link: null })] },
    });
    await fireEvent.press(await screen.findByLabelText(/Private note\./));
    expect(await screen.findByLabelText("Close")).toBeTruthy();
    server.on("POST /api/v1/auth/logout", { status: 204 });

    await logOutViaMenu();
    await screen.findByText("Sign in to HLS");

    expect(screen.queryByText("Private note")).toBeNull();
    expect(screen.queryByLabelText(/Notifications,/)).toBeNull();
    expect(screen.queryByText(/unread/)).toBeNull();
    expect(notifications.items).toHaveLength(1);
  });

  it("returns to Sign In, with no notifications left on screen, when the session ends while the list is open", async () => {
    const { server } = await openNotificationsScreen({ notifications: { items: [titled("Secret")] } });
    await screen.findByLabelText(/Secret\./);
    server.on("GET /api/v1/me/notifications", { status: 401, body: { reason: "expired" } });
    server.on("POST /api/v1/auth/renew", { status: 401, body: { reason: "expired" } });

    await fireEvent.press(screen.getByLabelText("Unread only"));

    await screen.findByText("Sign in to HLS");
    expect(screen.queryByText("Secret")).toBeNull();
    expect(screen.queryByLabelText(/Secret/)).toBeNull();
  });
});
