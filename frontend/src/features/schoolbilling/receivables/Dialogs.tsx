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
import { useAuth } from "../../../auth/useAuth";
import { todayIso } from "../../teachers/formatters";
import {
  MODE_LABELS,
  raiseDues,
  recordCollection,
  saveSettings,
  type Collection,
  type RaiseResult,
  type Settings,
} from "./billingApi";
import { monthLabel } from "./money";

/** A dialog asking for a reason (cancel a due, reverse an entry) and optionally an amount (an adjustment). */
export function ReasonDialog({
  title,
  confirm,
  withAmount,
  onClose,
  onSubmit,
}: {
  title: string;
  confirm: string;
  withAmount?: boolean;
  onClose: () => void;
  onSubmit: (amount: string, reason: string) => Promise<string | null>;
}) {
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setProblem(null);
    const failure = await onSubmit(amount, reason);
    setBusy(false);
    if (failure) setProblem(failure);
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs" aria-labelledby="reason-title">
      <DialogTitle id="reason-title">{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          {withAmount && (
            <TextField
              label="Amount"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              slotProps={{ htmlInput: { min: 0, step: "0.01" } }}
              required
            />
          )}
          <TextField
            label="Reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            multiline
            minRows={2}
            required
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          onClick={() => void submit()}
          disabled={busy || reason.trim().length < 3 || (withAmount && !amount)}
        >
          {confirm}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** Record a collection from a School; a likely duplicate is warned about and can be confirmed. */
export function CollectionDialog({
  schoolId,
  onClose,
  onSaved,
}: {
  schoolId: string;
  onClose: () => void;
  onSaved: (collection: Collection) => void;
}) {
  const { authFetch } = useAuth();
  const [amount, setAmount] = useState("");
  const [receivedOn, setReceivedOn] = useState(todayIso());
  const [mode, setMode] = useState("BANK");
  const [reference, setReference] = useState("");
  const [receiver, setReceiver] = useState("");
  const [comment, setComment] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const [duplicate, setDuplicate] = useState(false);
  const [busy, setBusy] = useState(false);

  async function save(confirmDuplicate: boolean) {
    setBusy(true);
    setProblem(null);
    const result = await recordCollection(authFetch, {
      schoolId,
      amount,
      receivedOn,
      mode,
      reference,
      receiver,
      comment,
      confirmDuplicate,
    });
    setBusy(false);
    if (result.ok) {
      onSaved(result.data);
      return;
    }
    setDuplicate(Boolean(result.duplicate));
    setProblem(result.reason);
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="collection-title">
      <DialogTitle id="collection-title">Record a collection</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity={duplicate ? "warning" : "error"}>{problem}</Alert>}
          <TextField
            label="Amount"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            slotProps={{ htmlInput: { min: 0, step: "0.01" } }}
            required
          />
          <TextField
            label="Date received"
            type="date"
            value={receivedOn}
            onChange={(e) => setReceivedOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            required
          />
          <TextField select label="Mode" value={mode} onChange={(e) => setMode(e.target.value)}>
            {Object.entries(MODE_LABELS).map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="Reference" value={reference} onChange={(e) => setReference(e.target.value)} />
          <TextField label="Received by" value={receiver} onChange={(e) => setReceiver(e.target.value)} />
          <TextField label="Comment" value={comment} onChange={(e) => setComment(e.target.value)} multiline minRows={2} />
          <Typography variant="body2" color="text.secondary">
            The amount goes to the oldest monthly dues first; anything above what is owed is kept as advance credit.
          </Typography>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {duplicate ? (
          <Button variant="contained" color="warning" onClick={() => void save(true)} disabled={busy}>
            Record anyway
          </Button>
        ) : (
          <Button variant="contained" onClick={() => void save(false)} disabled={busy || !amount}>
            Record collection
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}

/** Raise the monthly dues of every School for a month and show what was raised, skipped and not billed. */
export function RaiseDuesDialog({
  month,
  onClose,
  onRaised,
}: {
  month: string;
  onClose: () => void;
  onRaised: () => void;
}) {
  const { authFetch } = useAuth();
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [result, setResult] = useState<RaiseResult | null>(null);

  async function raise() {
    setBusy(true);
    setProblem(null);
    const response = await raiseDues(authFetch, month);
    setBusy(false);
    if (response.ok) {
      setResult(response.data);
      onRaised();
    } else {
      setProblem(response.reason);
    }
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="raise-title">
      <DialogTitle id="raise-title">Raise monthly dues for {monthLabel(month)}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          {!result && (
            <Typography>
              One monthly due is raised for every School with Teachers mapped to a contract in {monthLabel(month)}. Schools already
              raised are skipped. Nothing is sent to the Schools.
            </Typography>
          )}
          {result && (
            <>
              <Alert severity="success">
                {result.raised.length} monthly dues raised, {result.skipped.length} skipped.
              </Alert>
              {result.notBilled.length > 0 && (
                <Alert severity="warning">
                  {result.notBilled.length} Teachers could not be billed. See the Not billed tab.
                </Alert>
              )}
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {!result && (
          <Button variant="contained" onClick={() => void raise()} disabled={busy}>
            Raise monthly dues
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}

/** The two billing settings. */
export function SettingsDialog({
  settings,
  onClose,
  onSaved,
}: {
  settings: Settings;
  onClose: () => void;
  onSaved: (settings: Settings) => void;
}) {
  const { authFetch } = useAuth();
  const [due, setDue] = useState(String(settings.paymentDueDays));
  const [escalation, setEscalation] = useState(String(settings.escalationDays));
  const [problem, setProblem] = useState<string | null>(null);

  async function save() {
    const result = await saveSettings(authFetch, {
      paymentDueDays: Number(due),
      escalationDays: Number(escalation),
    });
    if (result.ok) onSaved(result.data);
    else setProblem(result.reason);
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs" aria-labelledby="settings-title">
      <DialogTitle id="settings-title">Billing settings</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          <TextField
            label="Days from raising to the due date"
            type="number"
            value={due}
            onChange={(e) => setDue(e.target.value)}
          />
          <TextField
            label="Days after the due date before the Director is told"
            type="number"
            value={escalation}
            onChange={(e) => setEscalation(e.target.value)}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button variant="contained" onClick={() => void save()}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
