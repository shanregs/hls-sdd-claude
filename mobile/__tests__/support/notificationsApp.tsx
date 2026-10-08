import type { AccessModel } from "../../src/api/accessModelApi";
import { openLeaveScreen, type LeaveSetup, type OpenedLeaveApp } from "./leaveApp";
import { installNotifications, type NotificationsFake } from "./notificationsServer";

export interface NotificationsSetup extends LeaveSetup {
  notifications?: Partial<NotificationsFake>;
  model?: AccessModel;
  /** "home" stays on Home (for bell tests); "Notifications" opens the screen from the drawer (default). */
  open?: "Notifications" | "home";
}

export interface OpenedNotificationsApp extends OpenedLeaveApp {
  notifications: NotificationsFake;
}

/** Signs in with a stored session, installs the attendance, leave and notification routes, then opens Notifications or stays on Home. */
export async function openNotificationsScreen(setup: NotificationsSetup = {}): Promise<OpenedNotificationsApp> {
  let notifications: NotificationsFake | undefined;
  const opened = await openLeaveScreen(setup.open === "home" ? null : "Notifications", {
    ...setup,
    prepare: (server, attendance) => {
      notifications = installNotifications(server, setup.notifications);
      setup.prepare?.(server, attendance);
    },
  });
  return { ...opened, notifications: notifications as NotificationsFake };
}
