import { ReasonDialog } from "./ReasonDialog";

interface Props {
  open: boolean;
  runName: string;
  /** Resolves to an error message, or null when the run was returned. */
  onConfirm: (reason: string) => Promise<string | null>;
  onClose: () => void;
}

/** Returning a run to draft needs a reason; the Admin who submitted it is told and sees the reason (spec 013b FR-012). */
export function ReturnRunDialog({ open, runName, onConfirm, onClose }: Props) {
  return (
    <ReasonDialog
      open={open}
      title="Return this pay run to draft?"
      description={`${runName} goes back to the Admin, who can change it and send it again.`}
      reasonLabel="Reason for returning"
      confirmLabel="Return to draft"
      dismissLabel="Keep waiting"
      onConfirm={onConfirm}
      onClose={onClose}
    />
  );
}
