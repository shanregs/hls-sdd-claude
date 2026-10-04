import { alpha, type Theme } from "@mui/material/styles";
import type { StatusCategory } from "./attendanceApi";
import { WEEKDAY_LABELS, weekdayIndex } from "./monthUtils";

/** The kinds of day that get their own colour (spec 008 FR-013). Colour always comes with text. */
export type DayKind = "off" | "holiday" | "leave" | "plain";

/** What a grid or calendar day needs to be styled; satisfied by both a grid cell and a day view. */
export interface DayFacts {
  date: string;
  state: string;
  /** The mark's status code letter, when marked. */
  code?: string | null;
  /** The mark's status category, when marked. */
  category?: StatusCategory | null;
  /** 1 for a whole day, 0.5 for a half day. */
  dayValue?: number | null;
}

export interface DayAppearance {
  kind: DayKind;
  /** The short text shown in the cell: the code, "H", or the weekday for an off day. */
  text: string;
}

/**
 * The kind and short text of a day: a weekly off day reads its weekday ("Sun"), a holiday reads "H",
 * and a mark shows its code, as a holiday when its status is non-working and as leave when its
 * category is leave (so Absent and any custom leave code are covered without listing letters).
 */
export function dayAppearance(day: DayFacts): DayAppearance {
  const half = (day.dayValue ?? 1) < 1 ? "½" : "";
  if (day.state === "MARKED") {
    const text = `${day.code ?? ""}${half}`;
    if (day.category === "NON_WORKING") return { kind: "holiday", text };
    if (day.category === "LEAVE") return { kind: "leave", text };
    return { kind: "plain", text };
  }
  if (day.state === "WEEKLY_OFF") {
    return { kind: "off", text: WEEKDAY_LABELS[weekdayIndex(day.date)] };
  }
  if (day.state === "NON_WORKING") return { kind: "holiday", text: "H" };
  return { kind: "plain", text: "" };
}

/** Palette colour behind each kind; "off" is a neutral tint, the others use the warning and error hues. */
const TINTS: Record<
  Exclude<DayKind, "plain">,
  (theme: Theme) => { light: string; dark: string }
> = {
  off: (t) => ({
    light: alpha(t.palette.text.primary, 0.08),
    dark: alpha(t.palette.text.primary, 0.14),
  }),
  holiday: (t) => ({
    light: alpha(t.palette.warning.main, 0.28),
    dark: alpha(t.palette.warning.main, 0.32),
  }),
  leave: (t) => ({
    light: alpha(t.palette.error.main, 0.16),
    dark: alpha(t.palette.error.main, 0.28),
  }),
};

/** The background for a kind of day, as an `sx` colour function; plain days keep the default. */
export function dayBackground(kind: DayKind) {
  return (theme: Theme): string | undefined => {
    if (kind === "plain") return undefined;
    const tint = TINTS[kind](theme);
    return theme.palette.mode === "dark" ? tint.dark : tint.light;
  };
}

/** The words for a kind of day, used by the legend and in accessible names. */
export const DAY_KIND_LABELS: Record<Exclude<DayKind, "plain">, string> = {
  off: "Weekly off",
  holiday: "Holiday",
  leave: "Leave or absent",
};
