import type { LeaveStatus } from "../api/leaveApi";
import { BUILTIN_STATUS_CHIPS } from "../theme/builtinClassic";
import type { ThemeMode } from "../theme/tokens";

export interface StatusLook {
  label: string;
  background: string;
  textColor: string;
  /** Spoken text, for example "Cancelled by the teacher" (the server's `cancelledBy` kind, shown as sent). */
  spoken: string;
}

/** Pending, approved and rejected use the shared status chip colours (spec 039 US5); cancelled stays neutral. */
const chip = (mode: ThemeMode, kind: "approved" | "pending" | "rejected") => ({
  background: BUILTIN_STATUS_CHIPS[mode][kind].background,
  text: BUILTIN_STATUS_CHIPS[mode][kind].text,
});

const CANCELLED = {
  light: { background: "#E0E0E0", text: "#2B2B2B" },
  dark: { background: "#424242", text: "#F0F0F0" },
};

function colorsFor(status: LeaveStatus, mode: ThemeMode): { background: string; text: string } {
  switch (status) {
    case "PENDING":
      return chip(mode, "pending");
    case "APPROVED":
      return chip(mode, "approved");
    case "REJECTED":
      return chip(mode, "rejected");
    case "CANCELLED":
      return CANCELLED[mode];
  }
}

const LABEL: Record<LeaveStatus, string> = {
  PENDING: "Pending",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

/** Label, colours and spoken text for a request's status, in the current theme. */
export function statusLook(status: LeaveStatus, cancelledBy: string | null, mode: ThemeMode): StatusLook {
  const colors = colorsFor(status, mode);
  return {
    label: LABEL[status],
    background: colors.background,
    textColor: colors.text,
    // The kind comes from the server ("who cancelled"); no role name is chosen or compared here.
    spoken: status === "CANCELLED" && cancelledBy ? `Cancelled by the ${cancelledBy.toLowerCase()}` : LABEL[status],
  };
}
