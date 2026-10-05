import type { ContractStatus } from "./schoolContractsApi";

const LABELS: Record<ContractStatus, string> = {
  ACTIVE: "Active",
  ENDS_SOON: "Ends soon",
  MOU_PENDING: "MoU pending",
  NONE: "No MoU yet",
  ENDED: "Ended",
  CANCELLED: "Cancelled",
};

export function statusLabel(status: ContractStatus): string {
  return LABELS[status];
}
