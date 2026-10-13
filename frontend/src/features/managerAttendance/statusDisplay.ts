/** How a Manager's day status is shown (spec 032 US5): a word and a short code, never colour alone. */
export interface StatusStyle {
  label: string;
  code: string;
  color: "default" | "success" | "info" | "warning" | "error";
}

const STYLES: Record<string, StatusStyle> = {
  WORKED: { label: "Worked", code: "W", color: "success" },
  FIELD: { label: "Field", code: "F", color: "success" },
  LEAVE: { label: "Leave", code: "L", color: "info" },
  ABSENT: { label: "Absent", code: "A", color: "error" },
  MISSED: { label: "Missed check-in", code: "M", color: "error" },
  NO_CHECK_OUT: { label: "No check-out", code: "N", color: "warning" },
  PENDING: { label: "Pending", code: "P", color: "default" },
  HOLIDAY: { label: "Holiday", code: "H", color: "default" },
  WEEKLY_OFF: { label: "Weekly off", code: "O", color: "default" },
  NOT_EMPLOYED: { label: "Not employed", code: "-", color: "default" },
};

export function statusStyle(status: string): StatusStyle {
  return STYLES[status] ?? { label: status, code: "?", color: "default" };
}

export const FLAG_LABELS: Record<string, string> = {
  LATE: "Late",
  CORRECTED: "Corrected",
  ADDED_LATER: "Added later",
  UNLINKED: "Not linked to a planned activity",
  NO_CHECK_OUT: "No check-out",
};

export function flagLabel(flag: string): string {
  return FLAG_LABELS[flag] ?? flag.replace(/_/g, " ").toLowerCase();
}

export function timeOf(iso: string | null): string {
  return iso
    ? new Date(iso).toLocaleTimeString("en-IN", {
        hour: "2-digit",
        minute: "2-digit",
      })
    : "";
}

export function figure(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}
