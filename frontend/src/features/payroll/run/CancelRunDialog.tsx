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
      <DialogTitle>Cancel this pay run?</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {runName} is kept with its lines and history, and a new run can then
          be started for the month.
        </DialogContentText>
        {serverError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {serverError}
          </Alert>
        )}
        <TextField
          label="Reason for cancelling"
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
          Keep the run
        </Button>
        <Button
          onClick={() => void confirm()}
          color="error"
          variant="contained"
          disabled={saving}
        >
          Cancel the run
        </Button>
      </DialogActions>
    </Dialog>
  );
}
