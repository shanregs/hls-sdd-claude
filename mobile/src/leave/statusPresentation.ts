import type { LeaveStatus } from "../api/leaveApi";
import type { ThemeMode } from "../theme/tokens";

export interface StatusLook {
  label: string;
  background: string;
  textColor: string;
  /** Spoken text, for example "Cancelled by the teacher" (the server's `cancelledBy` kind, shown as sent). */
  spoken: string;
}

const LIGHT: Record<LeaveStatus, { background: string; text: string }> = {
  PENDING: { background: "#FFE0B2", text: "#7A3300" },
  APPROVED: { background: "#C8E6C9", text: "#1B5E20" },
  REJECTED: { background: "#FFCDD2", text: "#7F1D1D" },
  CANCELLED: { background: "#E0E0E0", text: "#2B2B2B" },
};

const DARK: Record<LeaveStatus, { background: string; text: string }> = {
  PENDING: { background: "#7A3300", text: "#FFE9D0" },
  APPROVED: { background: "#1B5E20", text: "#E3F4E4" },
  REJECTED: { background: "#7F1D1D", text: "#FFE4E4" },
  CANCELLED: { background: "#424242", text: "#F0F0F0" },
};

const LABEL: Record<LeaveStatus, string> = {
  PENDING: "Pending",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

/** Label, colours and spoken text for a request's status, in the current theme. */
export function statusLook(status: LeaveStatus, cancelledBy: string | null, mode: ThemeMode): StatusLook {
  const colors = (mode === "dark" ? DARK : LIGHT)[status];
  return {
    label: LABEL[status],
    background: colors.background,
    textColor: colors.text,
    // The kind comes from the server ("who cancelled"); no role name is chosen or compared here.
    spoken: status === "CANCELLED" && cancelledBy ? `Cancelled by the ${cancelledBy.toLowerCase()}` : LABEL[status],
  };
}
