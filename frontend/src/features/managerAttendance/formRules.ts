import type { DayType, MonthDay } from "./managerAttendanceApi";

/** Input rules of the Manager attendance forms (spec 032 US5). The server checks the same rules again. */
export const MAX_REASON = 500;
export const MAX_PURPOSE = 500;

export interface RequestValues {
  date: string;
  time: string;
  dayType: DayType;
  purpose: string;
  reason: string;
}

export const EMPTY_REQUEST: RequestValues = {
  date: "",
  time: "09:00",
  dayType: "OFFICE",
  purpose: "",
  reason: "",
};

function nowHm(): string {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/** The first problem with a forgotten check-in request, or null. */
export function validateRequest(
  v: RequestValues,
  today: string,
): string | null {
  if (!v.date) return "Choose the day you forgot to check in.";
  if (v.date > today) return "The day cannot be in the future.";
  if (!v.time) return "Enter the time you arrived.";
  if (v.date === today && v.time > nowHm()) {
    return "The time cannot be later than now.";
  }
  if (v.dayType === "FIELD" && v.purpose.trim() === "") {
    return "A Field day needs a purpose.";
  }
  if (v.purpose.length > MAX_PURPOSE) {
    return `The purpose can be at most ${MAX_PURPOSE} characters.`;
  }
  if (v.reason.trim() === "") return "A reason is required.";
  if (v.reason.length > MAX_REASON) {
    return `The reason can be at most ${MAX_REASON} characters.`;
  }
  return null;
}

export interface CorrectionValues {
  dayType: DayType;
  purpose: string;
  checkIn: string;
  checkOut: string;
  clear: boolean;
  reason: string;
}

function hm(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export function correctionFor(day: MonthDay): CorrectionValues {
  return {
    dayType: day.dayType ?? "OFFICE",
    purpose: day.purpose ?? "",
    checkIn: hm(day.checkInAt),
    checkOut: hm(day.checkOutAt),
    clear: false,
    reason: "",
  };
}

/** The first problem with a correction, or null. */
export function validateCorrection(v: CorrectionValues): string | null {
  if (v.reason.trim() === "") return "A reason is required.";
  if (v.reason.length > MAX_REASON) {
    return `The reason can be at most ${MAX_REASON} characters.`;
  }
  if (v.clear) return null;
  if (v.dayType === "FIELD" && v.purpose.trim() === "") {
    return "A Field day needs a purpose.";
  }
  if (v.purpose.length > MAX_PURPOSE) {
    return `The purpose can be at most ${MAX_PURPOSE} characters.`;
  }
  if (v.checkIn === "") return "Enter the check-in time.";
  if (v.checkOut !== "" && v.checkOut < v.checkIn) {
    return "The check-out cannot be before the check-in.";
  }
  return null;
}

export interface LeaveValues {
  leaveTypeId: string;
  firstDate: string;
  lastDate: string;
  firstHalf: boolean;
  lastHalf: boolean;
  reason: string;
}

export const EMPTY_LEAVE: LeaveValues = {
  leaveTypeId: "",
  firstDate: "",
  lastDate: "",
  firstHalf: false,
  lastHalf: false,
  reason: "",
};

/** The first problem with a leave request, or null. Days off are checked by the server, which knows the calendar. */
export function validateLeave(v: LeaveValues): string | null {
  if (!v.leaveTypeId) return "Choose a leave type.";
  if (!v.firstDate) return "Choose the first day of leave.";
  if (!v.lastDate) return "Choose the last day of leave.";
  if (v.lastDate < v.firstDate) {
    return "The last day cannot be before the first day.";
  }
  if (v.firstDate === v.lastDate && v.firstHalf && v.lastHalf) {
    return "One day cannot be two half days. Choose one half day.";
  }
  if (v.reason.trim() === "") return "A reason is required.";
  if (v.reason.length > MAX_REASON) {
    return `The reason can be at most ${MAX_REASON} characters.`;
  }
  return null;
}

/** A reason asked for when rejecting or revoking. */
export function validateDecisionReason(reason: string): string | null {
  if (reason.trim() === "") return "A reason is required.";
  if (reason.length > MAX_REASON) {
    return `The reason can be at most ${MAX_REASON} characters.`;
  }
  return null;
}

export interface SettingsValues {
  missedCutoff: string;
  lateAfter: string;
}

/** The first problem with the cut-off and late time, or null. The server checks the same rules again. */
export function validateSettings(v: SettingsValues): string | null {
  if (!v.missedCutoff) return "Enter the cut-off time.";
  if (!v.lateAfter) return "Enter the late time.";
  if (v.lateAfter >= v.missedCutoff) {
    return "The late time must be before the cut-off time.";
  }
  return null;
}
