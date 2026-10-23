import type { ApiResult } from "../common/masterDataApi";
import { ReasonDialog } from "./DecisionDialog";
import type { ClaimDetail } from "./expensesApi";

interface Props {
  onReverse: (reason: string) => Promise<ApiResult<ClaimDetail>>;
  onDone: (claim: ClaimDetail) => void;
  onCancel: () => void;
}

/** Reverse an approval before the claim is paid; it goes back to Submitted and the claimant is told why. */
export function ReverseDialog({ onReverse, onDone, onCancel }: Props) {
  return (
    <ReasonDialog
      title="Reverse this approval"
      intro="The claim goes back to Submitted and leaves the pay run offer. The claimant is told why."
      label="Reason for reversing"
      confirmLabel="Reverse approval"
      onSubmit={onReverse}
      onDone={onDone}
      onCancel={onCancel}
    />
  );
}
