import { useEffect, useState } from "react";
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
import { formatDate, todayIso } from "../teachers/formatters";
import { SchoolSelect, TeacherSelect } from "./pickers";
import {
  NOTE_REQUIRED,
  REASON_LABELS,
  checkRequest,
  listVacancies,
  raiseTransfer,
  type MovementRequest,
  type Vacancy,
} from "./transfersApi";

/** Request a transfer: Teacher, receiving School, position, date, reason, with the live problems from the server's check. */
export function TransferForm({
  onClose,
  onSaved,
}: {
  onClose: () => void;
  onSaved: (request: MovementRequest) => void;
}) {
  const { authFetch } = useAuth();
  const [teacherId, setTeacherId] = useState("");
  const [toSchoolId, setToSchoolId] = useState("");
  const [positionId, setPositionId] = useState("");
  const [effectiveOn, setEffectiveOn] = useState("");
  const [reason, setReason] = useState("MANAGER_REBALANCING");
  const [note, setNote] = useState("");
  const [vacancies, setVacancies] = useState<Vacancy[]>([]);
  const [problems, setProblems] = useState<string[]>([]);
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!toSchoolId || !effectiveOn) return;
    let cancelled = false;
    (async () => {
      const list = await listVacancies(authFetch, toSchoolId, effectiveOn);
      if (!cancelled && list.ok) setVacancies(list.data);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, toSchoolId, effectiveOn]);

  useEffect(() => {
    if (!teacherId || !toSchoolId || !effectiveOn) return;
    let cancelled = false;
    (async () => {
      const result = await checkRequest(authFetch, {
        kind: "TRANSFER",
        teacherId,
        toSchoolId,
        positionId: positionId || null,
        effectiveOn,
      });
      if (cancelled) return;
      setProblems(result.ok ? result.data.problems : [result.reason]);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, teacherId, toSchoolId, positionId, effectiveOn]);

  async function submit() {
    if (!teacherId) return setProblem("Choose the Teacher.");
    if (!toSchoolId) return setProblem("Choose the receiving School.");
    if (!effectiveOn) return setProblem("Choose the effective date.");
    if (effectiveOn < todayIso())
      return setProblem("The effective date cannot be before today.");
    if (NOTE_REQUIRED.includes(reason) && !note.trim())
      return setProblem("A note is required for this reason.");
    setProblem(null);
    setBusy(true);
    const result = await raiseTransfer(authFetch, {
      teacherId,
      toSchoolId,
      positionId: positionId || null,
      effectiveOn,
      reasonCategory: reason,
      note: note.trim() || null,
    });
    setBusy(false);
    if (result.ok) onSaved(result.data);
    else setProblem(result.reason);
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="transfer-title"
    >
      <DialogTitle id="transfer-title">Request a transfer</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          <TeacherSelect
            label="Teacher"
            value={teacherId}
            onChange={setTeacherId}
            placedOnly
          />
          <SchoolSelect
            label="Receiving School"
            value={toSchoolId}
            onChange={(v) => {
              setToSchoolId(v);
              setPositionId("");
            }}
          />
          {vacancies.length > 0 && (
            <TextField
              select
              label="Position"
              value={positionId}
              onChange={(e) => setPositionId(e.target.value)}
              helperText="Leave empty to use the next vacant position"
            >
              <MenuItem value="">Next vacant position</MenuItem>
              {vacancies.map((v) => (
                <MenuItem key={v.positionId} value={v.positionId}>
                  Position {v.number}
                </MenuItem>
              ))}
            </TextField>
          )}
          <TextField
            label="Effective date"
            type="date"
            required
            value={effectiveOn}
            onChange={(e) => setEffectiveOn(e.target.value)}
            slotProps={{
              inputLabel: { shrink: true },
              htmlInput: { min: todayIso() },
            }}
            helperText={effectiveOn ? formatDate(effectiveOn) : undefined}
          />
          <TextField
            select
            label="Reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          >
            {Object.entries(REASON_LABELS).map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Note"
            multiline
            minRows={2}
            required={NOTE_REQUIRED.includes(reason)}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 1000 } }}
          />
          {problems.length > 0 && (
            <Alert severity="warning" aria-label="Problems with this request">
              {problems.map((p) => (
                <div key={p}>{p}</div>
              ))}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          disabled={busy}
          onClick={() => void submit()}
        >
          Request transfer
        </Button>
      </DialogActions>
    </Dialog>
  );
}
