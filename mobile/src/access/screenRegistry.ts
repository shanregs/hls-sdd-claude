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
  | "notifications"
  | "managerCheckIn"
  | "managerMonth"
  | "managerLeave"
  | "visits"
  | "tasks"
  | "myExpenses";

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
  "/manager-attendance/me": "managerCheckIn",
  "/manager-attendance/me/month": "managerMonth",
  "/manager-attendance/me/leave": "managerLeave",
  "/marketing/calendar": "visits",
  "/operations/tasks": "tasks",
  "/operations/my-expenses": "myExpenses",
};

export function screenFor(route: string): ScreenKey | undefined {
  return SCREEN_BY_ROUTE[route];
}

export const HOME_ROUTE = "/dashboard";
/** The Manager's own attendance (spec 033): the menu items the server offers under MY ATTENDANCE. */
export const MANAGER_CHECK_IN_ROUTE = "/manager-attendance/me";
/** The visit diary and the task list (spec 034): the menu items the server offers under MARKETING and OPERATIONS. */
export const VISITS_ROUTE = "/marketing/calendar";
export const TASKS_ROUTE = "/operations/tasks";
/** The claimant's own expenses (spec 036). The review item "/operations/expenses" is deliberately not known to the app. */
export const EXPENSES_ROUTE = "/operations/my-expenses";
export const PROFILE_ROUTE = "/account/profile";
export const NOTIFICATIONS_ROUTE = "/account/notifications";
