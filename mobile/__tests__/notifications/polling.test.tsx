import { screen, waitFor } from "@testing-library/react-native";
import { notification } from "../support/notificationsFixtures";
import { openNotificationsScreen } from "../support/notificationsApp";
import { installPollingControl, type PollingControl } from "../support/pollingControl";

const COUNT = "GET /api/v1/me/notifications/unread-count";

let control: PollingControl;
beforeEach(() => {
  control = installPollingControl();
});

afterEach(() => jest.restoreAllMocks());

describe("bell polling (spec 021 FR-002, SC-001, SC-009)", () => {
  it("asks once on start and again every 30 seconds, so a new notification appears without a tap", async () => {
    const { server, notifications } = await openNotificationsScreen({ open: "home", notifications: { items: [] } });
    await screen.findByLabelText("Notifications, 0 unread");
    expect(server.callsTo(COUNT)).toHaveLength(1);
    expect(control.timers.size).toBe(1);

    notifications.add(notification());
    await control.tick();

    await screen.findByLabelText("Notifications, 1 unread");
    expect(server.callsTo(COUNT)).toHaveLength(2);
  });

  it("stops in the background: no timer and no request, however long it stays there", async () => {
    const { server } = await openNotificationsScreen({ open: "home" });
    await screen.findByLabelText("Notifications, 0 unread");
    const before = server.callsTo(COUNT).length;

    await control.appState("background");
    await control.tick();

    expect(control.timers.size).toBe(0);
    expect(control.cleared.length).toBe(1);
    expect(server.callsTo(COUNT)).toHaveLength(before);
  });

  it("asks at once on returning to the foreground and starts the timer again", async () => {
    const { server, notifications } = await openNotificationsScreen({ open: "home" });
    await screen.findByLabelText("Notifications, 0 unread");
    await control.appState("background");
    notifications.add(notification());

    await control.appState("active");

    await screen.findByLabelText("Notifications, 1 unread");
    expect(control.timers.size).toBe(1);
    expect(server.callsTo(COUNT).length).toBeGreaterThanOrEqual(2);
  });

  it("shows no number and no error for a failed request, and tries again on the next tick", async () => {
    let failing = false;
    const { notifications } = await openNotificationsScreen({
      open: "home",
      notifications: {
        items: [notification(), notification()],
        refusal: (action) => (action === "count" && failing ? { status: 500, body: { reason: "boom" } } : undefined),
      },
    });
    await screen.findByLabelText("Notifications, 2 unread");

    failing = true;
    await control.tick();
    await screen.findByLabelText("Notifications, count unavailable");
    expect(screen.queryByRole("alert")).toBeNull();

    failing = false;
    await control.tick();
    await screen.findByLabelText("Notifications, 2 unread");
    expect(notifications.items).toHaveLength(2);
  });

  it("keeps one request in flight: a tick during a slow answer does not start a second", async () => {
    let release: () => void = () => undefined;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    let slow = false;
    const { server } = await openNotificationsScreen({ open: "home", notifications: { items: [notification()] } });
    await screen.findByLabelText("Notifications, 1 unread");
    const before = server.callsTo(COUNT).length;
    server.on(COUNT, async () => {
      if (slow) await gate;
      return { status: 200, body: { unread: 1 } };
    });

    slow = true;
    await control.tick();
    await control.tick();
    expect(server.callsTo(COUNT)).toHaveLength(before + 1);

    release();
    await waitFor(() => expect(server.callsTo(COUNT).length).toBeGreaterThanOrEqual(before + 1));
  });
});
