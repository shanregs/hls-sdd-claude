import { StatusChip, type StatusKind } from "../../theme/StatusChip";
import { statusLabel } from "./contractStatus";
import type { ContractStatus } from "./schoolContractsApi";

const KINDS: Record<ContractStatus, StatusKind> = {
  ACTIVE: "approved",
  ENDS_SOON: "pending",
  MOU_PENDING: "neutral",
  NONE: "rejected",
  ENDED: "neutral",
  CANCELLED: "neutral",
};

/** A contract's state as text and colour, so it is never colour alone (WCAG 2.2 AA). */
export function ContractStatusChip({ status }: { status: ContractStatus }) {
  return <StatusChip kind={KINDS[status]} label={statusLabel(status)} />;
}
