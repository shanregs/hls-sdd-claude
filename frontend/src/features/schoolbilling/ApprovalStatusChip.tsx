import { Chip } from "@mui/material";
import type { ApprovalState } from "./mouApprovalApi";
import { approvalLabel } from "./mouFormat";

const COLORS: Record<
  ApprovalState,
  "success" | "warning" | "info" | "default" | "error"
> = {
  NOT_APPLICABLE: "default",
  DRAFT: "default",
  PENDING: "warning",
  APPROVED: "success",
  APPROVED_WITHOUT_DOCUMENT: "info",
  APPROVED_BEFORE_APPROVAL_STEP: "success",
  REJECTED: "error",
  CANCELLED: "default",
};

/** The approval state as text and colour, so it is never colour alone (WCAG 2.2 AA). */
export function ApprovalStatusChip({ state }: { state: ApprovalState }) {
  return (
    <Chip
      size="small"
      label={approvalLabel(state)}
      color={COLORS[state]}
      variant={state === "DRAFT" ? "outlined" : "filled"}
    />
  );
}
