import type { ApiResult } from "../common/masterDataApi";
import { ReasonDialog } from "./DecisionDialog";
import type { BillView, ClaimDetail } from "./expensesApi";

interface Props {
  bill: BillView;
  onRemove: (reason: string) => Promise<ApiResult<ClaimDetail>>;
  onDone: (claim: ClaimDetail) => void;
  onCancel: () => void;
}

/** An Admin or Director removes a bill with a reason; the removal stays visible on the claim. */
export function RemoveBillDialog({ bill, onRemove, onDone, onCancel }: Props) {
  return (
    <ReasonDialog
      title="Remove this bill"
      intro={`The bill "${bill.name}" will be hidden from the claim and the reason kept.`}
      label="Reason for removing"
      confirmLabel="Remove bill"
      onSubmit={onRemove}
      onDone={onDone}
      onCancel={onCancel}
    />
  );
}
