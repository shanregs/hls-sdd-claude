/**
 * The screens this version of the app can show, keyed by the route the server's navigation uses.
 * A server menu item whose route is not listed here is simply not shown (spec FR-011). Later mobile
 * specs add their screens by adding routes here; nothing in this file knows about roles (FR-009).
 */
export type ScreenKey =
  | "home"
  | "profile"
  | "myAttendance"
  | "attendanceHistory"
  | "teacherAttendance"
  | "holidayCalendar"
  | "applyLeave"
  | "myLeaveHistory"
  | "leaveManagement"
  | "notifications";

const SCREEN_BY_ROUTE: Readonly<Record<string, ScreenKey>> = {
  "/dashboard": "home",
  "/account/profile": "profile",
  "/my-attendance": "myAttendance",
  "/my-attendance/history": "attendanceHistory",
  "/operations/teacher-attendance": "teacherAttendance",
  "/master-data/holiday-calendar": "holidayCalendar",
  "/leave/apply": "applyLeave",
  "/leave/history": "myLeaveHistory",
  "/operations/leave": "leaveManagement",
  "/account/notifications": "notifications",
};

export function screenFor(route: string): ScreenKey | undefined {
  return SCREEN_BY_ROUTE[route];
}

export const HOME_ROUTE = "/dashboard";
export const PROFILE_ROUTE = "/account/profile";
export const NOTIFICATIONS_ROUTE = "/account/notifications";
