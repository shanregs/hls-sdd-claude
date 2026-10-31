import { formatRupees } from "../teachers/formatters";
import type { Cell, MonthState, Unit } from "./reportsApi";

export const RUN_STATE_TEXT: Record<string, string> = {
  DRAFT: "Draft",
  AWAITING_APPROVAL: "Awaiting approval",
  APPROVED: "Approved",
  PAID: "Paid",
};

const STATE_TEXT: Record<Exclude<Cell["state"], "VALUE">, string> = {
  NO_DATA: "No data",
  NOT_AVAILABLE: "Not available",
  NOT_MATCHED: "Not matched",
};

/** One cell as text: a figure by its unit, or the words for why there is none (never 0 for "missing"). */
export function cellText(cell: Cell, unit: Unit): string {
  if (cell.state !== "VALUE" || cell.value === null) {
    return STATE_TEXT[cell.state === "VALUE" ? "NO_DATA" : cell.state];
  }
  switch (unit) {
    case "RUPEES":
      return formatRupees(Number(cell.value));
    case "PERCENT":
      return `${cell.value}%`;
    case "TEXT":
      return RUN_STATE_TEXT[String(cell.value)] ?? String(cell.value);
    default:
      return String(cell.value);
  }
}

export const MONTH_STATE_LABELS: Record<MonthState, string> = {
  LOCKED: "Locked",
  OPEN: "Open",
  DRAFT: "Draft",
  APPROVED: "Approved",
  PAID: "Paid",
  NONE: "No state",
};
