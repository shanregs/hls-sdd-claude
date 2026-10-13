import { useState } from "react";
import {
  Alert,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { PendingRequests } from "./PendingRequests";
import { lockMonth, type LockResult } from "./managerAttendanceApi";

interface Props {
  month: string;
  onClose: () => void;
  /** Called when at least one Manager's month was locked, so the grid can reload. */
  onChanged: () => void;
}

/** Locks every Manager's month that can be locked and says why for the rest (spec 032 US6). */
export function LockMonthDialog({ month, onClose, onChanged }: Props) {
  const { authFetch } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<LockResult | null>(null);

  const confirm = async () => {
    setBusy(true);
    setError(null);
    const outcome = await lockMonth(authFetch, month);
    setBusy(false);
    if (!outcome.ok) {
      setError(outcome.reason);
      return;
    }
    setResult(outcome.data);
    if (outcome.data.locked.length > 0) onChanged();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="lock-month-title"
    >
      <DialogTitle id="lock-month-title">Lock {month} for payroll</DialogTitle>
      <DialogContent dividers>
        {!result && (
          <DialogContentText>
            This freezes every Manager&apos;s figures for {month} for payroll. A
            Manager with a leave or forgotten check-in request still waiting is
            skipped and listed. A locked month can be reopened with a reason.
          </DialogContentText>
        )}
        {error && (
          <Alert severity="error" role="alert" sx={{ mt: 2 }}>
            {error}
          </Alert>
        )}
        {busy && <CircularProgress aria-label="Locking" sx={{ mt: 2 }} />}
        {result && (
          <Stack spacing={2}>
            <Alert severity={result.locked.length > 0 ? "success" : "info"}>
              {result.locked.length} Manager
              {result.locked.length === 1 ? "" : "s"} locked
              {result.alreadyLocked > 0
                ? `, ${result.alreadyLocked} already locked`
                : ""}
              .
            </Alert>
            {result.refused.length > 0 && (
              <Stack spacing={1.5}>
                <Typography variant="subtitle2" component="h3">
                  Not locked ({result.refused.length})
                </Typography>
                {result.refused.map((r) => (
                  <Stack key={r.managerId} spacing={0.5}>
                    <Typography>
                      <strong>{r.name}</strong>: {r.reason}
                    </Typography>
                    <PendingRequests items={r.pending} showManager={false} />
                  </Stack>
                ))}
              </Stack>
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{result ? "Close" : "Cancel"}</Button>
        {!result && (
          <Button
            variant="contained"
            disabled={busy}
            onClick={() => void confirm()}
          >
            Lock all
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
