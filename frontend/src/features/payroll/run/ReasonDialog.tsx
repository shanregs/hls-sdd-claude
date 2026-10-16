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
import { MAX_REASON, reasonProblem } from "./format";

export interface ReasonDialogProps {
  open: boolean;
  title: string;
  description: string;
  reasonLabel: string;
  confirmLabel: string;
  dismissLabel: string;
  confirmColor?: "primary" | "error";
  /** Resolves to an error message, or null when the action was done. */
  onConfirm: (reason: string) => Promise<string | null>;
  onClose: () => void;
}

/** A dialog that asks for a required reason (at most 500 characters) before it does something that needs one. */
export function ReasonDialog({
  open,
  title,
  description,
  reasonLabel,
  confirmLabel,
  dismissLabel,
  confirmColor = "primary",
  onConfirm,
  onClose,
}: ReasonDialogProps) {
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const problem = reasonProblem(reason);

  const confirm = async () => {
    setSubmitted(true);
    setServerError(null);
    if (problem) return;
    setSaving(true);
    const error = await onConfirm(reason.trim());
    setSaving(false);
    if (error) {
      setServerError(error);
      return;
    }
    setReason("");
    setSubmitted(false);
  };

  const close = () => {
    setReason("");
    setSubmitted(false);
    setServerError(null);
    onClose();
  };

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{description}</DialogContentText>
        {serverError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {serverError}
          </Alert>
        )}
        <TextField
          label={reasonLabel}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          required
          fullWidth
          multiline
          minRows={2}
          error={submitted && problem !== null}
          helperText={
            submitted && problem ? problem : `${reason.length}/${MAX_REASON}`
          }
          slotProps={{ htmlInput: { maxLength: MAX_REASON + 50 } }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={close} disabled={saving}>
          {dismissLabel}
        </Button>
        <Button
          onClick={() => void confirm()}
          color={confirmColor}
          variant="contained"
          disabled={saving}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
