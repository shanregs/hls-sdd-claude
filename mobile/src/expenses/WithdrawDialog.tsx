import { ReasonDialog } from "../leave/ReasonDialog";

interface Props {
  kind: "withdraw" | "delete";
  /** Sends the call; resolves with the refusal text to show, or null once the server accepted it. */
  onConfirm: () => Promise<string | null>;
  onCancel: () => void;
}

/** The confirmation for Withdraw (a Submitted claim goes back to Draft) and Delete (a Draft is removed). */
export function WithdrawDialog({ kind, onConfirm, onCancel }: Props) {
  return kind === "withdraw" ? (
    <ReasonDialog
      title="Withdraw this claim?"
      message="It goes back to Draft. You can change it and submit it again."
      confirmLabel="Withdraw"
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  ) : (
    <ReasonDialog
      title="Delete this claim?"
      message="This Draft and its bills are removed. This cannot be undone."
      confirmLabel="Delete"
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  );
}
