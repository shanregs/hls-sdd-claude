import type { DayView, GridCell } from "./attendanceApi";
import { WEEKDAY_LABELS, weekdayIndex } from "./monthUtils";

/** The words that describe a day, used on screen and as its accessible name (never colour alone). */
export function describeDay(day: DayView): string {
  switch (day.state) {
    case "MARKED": {
      const mark = day.mark!;
      const part = mark.dayValue < 1 ? "half day" : "whole day";
      const by =
        mark.setByKind === "SUPERVISOR"
          ? `set by ${mark.setByName}`
          : "set by teacher";
      return `${mark.codeName}, ${part}, ${by}`;
    }
    case "UNMARKED":
      return "Unmarked";
    case "NOT_PLACED":
      return "Not placed";
    case "WEEKLY_OFF":
      return `Weekly off, ${WEEKDAY_LABELS[weekdayIndex(day.date)]}`;
    case "NON_WORKING":
      return "Holiday";
    case "FUTURE":
      return "Not yet";
  }
}

/** The words that describe a grid cell (never colour alone). */
export function describeCell(cell: GridCell): string {
  switch (cell.state) {
    case "MARKED": {
      const part = (cell.dayValue ?? 1) < 1 ? "half day" : "whole day";
      const by =
        cell.setByKind === "SUPERVISOR"
          ? "set by supervisor"
          : "set by teacher";
      return `${cell.code}, ${part}, ${by}`;
    }
    case "UNMARKED":
      return "Unmarked";
    case "NOT_PLACED":
      return "Not placed";
    case "WEEKLY_OFF":
      return `Weekly off, ${WEEKDAY_LABELS[weekdayIndex(cell.date)]}`;
    case "NON_WORKING":
      return "Holiday";
    case "FUTURE":
      return "Not yet";
  }
}
