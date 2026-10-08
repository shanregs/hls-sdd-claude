import { ApiError, NoConnectionError } from "../api/errors";

export type NotificationActionName = "read" | "readAll" | "delete" | "clear";

export const NOTIFICATION_GONE_TEXT = "This notification no longer exists.";

/** True when the server says the notification is not there (deleted elsewhere, or not the caller's). */
export function isGone(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

/** Plain words for a failed action; nothing is shown as done (spec 021 FR-011). */
export function notificationFailureText(action: NotificationActionName, error: unknown): string {
  if (error instanceof NoConnectionError) return "No connection. Nothing was changed.";
  if ((action === "delete" || action === "clear") && error instanceof ApiError && error.status === 403) {
    return "You're not allowed to delete notifications.";
  }
  switch (action) {
    case "read":
      return "Couldn't mark it as read. Try again.";
    case "readAll":
      return "Couldn't mark them as read. Try again.";
    case "delete":
      return "Couldn't delete it. Try again.";
    case "clear":
      return "Couldn't clear the read notifications. Try again.";
  }
}
