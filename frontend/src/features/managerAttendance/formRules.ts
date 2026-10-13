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
