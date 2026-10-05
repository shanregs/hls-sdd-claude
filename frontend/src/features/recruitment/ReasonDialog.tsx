import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  TextField,
} from "@mui/material";

interface ReasonDialogProps {
  title: string;
  message?: string;
  label: string;
  confirmLabel: string;
  destructive?: boolean;
  /** Returns an error text to show, or null when the action went through. */
  onConfirm: (reason: string) => Promise<string | null>;
  onCancel: () => void;
}

/** Asks for a written reason before an action that needs one (cancel a drive, decline an offer, release a recruit). */
export function ReasonDialog({
  title,
  message,
  label,
  confirmLabel,
  destructive = false,
  onConfirm,
  onCancel,
}: ReasonDialogProps) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const confirm = async () => {
    if (!reason.trim()) {
      setError(`${label} is required.`);
      return;
    }
    setBusy(true);
    const failure = await onConfirm(reason.trim());
    setBusy(false);
    if (failure) setError(failure);
  };

  return (
    <Dialog
      open
      onClose={onCancel}
      fullWidth
      maxWidth="xs"
      aria-labelledby="reason-title"
    >
      <DialogTitle id="reason-title">{title}</DialogTitle>
      <DialogContent>
        {message && (
          <DialogContentText sx={{ mb: 2 }}>{message}</DialogContentText>
        )}
        {error && (
          <Alert severity="error" role="alert" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <TextField
          autoFocus
          fullWidth
          required
          label={label}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Back</Button>
        <Button
          variant="contained"
          color={destructive ? "error" : "primary"}
          onClick={() => void confirm()}
          disabled={busy}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
