import {
  clearRead,
  deleteNotification,
  getUnreadCount,
  listNotifications,
  markAllRead,
  markRead,
} from "../../src/api/notificationsApi";
import { installLocationHeaders } from "../../src/location/LocationGate";
import { setAccessToken } from "../../src/security/memoryToken";
import { newServer, type FakeServer } from "../support/fakeServer";
import { notification } from "../support/notificationsFixtures";
import { installNotifications } from "../support/notificationsServer";

/**
 * Spec 021: the notification calls of the app speak exactly the contract in
 * specs/021-mobile-notifications/contracts/mobile-notifications-screens.md (the API of spec 010, unchanged).
 */
let server: FakeServer;
let removeLocation: () => void;

beforeEach(() => {
  server = newServer();
  installNotifications(server, {
    items: [notification({ id: "n1" }), notification({ id: "n2", read: true })],
  });
  setAccessToken("access-1");
  removeLocation = installLocationHeaders();
});

afterEach(() => {
  removeLocation();
  setAccessToken(null);
});

const last = () => server.calls[server.calls.length - 1];

describe("each notification call", () => {
  it("GET /me/notifications?page=0&size=25 for All, with no unread parameter", async () => {
    const page = await listNotifications({ unreadOnly: false });

    expect(last()).toMatchObject({ method: "GET", path: "/api/v1/me/notifications", query: { page: "0", size: "25" } });
    expect(last().query.unread).toBeUndefined();
    expect(page).toMatchObject({ page: 0, size: 25, totalElements: 2, unread: 1 });
    expect(page.content[0]).toEqual(
      expect.objectContaining({ id: "n1", type: "LEAVE_DECIDED", link: "/leave/history", channel: "IN_APP", read: false }),
    );
  });

  it("GET /me/notifications?unread=true for Unread only, and the page number for later pages", async () => {
    const page = await listNotifications({ unreadOnly: true, page: 2 });

    expect(last()).toMatchObject({ method: "GET", path: "/api/v1/me/notifications", query: { unread: "true", page: "2", size: "25" } });
    expect(page.totalElements).toBe(1);
  });

  it("GET /me/notifications/unread-count returns the number", async () => {
    expect(await getUnreadCount()).toBe(1);
    expect(last()).toMatchObject({ method: "GET", path: "/api/v1/me/notifications/unread-count" });
  });

  it("POST /me/notifications/{id}/read has no body", async () => {
    await markRead("n1");

    expect(last()).toMatchObject({ method: "POST", path: "/api/v1/me/notifications/n1/read" });
    expect(last().body).toBeUndefined();
  });

  it("POST /me/notifications/read-all has no body", async () => {
    expect(await markAllRead()).toEqual({ marked: 1 });
    expect(last()).toMatchObject({ method: "POST", path: "/api/v1/me/notifications/read-all" });
    expect(last().body).toBeUndefined();
  });

  it("DELETE /me/notifications/{id} and DELETE /me/notifications/read", async () => {
    await deleteNotification("n1");
    expect(last()).toMatchObject({ method: "DELETE", path: "/api/v1/me/notifications/n1" });

    expect(await clearRead()).toEqual({ deleted: 1 });
    expect(last()).toMatchObject({ method: "DELETE", path: "/api/v1/me/notifications/read" });
  });

  it("every call carries the bearer token, the client header and the location or its reason", async () => {
    await listNotifications({ unreadOnly: false });
    await getUnreadCount();
    await markRead("n1");
    await markAllRead();
    await deleteNotification("n2");
    await clearRead();

    expect(server.calls).toHaveLength(6);
    for (const call of server.calls) {
      expect(call.headers.Authorization).toBe("Bearer access-1");
      expect(call.headers["X-HLS-Client"]).toBeTruthy();
      expect(call.headers["X-HLS-Location"] ?? call.headers["X-HLS-Location-Status"]).toBeTruthy();
    }
  });
});
