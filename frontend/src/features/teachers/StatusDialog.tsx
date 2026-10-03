import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { todayIso } from "./formatters";
import {
  changeTeacherStatus,
  STATUS_LABELS,
  type TeacherStatus,
  type TeacherSummary,
} from "./teachersApi";

interface StatusDialogProps {
  teacher: TeacherSummary;
  onClose: () => void;
  onSaved: () => void;
}

/** Moves a Teacher along the allowed status paths (FR-011); only the allowed next statuses are offered. */
export function StatusDialog({ teacher, onClose, onSaved }: StatusDialogProps) {
  const { authFetch } = useAuth();
  const [status, setStatus] = useState<TeacherStatus | "">("");
  const [effectiveOn, setEffectiveOn] = useState(todayIso());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    if (!status) {
      setError("Choose the new status.");
      return;
    }
    setSaving(true);
    const result = await changeTeacherStatus(
      authFetch,
      teacher.id,
      status,
      effectiveOn,
    );
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Change status of {teacher.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2">
            Currently {STATUS_LABELS[teacher.status]}.
          </Typography>
          {status === "EXITED" && (
            <Alert severity="warning">
              Exit is final. The teacher&apos;s school placement ends and their
              account link is released. A returning person is entered as a new
              teacher.
            </Alert>
          )}
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          {teacher.allowedNextStatuses.length === 0 ? (
            <Typography>No further status changes are possible.</Typography>
          ) : (
            <TextField
              select
              required
              label="New status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as TeacherStatus);
                setError(null);
              }}
            >
              {teacher.allowedNextStatuses.map((next) => (
                <MenuItem key={next} value={next}>
                  {STATUS_LABELS[next]}
                </MenuItem>
              ))}
            </TextField>
          )}
          <TextField
            label="Effective date"
            type="date"
            value={effectiveOn}
            onChange={(e) => setEffectiveOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={save}
          disabled={saving || teacher.allowedNextStatuses.length === 0}
        >
          Change status
        </Button>
      </DialogActions>
    </Dialog>
  );
}
