import type { NotificationRow } from "../../src/api/notificationsApi";
import type { FakeReply, FakeServer } from "./fakeServer";
import { notificationPage } from "./notificationsFixtures";

const BASE = "/api/v1/me/notifications";

export type NotificationAction = "list" | "count" | "read" | "readAll" | "delete" | "clear";

export interface NotificationsFake {
  /** The signed-in user's notifications, newest first. */
  items: NotificationRow[];
  /** Another user's notifications: their ids answer 404, as on the real server. */
  others: NotificationRow[];
  /** Returns a refusal to make an action fail, or undefined to let it through. */
  refusal?: (action: NotificationAction, id?: string) => FakeReply | undefined;
  /** Calls the tests inspect. */
  reads: string[];
  deletes: string[];
  readAlls: number;
  clears: number;
  /** Adds a notification at the top, as if the server created it while the app is open. */
  add: (row: NotificationRow) => void;
}

const NOT_FOUND: FakeReply = { status: 404, body: { reason: "Notification not found." } };

/**
 * Registers the notification routes of spec 010 on a FakeServer for the signed-in user. Reads, deletes and clears are
 * applied to the returned state, so a list or count fetched afterwards shows the result as the real server would.
 */
export function installNotifications(server: FakeServer, over: Partial<NotificationsFake> = {}): NotificationsFake {
  const state: NotificationsFake = {
    items: [],
    others: [],
    reads: [],
    deletes: [],
    readAlls: 0,
    clears: 0,
    add: (row) => {
      state.items = [row, ...state.items];
    },
    ...over,
  };
  const unread = () => state.items.filter((n) => !n.read).length;
  const refused = (action: NotificationAction, id?: string) => state.refusal?.(action, id);

  server.on(`GET ${BASE}`, (request) => {
    const refusal = refused("list");
    if (refusal) return refusal;
    const page = Number(request.query.page ?? 0);
    const size = Number(request.query.size ?? 25);
    const pool = request.query.unread === "true" ? state.items.filter((n) => !n.read) : state.items;
    const body = notificationPage(pool.slice(page * size, page * size + size), {
      page,
      size,
      totalElements: pool.length,
      unread: unread(),
    });
    return { status: 200, body };
  });

  server.on(`GET ${BASE}/unread-count`, () => refused("count") ?? { status: 200, body: { unread: unread() } });

  server.on(`POST ${BASE}/read-all`, () => {
    const refusal = refused("readAll");
    if (refusal) return refusal;
    const marked = unread();
    state.items = state.items.map((n) => ({ ...n, read: true }));
    state.readAlls += 1;
    return { status: 200, body: { marked } };
  });

  server.on(`POST ${BASE}/{id}/read`, (request) => {
    const id = request.params.id;
    const refusal = refused("read", id);
    if (refusal) return refusal;
    const row = state.items.find((n) => n.id === id);
    if (!row) return NOT_FOUND;
    state.reads.push(id);
    state.items = state.items.map((n) => (n.id === id ? { ...n, read: true } : n));
    return { status: 200, body: { ...row, read: true } };
  });

  server.on(`DELETE ${BASE}/read`, () => {
    const refusal = refused("clear");
    if (refusal) return refusal;
    const deleted = state.items.filter((n) => n.read).length;
    state.items = state.items.filter((n) => !n.read);
    state.clears += 1;
    return { status: 200, body: { deleted } };
  });

  server.on(`DELETE ${BASE}/{id}`, (request) => {
    const id = request.params.id;
    const refusal = refused("delete", id);
    if (refusal) return refusal;
    if (!state.items.some((n) => n.id === id)) return NOT_FOUND;
    state.deletes.push(id);
    state.items = state.items.filter((n) => n.id !== id);
    return { status: 204 };
  });

  return state;
}
