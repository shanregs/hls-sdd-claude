import { NOTIFICATION_PAGE_SIZE } from "../config/constants";
import { api } from "./httpClient";

/**
 * Typed calls to the notification endpoints of spec 010, unchanged (spec 021 research §1). The shapes copy the
 * server's responses (specs/021-mobile-notifications/data-model.md); the app computes no count, scope or permission.
 */
export interface NotificationRow {
  id: string;
  /** Shown as text only, never branched on. */
  type: string;
  title: string;
  message: string;
  /** An app route starting with "/", possibly with `?month=YYYY-MM`, or null. */
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
  /** The caller's whole unread count, so opening the list needs one request. */
  unread: number;
}

const BASE = "/api/v1/me/notifications";
const enc = encodeURIComponent;

export interface ListQuery {
  unreadOnly: boolean;
  page?: number;
  size?: number;
}

export const listNotifications = ({ unreadOnly, page = 0, size = NOTIFICATION_PAGE_SIZE }: ListQuery) => {
  const params = [`page=${page}`, `size=${size}`];
  if (unreadOnly) params.unshift("unread=true");
  return api.get<NotificationPage>(`${BASE}?${params.join("&")}`);
};

export const getUnreadCount = () => api.get<{ unread: number }>(`${BASE}/unread-count`).then((r) => r.unread);

export const markRead = (id: string) => api.post<NotificationRow>(`${BASE}/${enc(id)}/read`);

export const markAllRead = () => api.post<{ marked: number }>(`${BASE}/read-all`);

export const deleteNotification = (id: string) => api.delete<void>(`${BASE}/${enc(id)}`);

export const clearRead = () => api.delete<{ deleted: number }>(`${BASE}/read`);
