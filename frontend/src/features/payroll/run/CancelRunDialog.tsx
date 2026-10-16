import { ReasonDialog } from "./ReasonDialog";

interface Props {
  open: boolean;
  /** "March 2026 (Primary run)" - shown so the person knows which run they are cancelling. */
  runName: string;
  /** Resolves to an error message, or null when the run was cancelled. */
  onConfirm: (reason: string) => Promise<string | null>;
  onClose: () => void;
}

/** Cancelling a draft needs a reason; the run, its lines and its history are kept (spec 013b FR-009). */
export function CancelRunDialog({ open, runName, onConfirm, onClose }: Props) {
  return (
    <ReasonDialog
      open={open}
      title="Cancel this pay run?"
      description={`${runName} is kept with its lines and history, and a new run can then be started for the month.`}
      reasonLabel="Reason for cancelling"
      confirmLabel="Cancel the run"
      dismissLabel="Keep the run"
      confirmColor="error"
      onConfirm={onConfirm}
      onClose={onClose}
    />
  );
}
