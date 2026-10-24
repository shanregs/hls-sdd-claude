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
  listCandidates,
  raiseReplacement,
  type Candidate,
  type MovementRequest,
} from "./transfersApi";

/** Replace a placed Teacher with an unplaced one in the same position; the outgoing Teacher may move to a named School. */
export function ReplacementForm({
  onClose,
  onSaved,
}: {
  onClose: () => void;
  onSaved: (request: MovementRequest) => void;
}) {
  const { authFetch } = useAuth();
  const [teacherId, setTeacherId] = useState("");
  const [schoolId, setSchoolId] = useState("");
  const [replacementId, setReplacementId] = useState("");
  const [destination, setDestination] = useState("");
  const [effectiveOn, setEffectiveOn] = useState("");
  const [reason, setReason] = useState("AFTER_REVIEW");
  const [note, setNote] = useState("");
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [problems, setProblems] = useState<string[]>([]);
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // the School the Teacher is leaving is the one they are placed at, which the server resolves; the candidates query needs it
  useEffect(() => {
    if (!schoolId || !effectiveOn) return;
    let cancelled = false;
    (async () => {
      const list = await listCandidates(authFetch, schoolId, effectiveOn);
      if (!cancelled && list.ok) setCandidates(list.data);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, schoolId, effectiveOn]);

  useEffect(() => {
    if (!teacherId || !replacementId || !effectiveOn) return;
    let cancelled = false;
    (async () => {
      const result = await checkRequest(authFetch, {
        kind: "REPLACEMENT",
        teacherId,
        replacementTeacherId: replacementId,
        outgoingDestinationSchoolId: destination || null,
        effectiveOn,
      });
      if (cancelled) return;
      setProblems(result.ok ? result.data.problems : [result.reason]);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, teacherId, replacementId, destination, effectiveOn]);

  async function submit() {
    if (!teacherId) return setProblem("Choose the Teacher to replace.");
    if (!replacementId) return setProblem("Choose the replacement Teacher.");
    if (!effectiveOn) return setProblem("Choose the effective date.");
    if (effectiveOn < todayIso())
      return setProblem("The effective date cannot be before today.");
    if (NOTE_REQUIRED.includes(reason) && !note.trim())
      return setProblem("A note is required for this reason.");
    setProblem(null);
    setBusy(true);
    const result = await raiseReplacement(authFetch, {
      teacherId,
      replacementTeacherId: replacementId,
      outgoingDestinationSchoolId: destination || null,
      effectiveOn,
      reasonCategory: reason,
      note: note.trim() || null,
    });
    setBusy(false);
    if (result.ok) onSaved(result.data);
    else setProblem(result.reason);
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="replacement-title">
      <DialogTitle id="replacement-title">Replace a Teacher</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          <SchoolSelect label="School" value={schoolId} onChange={setSchoolId} />
          <TeacherSelect label="Teacher to replace" value={teacherId} onChange={setTeacherId} placedOnly />
          <TextField
            label="Effective date"
            type="date"
            required
            value={effectiveOn}
            onChange={(e) => setEffectiveOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: todayIso() } }}
            helperText={effectiveOn ? formatDate(effectiveOn) : undefined}
          />
          <TextField
            select
            required
            label="Replacement Teacher"
            value={replacementId}
            onChange={(e) => setReplacementId(e.target.value)}
            helperText={
              candidates.length === 0
                ? "Choose the School and date to see Teachers who are free"
                : "Active Teachers with no School on that date"
            }
          >
            {candidates.map((c) => (
              <MenuItem key={c.id} value={c.id}>
                {c.name}
              </MenuItem>
            ))}
          </TextField>
          <SchoolSelect
            label="Where the replaced Teacher goes"
            value={destination}
            onChange={setDestination}
            exclude={schoolId}
            required={false}
            allowNone
          />
          <TextField select label="Reason" value={reason} onChange={(e) => setReason(e.target.value)}>
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
        <Button variant="contained" disabled={busy} onClick={() => void submit()}>
          Request replacement
        </Button>
      </DialogActions>
    </Dialog>
  );
}
