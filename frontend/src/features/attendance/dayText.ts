import type { DayView } from "./attendanceApi";

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
      return "Weekly off";
    case "NON_WORKING":
      return "Non-working date";
    case "FUTURE":
      return "Not yet";
  }
}
