import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { deleteWorkingDays, type WorkingDaysRow } from "./schoolWorkingDaysApi";
import { FINALISED_NOTICE, formatFigure, monthName } from "./workingDaysRules";

interface DeleteDialogProps {
  row: WorkingDaysRow;
  onClose: () => void;
  /** The row is gone (deleted now, or already deleted by someone else). */
  onDeleted: (alreadyGone: boolean) => void;
  /** Someone changed the row since it was loaded; the caller shows the current figure. */
  onStale: (current: WorkingDaysRow) => void;
}

/** Names the School and month, says what happens afterwards and removes nothing until confirmed (FR-017, FR-028). */
export function DeleteDialog({
  row,
  onClose,
  onDeleted,
  onStale,
}: DeleteDialogProps) {
  const { authFetch } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const confirm = async () => {
    setBusy(true);
    setError(null);
    const result = await deleteWorkingDays(authFetch, row);
    setBusy(false);
    if (result.ok) {
      onDeleted(false);
      return;
    }
    if (result.code === "ROW_DELETED") {
      onDeleted(true);
      return;
    }
    if (result.code === "STALE" && result.current) {
      onStale(result.current);
      return;
    }
    setError(result.reason);
  };

  return (
    <Dialog open onClose={onClose} aria-labelledby="delete-working-days-title">
      <DialogTitle id="delete-working-days-title">
        Delete working days?
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" role="alert" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <DialogContentText>
          Delete the figure of {formatFigure(row.workingDays)} working days for{" "}
          {row.schoolName} in {monthName(row.month)} {row.year}? The month will
          use the calendar-derived count ({row.calendarDerived} working days)
          instead.
        </DialogContentText>
        <DialogContentText sx={{ mt: 1 }}>{FINALISED_NOTICE}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          color="error"
          onClick={confirm}
          disabled={busy}
        >
          Delete
        </Button>
      </DialogActions>
    </Dialog>
  );
}
