import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { lockMonth, type UnmarkedTeacher } from "./attendanceApi";
import { formatDate, monthLabel } from "./monthUtils";

interface LockMonthDialogProps {
  month: string;
  onClose: () => void;
  onLocked: () => void;
  /** Opens a Teacher's panel so the unmarked days can be fixed. */
  onOpenTeacher?: (teacherId: string, name: string) => void;
}

/** Confirms locking a month; a refusal lists who still has unmarked working days (spec 008 FR-016). */
export function LockMonthDialog({
  month,
  onClose,
  onLocked,
  onOpenTeacher,
}: LockMonthDialogProps) {
  const { authFetch } = useAuth();
  const [busy, setBusy] = useState(false);
  const [reason, setReason] = useState<string | null>(null);
  const [unmarked, setUnmarked] = useState<UnmarkedTeacher[]>([]);

  const confirm = async () => {
    setBusy(true);
    const result = await lockMonth(authFetch, month);
    setBusy(false);
    if (result.ok) {
      onLocked();
      return;
    }
    setReason(result.reason);
    setUnmarked(result.unmarked);
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Lock {monthLabel(month)}</DialogTitle>
      <DialogContent>
        <Typography sx={{ mb: 2 }}>
          Locking freezes every Teacher's rollup for this month and stops any
          further marking. A single Teacher can be reopened later with a reason.
        </Typography>
        {reason && (
          <Alert severity="error" role="alert" sx={{ mb: 2 }}>
            {reason}
          </Alert>
        )}
        {unmarked.length > 0 && (
          <ul aria-label="Teachers with unmarked days">
            {unmarked.map((t) => (
              <li key={t.teacherId}>
                {onOpenTeacher ? (
                  <Link
                    component="button"
                    type="button"
                    onClick={() => onOpenTeacher(t.teacherId, t.name)}
                  >
                    {t.name}
                  </Link>
                ) : (
                  t.name
                )}
                : {t.dates.map(formatDate).join(", ")}
              </li>
            ))}
          </ul>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={confirm} disabled={busy}>
          Lock month
        </Button>
      </DialogActions>
    </Dialog>
  );
}
