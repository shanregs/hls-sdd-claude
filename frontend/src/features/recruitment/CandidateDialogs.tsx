import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  CRITERIA,
  addCandidate,
  assessCandidate,
  candidateHistory,
  importCandidates,
  setOutcome,
  type Candidate,
  type Criterion,
  type HistoryRow,
  type ImportResult,
  type Outcome,
} from "./recruitmentApi";

function useFormState() {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return { error, setError, busy, setBusy };
}

export function AddCandidateDialog({
  driveId,
  onClose,
  onSaved,
}: {
  driveId: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { authFetch } = useAuth();
  const { error, setError, busy, setBusy } = useFormState();
  const [form, setForm] = useState({
    name: "",
    phone: "",
    email: "",
    degree: "",
    year: "",
  });
  const set =
    (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  const save = async () => {
    if (!form.name.trim() || !form.phone.trim()) {
      setError("Name and phone are required.");
      return;
    }
    setBusy(true);
    const result = await addCandidate(authFetch, driveId, {
      name: form.name,
      phone: form.phone,
      email: form.email || undefined,
      degree: form.degree || undefined,
      year: form.year || undefined,
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
      maxWidth="xs"
      aria-labelledby="add-candidate-title"
    >
      <DialogTitle id="add-candidate-title">Add a candidate</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField
            required
            label="Name"
            value={form.name}
            onChange={set("name")}
          />
          <TextField
            required
            label="Phone"
            value={form.phone}
            onChange={set("phone")}
          />
          <TextField label="Email" value={form.email} onChange={set("email")} />
          <TextField
            label="Degree"
            value={form.degree}
            onChange={set("degree")}
          />
          <TextField label="Year" value={form.year} onChange={set("year")} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void save()} disabled={busy}>
          Add
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** Uploads a CSV (name, phone, email, degree, year, notes) and lists every row that could not be saved. */
export function ImportDialog({
  driveId,
  onClose,
  onDone,
}: {
  driveId: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const { authFetch } = useAuth();
  const { error, setError, busy, setBusy } = useFormState();
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  const upload = async () => {
    if (!file) {
      setError("Choose a CSV file.");
      return;
    }
    setBusy(true);
    const response = await importCandidates(authFetch, driveId, file);
    setBusy(false);
    if (!response.ok) {
      setError(response.reason);
      return;
    }
    setError(null);
    setResult(response.data);
    onDone();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="import-title"
    >
      <DialogTitle id="import-title">Import candidates from CSV</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2">
            The first row must be the header: name,phone,email,degree,year,notes
            (name and phone are required).
          </Typography>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <input
            type="file"
            accept=".csv,text/csv,text/plain"
            aria-label="CSV file"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
          {result && (
            <Alert
              severity={result.errors.length === 0 ? "success" : "warning"}
              role="status"
            >
              Saved {result.saved}, skipped {result.skipped} as duplicates
              {result.errors.length > 0
                ? `, ${result.errors.length} rows with problems:`
                : "."}
              <List dense>
                {result.errors.map((e) => (
                  <ListItem key={`${e.row}-${e.reason}`} disableGutters>
                    <ListItemText primary={`Row ${e.row}: ${e.reason}`} />
                  </ListItem>
                ))}
              </List>
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          onClick={() => void upload()}
          disabled={busy}
        >
          Import
        </Button>
      </DialogActions>
    </Dialog>
  );
}

const OUTCOMES: Outcome[] = ["SELECTED", "WAITLISTED", "REJECTED"];

export function OutcomeDialog({
  candidate,
  onClose,
  onSaved,
}: {
  candidate: Candidate;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { authFetch } = useAuth();
  const { error, setError, busy, setBusy } = useFormState();
  const [outcome, setChoice] = useState<Outcome | "">(candidate.outcome ?? "");
  const [note, setNote] = useState("");

  const save = async () => {
    if (!outcome) {
      setError("Choose an outcome.");
      return;
    }
    setBusy(true);
    const result = await setOutcome(
      authFetch,
      candidate.id,
      outcome,
      note || undefined,
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
      aria-labelledby="outcome-title"
    >
      <DialogTitle id="outcome-title">Outcome for {candidate.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField
            select
            label="Outcome"
            value={outcome}
            onChange={(e) => setChoice(e.target.value as Outcome)}
          >
            {OUTCOMES.map((o) => (
              <MenuItem key={o} value={o}>
                {o.charAt(0) + o.slice(1).toLowerCase()}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
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

export function AssessmentDialog({
  candidate,
  onClose,
  onSaved,
}: {
  candidate: Candidate;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { authFetch } = useAuth();
  const { error, setError, busy, setBusy } = useFormState();
  const [scores, setScores] = useState<Partial<Record<Criterion, string>>>({});
  const [remarks, setRemarks] = useState("");

  const save = async () => {
    const parsed: Partial<Record<Criterion, number>> = {};
    for (const c of CRITERIA) {
      const raw = scores[c];
      if (raw) {
        const n = Number(raw);
        if (!Number.isInteger(n) || n < 1 || n > 5) {
          setError("Each score must be a whole number from 1 to 5.");
          return;
        }
        parsed[c] = n;
      }
    }
    if (Object.keys(parsed).length === 0) {
      setError("Give a score for at least one criterion.");
      return;
    }
    setBusy(true);
    const result = await assessCandidate(
      authFetch,
      candidate.id,
      parsed,
      remarks || undefined,
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
      aria-labelledby="assess-title"
    >
      <DialogTitle id="assess-title">Assess {candidate.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          {CRITERIA.map((c) => (
            <TextField
              key={c}
              label={`${c.charAt(0) + c.slice(1).toLowerCase()} (1 to 5)`}
              type="number"
              value={scores[c] ?? ""}
              onChange={(e) =>
                setScores((s) => ({ ...s, [c]: e.target.value }))
              }
              slotProps={{ htmlInput: { min: 1, max: 5 } }}
            />
          ))}
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
          Save assessment
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function HistoryDialog({
  candidate,
  onClose,
}: {
  candidate: Candidate;
  onClose: () => void;
}) {
  const { authFetch } = useAuth();
  const [rows, setRows] = useState<HistoryRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      const result = await candidateHistory(authFetch, candidate.id);
      if (result.ok) setRows(result.data);
      else setError(result.reason);
    })();
  }, [authFetch, candidate.id]);

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="history-title"
    >
      <DialogTitle id="history-title">History of {candidate.name}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" role="alert">
            {error}
          </Alert>
        )}
        {rows === null && !error && (
          <Typography role="status">Loading...</Typography>
        )}
        {rows?.length === 0 && (
          <Typography>No outcome or assessment yet.</Typography>
        )}
        <List dense>
          {rows?.map((r, i) => (
            <ListItem key={i} disableGutters>
              <ListItemText
                primary={`${r.kind}: ${r.value}`}
                secondary={`${new Date(r.at).toLocaleString("en-IN")} by ${r.byName ?? "unknown"}${r.note ? ` - ${r.note}` : ""}`}
              />
            </ListItem>
          ))}
        </List>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
