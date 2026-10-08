import type { AccessModel } from "../../src/api/accessModelApi";
import type { NotificationPage, NotificationRow } from "../../src/api/notificationsApi";

let counter = 0;

/** One notification as the server sends it (spec 010 contract); the defaults are an unread leave decision. */
export function notification(over: Partial<NotificationRow> = {}): NotificationRow {
  counter += 1;
  const createdAt = over.createdAt ?? `2026-10-05T08:${String(30 - (counter % 30)).padStart(2, "0")}:00Z`;
  return {
    id: `n-${counter}`,
    type: "LEAVE_DECIDED",
    title: "Your leave was rejected",
    message: "Your Sick leave on 28/09/2026 was rejected: Annual exams are on that day.",
    link: "/leave/history",
    channel: "IN_APP",
    read: false,
    createdAt,
    updatedAt: createdAt,
    ...over,
  };
}

/** The shared page envelope, with the caller's whole unread count. */
export function notificationPage(
  content: NotificationRow[],
  over: Partial<Omit<NotificationPage, "content">> = {},
): NotificationPage {
  return {
    content,
    page: 0,
    size: 25,
    totalElements: content.length,
    unread: content.filter((n) => !n.read).length,
    ...over,
  };
}

/** The eight texts the server writes (specs/010 contracts/notifications-api.md), with their links. */
export const SERVER_TEXTS: Partial<NotificationRow>[] = [
  { type: "LEAVE_DECIDED", title: "Your leave was approved", message: "Your leave 12/10/2026 to 13/10/2026 was approved.", link: "/leave/history" },
  { type: "LEAVE_DECIDED", title: "Your leave was rejected", message: "Your leave 28/09/2026 was rejected: Annual exams.", link: "/leave/history" },
  { type: "LEAVE_DECIDED", title: "Your approved leave was revoked", message: "Your approved leave 26/10/2026 to 27/10/2026 was revoked: Staff shortage.", link: "/leave/history" },
  { type: "LEAVE_REQUESTED", title: "New leave request", message: "Tara asked for leave 12/10/2026 to 13/10/2026.", link: "/operations/leave" },
  { type: "LEAVE_CANCELLED", title: "Leave request withdrawn", message: "Tara cancelled their leave 12/10/2026 to 13/10/2026.", link: "/operations/leave" },
  { type: "ATTENDANCE_CHANGED", title: "Your attendance was updated", message: "Asha updated your attendance for 05/10/2026.", link: "/my-attendance?month=2026-10" },
  { type: "ATTENDANCE_MONTH_LOCKED", title: "Your attendance month was locked", message: "October 2026 is locked; changes now need a reopen.", link: "/my-attendance?month=2026-10" },
  { type: "ATTENDANCE_MONTH_REOPENED", title: "Your attendance month was reopened", message: "October 2026 was reopened: Correction needed.", link: "/my-attendance?month=2026-10" },
];

export const NOTIFICATIONS_ROUTE = "/account/notifications";

/** The model with the Notifications item's granted actions replaced (for example `["VIEW"]`: no Delete). */
export function withNotificationActions(model: AccessModel, actions: string[]): AccessModel {
  return {
    ...model,
    navigation: model.navigation.map((section) => ({
      ...section,
      items: section.items.map((item) => (item.route === NOTIFICATIONS_ROUTE ? { ...item, actions } : item)),
    })),
  };
}

/** The model without the Notifications item (View removed, or a role that never had it). */
export function withoutNotifications(model: AccessModel): AccessModel {
  return {
    ...model,
    navigation: model.navigation.map((section) => ({
      ...section,
      items: section.items.filter((item) => item.route !== NOTIFICATIONS_ROUTE),
    })),
  };
}
