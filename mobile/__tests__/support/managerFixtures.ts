import type { AccessModel } from "../../src/api/accessModelApi";
import type {
  ManagerDay,
  ManagerMonth,
  MonthDay,
  PlannedActivity,
  Regularization,
  Today,
} from "../../src/api/managerAttendanceApi";
import type { ManagerLeave, ManagerLeaveType } from "../../src/api/managerLeaveApi";

const item = (label: string, route: string, actions = ["VIEW"]) => ({ label, route, actions });

export const CHECK_IN_ROUTE = "/manager-attendance/me";
export const MONTH_ROUTE = "/manager-attendance/me/month";
export const LEAVE_ROUTE = "/manager-attendance/me/leave";

/** A Manager's menu as the server's navigation catalog gives it (spec 032): MY ATTENDANCE with three entries. */
export function managerFieldModel(grants: { create?: boolean; remove?: boolean } = {}): AccessModel {
  const create = grants.create ?? true;
  const remove = grants.remove ?? true;
  const actions = ["VIEW", ...(create ? ["CREATE"] : []), ...(remove ? ["DELETE"] : [])];
  return {
    roles: ["MANAGER"],
    navigation: [
      { section: "Dashboard", items: [item("Dashboard", "/dashboard")] },
      {
        section: "MY ATTENDANCE",
        items: [
          item("Check-in", CHECK_IN_ROUTE, actions),
          item("My Month", MONTH_ROUTE, actions),
          item("My Leave", LEAVE_ROUTE, actions),
        ],
      },
      {
        section: "ACCOUNT",
        items: [
          item("Profile", "/account/profile", ["VIEW", "EDIT"]),
          item("Notifications", "/account/notifications", ["VIEW", "DELETE"]),
          item("Settings", "/account/settings"),
        ],
      },
    ],
    dataScope: { MY_MANAGER_ATTENDANCE: "OWN" },
  };
}

/** The server's day, with a Field/Office check-in by default. */
export function managerDay(overrides: Partial<ManagerDay> = {}): ManagerDay {
  return {
    id: "day-1",
    date: "2026-10-05",
    dayType: "OFFICE",
    purpose: null,
    checkInAt: "2026-10-05T04:12:00Z",
    late: false,
    checkOutAt: null,
    hasPhoto: false,
    leave: null,
    flags: [],
    activities: [],
    ...overrides,
  };
}

export function plannedActivity(overrides: Partial<PlannedActivity> = {}): PlannedActivity {
  return {
    kind: "COLLEGE_DRIVE",
    id: "act-1",
    date: "2026-10-05",
    place: "Anna Nagar College",
    status: "PLANNED",
    ...overrides,
  };
}

/** Today's view; by default a working day with no check-in and no plan. */
export function today(overrides: Partial<Today> = {}): Today {
  return {
    date: "2026-10-05",
    workingDay: true,
    missedCutoff: "10:30:00",
    lateAfter: "09:30:00",
    day: null,
    leave: null,
    missingCheckOutDates: [],
    plannedActivities: [],
    ...overrides,
  };
}

export function monthDay(date: string, overrides: Partial<MonthDay> = {}): MonthDay {
  return {
    date,
    status: "PENDING",
    id: null,
    dayType: null,
    purpose: null,
    checkInAt: null,
    checkOutAt: null,
    late: false,
    hasPhoto: false,
    leave: null,
    flags: [],
    activities: [],
    ...overrides,
  };
}

/** A month with the given days (the rest of the month is not listed) and the server's figures. */
export function managerMonth(overrides: Partial<ManagerMonth> = {}): ManagerMonth {
  return {
    month: "2026-10",
    locked: false,
    figures: {
      workingDays: 26,
      daysWorked: 3,
      leavePaid: 0,
      leaveUnpaid: 0,
      unpaid: 0,
      fieldDays: 1,
      pending: [],
    },
    figuresUnavailable: null,
    days: [],
    ...overrides,
  };
}

export function managerLeaveTypes(): ManagerLeaveType[] {
  return [
    { id: "lt-1", name: "Casual Leave", lossOfPay: false },
    { id: "lt-2", name: "Loss of Pay", lossOfPay: true },
  ];
}

export function managerLeave(overrides: Partial<ManagerLeave> = {}): ManagerLeave {
  return {
    id: "ml-1",
    leaveTypeId: "lt-1",
    leaveTypeName: "Casual Leave",
    lossOfPay: false,
    firstDate: "2026-10-12",
    lastDate: "2026-10-14",
    firstHalf: false,
    lastHalf: false,
    reason: "Family function",
    status: "PENDING",
    requestedAt: "2026-10-05T04:00:00Z",
    decidedAt: null,
    decisionReason: null,
    ...overrides,
  };
}

export function regularization(overrides: Partial<Regularization> = {}): Regularization {
  return {
    id: "rg-1",
    date: "2026-10-02",
    proposedTime: "09:15:00",
    dayType: "OFFICE",
    purpose: null,
    reason: "Phone was off",
    status: "PENDING",
    requestedAt: "2026-10-03T04:00:00Z",
    decidedAt: null,
    decisionReason: null,
    ...overrides,
  };
}
