import type { MonthDay, MonthStatus } from "../api/managerAttendanceApi";
import { formatLongDate } from "../formats/dates";
import { formatBusinessTime } from "../formats/manager";
import { dayTypeText, flagText, statusText } from "../manager/managerMessages";
import type { ThemeMode } from "../theme/tokens";
import type { DayLook } from "./dayPresentation";

interface Colors {
  background: string;
  text: string;
}

/** Colours per status, the same meaning in both themes, with at least AA contrast for the text on its background. */
const LIGHT: Record<MonthStatus, Colors> = {
  WORKED: { background: "#C8E6C9", text: "#1B5E20" },
  FIELD: { background: "#BBDEFB", text: "#0D3A7A" },
  LEAVE: { background: "#FFE0B2", text: "#7A3300" },
  HOLIDAY: { background: "#E0E0E0", text: "#2B2B2B" },
  WEEKLY_OFF: { background: "#EEEEEE", text: "#424242" },
  NOT_EMPLOYED: { background: "#F5F5F5", text: "#616161" },
  PENDING: { background: "transparent", text: "#1B1F24" },
  MISSED: { background: "#FFCDD2", text: "#7F0000" },
  ABSENT: { background: "#FFCDD2", text: "#7F0000" },
  NO_CHECK_OUT: { background: "#FFF0C2", text: "#5A4100" },
};

const DARK: Record<MonthStatus, Colors> = {
  WORKED: { background: "#1B5E20", text: "#E3F4E4" },
  FIELD: { background: "#0D3A7A", text: "#DCEBFF" },
  LEAVE: { background: "#7A3300", text: "#FFE9D0" },
  HOLIDAY: { background: "#424242", text: "#F0F0F0" },
  WEEKLY_OFF: { background: "#2B2B2B", text: "#D6D6D6" },
  NOT_EMPLOYED: { background: "#1F2328", text: "#A8B0BA" },
  PENDING: { background: "transparent", text: "#E6E9ED" },
  MISSED: { background: "#7F0000", text: "#FFE5E5" },
  ABSENT: { background: "#7F0000", text: "#FFE5E5" },
  NO_CHECK_OUT: { background: "#5A4100", text: "#FFF0C2" },
};

/** The letter in the cell, so a day is never told by colour alone. */
const LETTER: Record<MonthStatus, string> = {
  WORKED: "W",
  FIELD: "F",
  LEAVE: "L",
  HOLIDAY: "H",
  WEEKLY_OFF: "Off",
  NOT_EMPLOYED: "–",
  PENDING: "·",
  MISSED: "M",
  ABSENT: "A",
  NO_CHECK_OUT: "C",
};

const BORDER_LIGHT = "rgba(0, 0, 0, 0.25)";
const BORDER_DARK = "rgba(255, 255, 255, 0.3)";

export const MANAGER_LIGHT = LIGHT;
export const MANAGER_DARK = DARK;

/**
 * What a Manager's day looks like and how it is announced, from the server's own entry: its status, times, kind of day
 * and flags. The app works none of them out (spec 033 FR-010).
 */
export function presentManagerDay(day: MonthDay, mode: ThemeMode): DayLook {
  const colors = (mode === "dark" ? DARK : LIGHT)[day.status];
  const parts = [formatLongDate(day.date), statusText(day.status).toLowerCase()];
  if (day.checkInAt) {
    parts.push(`checked in ${formatBusinessTime(day.checkInAt)}`);
    if (day.dayType) parts.push(dayTypeText(day.dayType).toLowerCase());
  }
  if (day.checkOutAt) parts.push(`checked out ${formatBusinessTime(day.checkOutAt)}`);
  if (day.leave) parts.push(day.leave.fraction === 0.5 ? "half day leave" : "leave");
  // a flag that only repeats the status ("no check-out" on a NO_CHECK_OUT day) is said once
  for (const flag of day.flags) {
    const text = flagText(flag).toLowerCase();
    if (!parts.includes(text)) parts.push(text);
  }
  const noCheckOut = day.status === "NO_CHECK_OUT" || day.flags.includes("NO_CHECK_OUT");
  return {
    letter: `${LETTER[day.status]}${noCheckOut && day.status !== "NO_CHECK_OUT" ? "*" : ""}`,
    half: day.leave?.fraction === 0.5,
    background: colors.background,
    textColor: colors.text,
    borderColor: mode === "dark" ? BORDER_DARK : BORDER_LIGHT,
    label: parts.join(", "),
  };
}
