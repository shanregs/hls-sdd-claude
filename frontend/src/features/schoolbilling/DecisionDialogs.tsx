import { useState } from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  TextField,
} from "@mui/material";
import { ConfirmDialog } from "../common/ConfirmDialog";

/** Confirms sending the MoU for approval; no document is needed. */
export function SubmitDialog({
  hasDocument,
  onCancel,
  onConfirm,
}: {
  hasDocument: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <ConfirmDialog
      title="Send this MoU for approval?"
      message={
        hasDocument
          ? "The Director and the Zone Manager of the School will be told."
          : "No signed document is attached yet. It can still be sent; the document can be added later. The Director and the Zone Manager of the School will be told."
      }
      confirmLabel="Send for approval"
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
}

export function ApproveDialog({
  hasDocument,
  onCancel,
  onConfirm,
}: {
  hasDocument: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <ConfirmDialog
      title="Approve this MoU?"
      message={
        hasDocument
          ? "The MoU is marked approved and the people who submitted it are told."
          : "No signed document is attached. The MoU will be approved without a document."
      }
      confirmLabel="Approve"
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
}

/** Rejecting needs a reason of at most 500 characters; the error of the server is shown inside the dialog. */
export function RejectDialog({
  onCancel,
  onConfirm,
}: {
  onCancel: () => void;
  /** Resolves with an error message, or null when it worked. */
  onConfirm: (reason: string) => Promise<string | null>;
}) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = () => {
    const trimmed = reason.trim();
    if (!trimmed) {
      setError("Give a reason.");
      return;
    }
    setBusy(true);
    void (async () => {
      const failure = await onConfirm(trimmed);
      setBusy(false);
      if (failure) {
        setError(failure);
      }
    })();
  };

  return (
    <Dialog open onClose={onCancel} aria-labelledby="reject-title" fullWidth>
      <DialogTitle id="reject-title">Reject this MoU?</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          The reason is shown to the people who submitted it. They can fix the
          document or the terms and send it again.
        </DialogContentText>
        <TextField
          autoFocus
          fullWidth
          multiline
          minRows={2}
          label="Reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 500 } }}
          error={error !== null}
          helperText={error ?? `${reason.length}/500`}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button
          variant="contained"
          color="error"
          onClick={submit}
          disabled={busy}
        >
          Reject
        </Button>
      </DialogActions>
    </Dialog>
  );
}
