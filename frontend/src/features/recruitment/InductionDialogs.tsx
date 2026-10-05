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
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  createBatch,
  enrolRecruit,
  recordAttendance,
  signOff,
  type DayStatus,
  type RecruitRef,
  type RosterRow,
} from "./recruitmentApi";

function useBusy() {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return { error, setError, busy, setBusy };
}

export function BatchDialog({
  onClose,
  onSaved,
}: {
  onClose: () => void;
  onSaved: () => void;
}) {
  const { authFetch } = useAuth();
  const { error, setError, busy, setBusy } = useBusy();
  const [form, setForm] = useState({
    name: "",
    startsOn: "",
    endsOn: "",
    trainer: "",
    venueType: "PHYSICAL" as "PHYSICAL" | "VIRTUAL",
    venue: "",
    seatLimit: "20",
  });
  const set =
    (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  const save = async () => {
    if (!form.name.trim() || !form.startsOn || !form.endsOn) {
      setError("The name, start date and end date are required.");
      return;
    }
    const seats = Number(form.seatLimit);
    if (!Number.isInteger(seats) || seats < 1) {
      setError("The seat limit must be at least 1.");
      return;
    }
    setBusy(true);
    const result = await createBatch(authFetch, {
      name: form.name,
      startsOn: form.startsOn,
      endsOn: form.endsOn,
      trainer: form.trainer || undefined,
      venueType: form.venueType,
      venue: form.venue || undefined,
      seatLimit: seats,
    });
    setBusy(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="batch-title"
    >
      <DialogTitle id="batch-title">Create an induction batch</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField
            required
            label="Batch name"
            value={form.name}
            onChange={set("name")}
          />
          <TextField
            required
            type="date"
            label="Starts on"
            value={form.startsOn}
            onChange={set("startsOn")}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            required
            type="date"
            label="Ends on"
            value={form.endsOn}
            onChange={set("endsOn")}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Trainer"
            value={form.trainer}
            onChange={set("trainer")}
          />
          <TextField
            select
            label="Venue type"
            value={form.venueType}
            onChange={set("venueType")}
          >
            <MenuItem value="PHYSICAL">Physical</MenuItem>
            <MenuItem value="VIRTUAL">Virtual</MenuItem>
          </TextField>
          <TextField label="Venue" value={form.venue} onChange={set("venue")} />
          <TextField
            required
            type="number"
            label="Seat limit"
            value={form.seatLimit}
            onChange={set("seatLimit")}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void save()} disabled={busy}>
          Create batch
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function EnrolDialog({
  batchId,
  waiting,
  onClose,
  onSaved,
}: {
  batchId: string;
  waiting: RecruitRef[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { authFetch } = useAuth();
  const { error, setError, busy, setBusy } = useBusy();
  const [teacherId, setTeacherId] = useState("");

  const save = async () => {
    if (!teacherId) {
      setError("Choose a recruit.");
      return;
    }
    setBusy(true);
    const result = await enrolRecruit(authFetch, batchId, teacherId);
    setBusy(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="xs"
      aria-labelledby="enrol-title"
    >
      <DialogTitle id="enrol-title">Enrol a recruit</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          {waiting.length === 0 && (
            <Alert severity="info">No recruits are waiting for a batch.</Alert>
          )}
          <TextField
            select
            label="Recruit"
            value={teacherId}
            onChange={(e) => setTeacherId(e.target.value)}
          >
            {waiting.map((r) => (
              <MenuItem key={r.teacherId} value={r.teacherId}>
                {r.name}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void save()} disabled={busy}>
          Enrol
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function AttendanceDialog({
  row,
  minDate,
  maxDate,
  onClose,
  onSaved,
}: {
  row: RosterRow;
  minDate: string;
  maxDate: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { authFetch } = useAuth();
  const { error, setError, busy, setBusy } = useBusy();
  const today = new Date().toISOString().slice(0, 10);
  const [date, setDate] = useState(today < maxDate ? today : maxDate);
  const [status, setStatus] = useState<DayStatus>("PRESENT");
  const [reason, setReason] = useState("");

  const save = async () => {
    if (status === "ABSENT" && !reason.trim()) {
      setError("A reason is required for an absence.");
      return;
    }
    setBusy(true);
    const result = await recordAttendance(
      authFetch,
      row.enrolmentId,
      date,
      status,
      reason || undefined,
    );
    setBusy(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="xs"
      aria-labelledby="attend-title"
    >
      <DialogTitle id="attend-title">Attendance of {row.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField
            type="date"
            label="Date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            slotProps={{
              inputLabel: { shrink: true },
              htmlInput: { min: minDate, max: maxDate },
            }}
          />
          <TextField
            select
            label="Status"
            value={status}
            onChange={(e) => setStatus(e.target.value as DayStatus)}
          >
            <MenuItem value="PRESENT">Present (whole day)</MenuItem>
            <MenuItem value="HALF">Present (half day)</MenuItem>
            <MenuItem value="ABSENT">Absent</MenuItem>
          </TextField>
          {status === "ABSENT" && (
            <TextField
              required
              label="Reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void save()} disabled={busy}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function SignOffDialog({
  row,
  onClose,
  onSaved,
}: {
  row: RosterRow;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { authFetch } = useAuth();
  const { error, setError, busy, setBusy } = useBusy();
  const [result, setResult] = useState<"COMPLETED" | "NOT_COMPLETED">(
    "COMPLETED",
  );
  const [remarks, setRemarks] = useState("");

  const save = async () => {
    setBusy(true);
    const response = await signOff(
      authFetch,
      row.enrolmentId,
      result,
      remarks || undefined,
    );
    setBusy(false);
    if (!response.ok) {
      setError(response.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="xs"
      aria-labelledby="signoff-title"
    >
      <DialogTitle id="signoff-title">Sign off {row.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField
            select
            label="Result"
            value={result}
            onChange={(e) =>
              setResult(e.target.value as "COMPLETED" | "NOT_COMPLETED")
            }
          >
            <MenuItem value="COMPLETED">
              Completed (becomes active, ready to deploy)
            </MenuItem>
            <MenuItem value="NOT_COMPLETED">Not completed</MenuItem>
          </TextField>
          <TextField
            label="Remarks"
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void save()} disabled={busy}>
          Sign off
        </Button>
      </DialogActions>
    </Dialog>
  );
}
