import { ApiError, NoConnectionError } from "../api/errors";
import type { DayFlag, MonthStatus } from "../api/managerAttendanceApi";
import type { MissingReason } from "../location/LocationGate";

/**
 * The words the Manager screens use (spec 033, contracts/screens.md). The server's `reason` is shown as it is for every
 * refusal; the app writes its own text only for what it finds out itself: no position, no connection, and the few
 * things it checks before sending anything.
 */

export interface LocationProblem {
  text: string;
  /** True when the Manager can fix it from the phone's settings for this app, so Open settings is offered. */
  canOpenSettings: boolean;
}

/** Why a check-in or check-out was not sent, in words the Manager can act on. */
export function locationMessage(reason: MissingReason): LocationProblem {
  switch (reason) {
    case "PERMISSION_DENIED":
      return {
        text: "Check-in needs your location. Allow location for HLS in your phone settings, then try again.",
        canOpenSettings: true,
      };
    case "SERVICES_OFF":
      return { text: "Turn on location (GPS) on your phone, then try again.", canOpenSettings: false };
    default:
      return {
        text: "Your phone could not find its position. Move to an open area and try again.",
        canOpenSettings: false,
      };
  }
}

export const NO_CONNECTION_TEXT = "No connection. Nothing was sent.";
export const GENERIC_FAILURE_TEXT = "Something went wrong. Nothing was saved. Please try again.";

/** The server's reason as it is, "no connection" for an unreachable server, and a plain fallback for anything else. */
export function managerRefusalText(error: unknown): string {
  if (error instanceof NoConnectionError) return NO_CONNECTION_TEXT;
  if (error instanceof ApiError && error.message.trim() !== "") return error.message;
  return GENERIC_FAILURE_TEXT;
}

const FLAG_TEXT: Record<DayFlag, string> = {
  LATE: "Late",
  NO_CHECK_OUT: "No check-out",
  ADDED_LATER: "Added later",
  CORRECTED: "Corrected by your Director",
  UNLINKED: "No planned activity linked",
};

export function flagText(flag: DayFlag): string {
  return FLAG_TEXT[flag] ?? flag;
}

const STATUS_TEXT: Record<MonthStatus, string> = {
  WORKED: "Worked",
  FIELD: "Field day",
  LEAVE: "Leave",
  HOLIDAY: "Holiday",
  WEEKLY_OFF: "Weekly off",
  NOT_EMPLOYED: "Not employed",
  PENDING: "Not yet",
  MISSED: "No check-in",
  ABSENT: "Absent",
  NO_CHECK_OUT: "No check-out",
};

export function statusText(status: MonthStatus): string {
  return STATUS_TEXT[status] ?? status;
}

const DAY_TYPE_TEXT = { OFFICE: "Office", FIELD: "Field" } as const;

export function dayTypeText(dayType: "OFFICE" | "FIELD" | null): string {
  return dayType ? DAY_TYPE_TEXT[dayType] : "";
}

/** A planned activity's kind in words ("COLLEGE_DRIVE" becomes "College drive"). */
export function kindText(kind: string): string {
  const words = kind.toLowerCase().replace(/_/g, " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** What a Field day still needs, or null when it has it. The only rule the app checks itself before sending. */
export function fieldDayProblem(activityCount: number, purpose: string): string | null {
  return activityCount > 0 || purpose.trim() !== ""
    ? null
    : "A Field day needs a planned activity or a purpose. Pick an activity or write a purpose.";
}
