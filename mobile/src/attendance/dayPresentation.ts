import type { DayView, StatusCategory } from "../api/attendanceApi";
import { WEEKDAY_SHORT, formatLongDate, weekdayIndex } from "../formats/dates";
import type { ThemeMode } from "../theme/tokens";

/** Whose eyes the day is shown to: the Teacher's own screens, or a supervisor's. */
export type Viewer = "self" | "supervisor";

export interface DayLook {
  /** Short text in the cell: the status code, "H" for a holiday, the weekday for a weekly off. */
  letter: string;
  /** True for a half-day mark; the cell shows a marker next to the letter. */
  half: boolean;
  background: string;
  textColor: string;
  borderColor: string;
  /** Spoken label: weekday, date, state, status, day value and who set it. */
  label: string;
}

interface Colors {
  background: string;
  text: string;
}

/** Colours per state, with the same meaning in both themes and at least AA contrast for the text. */
const LIGHT: Record<string, Colors> = {
  WORKED: { background: "#C8E6C9", text: "#1B5E20" },
  LEAVE: { background: "#FFE0B2", text: "#7A3300" },
  TRAINING: { background: "#BBDEFB", text: "#0D3A7A" },
  NON_WORKING: { background: "#E0E0E0", text: "#2B2B2B" },
  WEEKLY_OFF: { background: "#EEEEEE", text: "#424242" },
  NOT_PLACED: { background: "#F5F5F5", text: "#616161" },
  OPEN: { background: "transparent", text: "#1B1F24" },
};

const DARK: Record<string, Colors> = {
  WORKED: { background: "#1B5E20", text: "#E3F4E4" },
  LEAVE: { background: "#7A3300", text: "#FFE9D0" },
  TRAINING: { background: "#0D3A7A", text: "#DCEBFF" },
  NON_WORKING: { background: "#424242", text: "#F0F0F0" },
  WEEKLY_OFF: { background: "#2B2B2B", text: "#D6D6D6" },
  NOT_PLACED: { background: "#1F2328", text: "#A8B0BA" },
  OPEN: { background: "transparent", text: "#E6E9ED" },
};

const BORDER_LIGHT = "rgba(0, 0, 0, 0.25)";
const BORDER_DARK = "rgba(255, 255, 255, 0.3)";

function colorsFor(key: string, mode: ThemeMode): Colors {
  return (mode === "dark" ? DARK : LIGHT)[key] ?? (mode === "dark" ? DARK : LIGHT).OPEN;
}

function categoryKey(category: StatusCategory): string {
  return category;
}

function setBy(day: DayView, viewer: Viewer): string {
  const mark = day.mark;
  if (!mark) return "";
  if (viewer === "self" && mark.setByKind === "SELF") return "set by you";
  return `set by ${mark.setByName}`;
}

/** What a day looks like and how it is announced, from the server's own day entry. */
export function presentDay(day: DayView, mode: ThemeMode, viewer: Viewer): DayLook {
  const date = formatLongDate(day.date);
  const border = mode === "dark" ? BORDER_DARK : BORDER_LIGHT;
  const done = (colors: Colors, letter: string, spoken: string, half = false): DayLook => ({
    letter,
    half,
    background: colors.background,
    textColor: colors.text,
    borderColor: border,
    label: `${date}, ${spoken}`,
  });

  switch (day.state) {
    case "MARKED": {
      const mark = day.mark;
      if (!mark) {
        return done(colorsFor("OPEN", mode), "–", "not marked");
      }
      const half = mark.dayValue === 0.5;
      const value = half ? "half day" : "whole day";
      return done(
        colorsFor(categoryKey(mark.category), mode),
        mark.code,
        `${mark.codeName}, ${value}, ${setBy(day, viewer)}`,
        half,
      );
    }
    case "UNMARKED":
      return done(colorsFor("OPEN", mode), "–", "not marked");
    case "NOT_PLACED":
      return done(colorsFor("NOT_PLACED", mode), "·", "not placed in a school");
    case "WEEKLY_OFF":
      return done(colorsFor("WEEKLY_OFF", mode), WEEKDAY_SHORT[weekdayIndex(day.date)], "weekly off");
    case "NON_WORKING":
      return done(colorsFor("NON_WORKING", mode), "H", "holiday");
    case "FUTURE":
      return done(colorsFor("OPEN", mode), "", "future day");
  }
}

/** The Holiday Calendar's look: holidays and weekly offs marked, every other day a plain working day. */
export function presentCalendarDay(day: DayView, mode: ThemeMode): DayLook {
  if (day.state === "WEEKLY_OFF" || day.state === "NON_WORKING") return presentDay(day, mode, "self");
  const open = colorsFor("OPEN", mode);
  return {
    letter: "",
    half: false,
    background: open.background,
    textColor: open.text,
    borderColor: mode === "dark" ? BORDER_DARK : BORDER_LIGHT,
    label: `${formatLongDate(day.date)}, working day`,
  };
}
