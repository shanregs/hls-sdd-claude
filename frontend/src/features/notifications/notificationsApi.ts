import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

/** One in-app notification (contracts/notifications-api.md). */
export interface NotificationRow {
  id: string;
  type: string;
  title: string;
  message: string;
  /** An app route starting with "/", or null. */
  link: string | null;
  channel: string;
  read: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface NotificationPage {
  content: NotificationRow[];
  page: number;
  size: number;
  totalElements: number;
  /** The caller's unread count, so the page needs one request. */
  unread: number;
}

const BASE = "/api/v1/me/notifications";

export function listNotifications(
  authFetch: AuthFetch,
  params: { unreadOnly: boolean; page: number; size: number },
): Promise<ApiResult<NotificationPage>> {
  const query = queryString({
    unread: params.unreadOnly ? true : undefined,
    page: params.page,
    size: params.size,
  });
  return getJson(
    authFetch,
    `${BASE}?${query}`,
    "Could not load notifications.",
  );
}

export async function getUnreadCount(
  authFetch: AuthFetch,
): Promise<ApiResult<number>> {
  const result = await getJson<{ unread: number }>(
    authFetch,
    `${BASE}/unread-count`,
    "Could not load the unread count.",
  );
  return result.ok ? { ok: true, data: result.data.unread } : result;
}

export function markRead(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<NotificationRow>> {
  return sendJson(authFetch, "POST", `${BASE}/${id}/read`);
}

export function markAllRead(
  authFetch: AuthFetch,
): Promise<ApiResult<{ marked: number }>> {
  return sendJson(authFetch, "POST", `${BASE}/read-all`);
}

export function deleteNotification(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<void>> {
  return sendJson(authFetch, "DELETE", `${BASE}/${id}`, undefined, false);
}

export function clearRead(
  authFetch: AuthFetch,
): Promise<ApiResult<{ deleted: number }>> {
  return sendJson(authFetch, "DELETE", `${BASE}/read`);
}
