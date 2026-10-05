import { Chip } from "@mui/material";
import { statusLabel } from "./contractStatus";
import type { ContractStatus } from "./schoolContractsApi";

const COLORS: Record<
  ContractStatus,
  "success" | "warning" | "info" | "default" | "error"
> = {
  ACTIVE: "success",
  ENDS_SOON: "warning",
  MOU_PENDING: "info",
  NONE: "error",
  ENDED: "default",
  CANCELLED: "default",
};

/** A contract's state as text and colour, so it is never colour alone (WCAG 2.2 AA). */
export function ContractStatusChip({ status }: { status: ContractStatus }) {
  return (
    <Chip size="small" label={statusLabel(status)} color={COLORS[status]} />
  );
}
