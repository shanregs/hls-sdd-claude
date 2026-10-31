import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { listTeachers, type TeacherSummary } from "../teachers/teachersApi";
import {
  getSupervisorLeaveTypes,
  recordSameDayLeave,
  type LeaveType,
} from "./leaveApi";

const MAX_REASON = 500;

interface Props {
  onClose: () => void;
  onRecorded: () => void;
}

/**
 * A Manager, Admin or Director records leave for a Teacher for today (spec 040 US1). Today is fixed: the server accepts
 * nothing else, and the leave is Approved the moment it is saved.
 */
export function RecordSameDayLeaveDialog({ onClose, onRecorded }: Props) {
  const { authFetch } = useAuth();
  const [teachers, setTeachers] = useState<TeacherSummary[] | null>(null);
  const [types, setTypes] = useState<LeaveType[] | null>(null);
  const [teacherId, setTeacherId] = useState("");
  const [leaveTypeId, setLeaveTypeId] = useState("");
  const [halfDay, setHalfDay] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const today = new Date().toLocaleDateString("en-CA");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [t, l] = await Promise.all([
        listTeachers(authFetch, {
          query: "",
          status: "",
          page: 0,
          size: 200,
        }),
        getSupervisorLeaveTypes(authFetch),
      ]);
      if (cancelled) return;
      if (!t.ok) setError(t.reason);
      else if (!l.ok) setError(l.reason);
      setTeachers(t.ok ? t.data.content : []);
      setTypes(l.ok ? l.data : []);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const ready = teacherId !== "" && leaveTypeId !== "" && reason.trim() !== "";

  const save = async () => {
    setBusy(true);
    setError(null);
    const result = await recordSameDayLeave(authFetch, {
      teacherId,
      leaveTypeId,
      firstDate: today,
      lastDate: today,
      halfDayStart: halfDay,
      halfDayEnd: false,
      reason: reason.trim(),
    });
    setBusy(false);
    if (result.ok) onRecorded();
    else setError(result.reason);
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="same-day-leave-title"
    >
      <DialogTitle id="same-day-leave-title">Record same-day leave</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Alert severity="info">
            Leave for today takes effect at once. Nobody needs to approve it.
          </Alert>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField
            select
            required
            size="small"
            label="Teacher"
            value={teacherId}
            onChange={(e) => setTeacherId(e.target.value)}
            disabled={teachers === null}
          >
            {(teachers ?? []).map((t) => (
              <MenuItem key={t.id} value={t.id}>
                {t.name}
                {t.school ? ` (${t.school.name})` : ""}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            required
            size="small"
            label="Leave type"
            value={leaveTypeId}
            onChange={(e) => setLeaveTypeId(e.target.value)}
            disabled={types === null}
          >
            {(types ?? []).map((t) => (
              <MenuItem key={t.id} value={t.id}>
                {t.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            size="small"
            label="Date"
            value={today}
            slotProps={{ input: { readOnly: true } }}
            helperText="Today"
          />
          <FormControlLabel
            control={
              <Checkbox
                checked={halfDay}
                onChange={(e) => setHalfDay(e.target.checked)}
              />
            }
            label="Half day"
          />
          <TextField
            required
            multiline
            minRows={2}
            size="small"
            label="Reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            slotProps={{ htmlInput: { maxLength: MAX_REASON } }}
            helperText={`${reason.length} of ${MAX_REASON}`}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={!ready || busy}>
          Record leave
        </Button>
      </DialogActions>
    </Dialog>
  );
}
