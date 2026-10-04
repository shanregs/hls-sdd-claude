import { Chip } from "@mui/material";
import type { LeaveStatus } from "./leaveApi";

const COLOURS: Record<
  LeaveStatus,
  "default" | "warning" | "success" | "error"
> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "error",
  CANCELLED: "default",
};

const LABELS: Record<LeaveStatus, string> = {
  PENDING: "Pending",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

/** A request's status as a labelled chip (the text, not only the colour, carries the meaning). */
export function LeaveStatusChip({ status }: { status: LeaveStatus }) {
  return (
    <Chip
      size="small"
      label={LABELS[status]}
      color={COLOURS[status]}
      variant="outlined"
    />
  );
}
