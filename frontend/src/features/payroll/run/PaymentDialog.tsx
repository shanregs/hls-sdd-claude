import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../../auth/useAuth";
import { formatDate } from "../../teachers/formatters";
import { dateTime, MAX_REFERENCE, paymentProblem, today } from "./format";
import {
  getPayment,
  PAYMENT_MODE_LABELS,
  recordPayment,
  type Payment,
  type PaymentEntry,
  type PaymentMode,
} from "./payrollRunApi";

interface Props {
  runId: string;
  lineId: string | null;
  personName: string;
  /** The Admin (Process) may record; anyone else with View only reads the entries. */
  canRecord: boolean;
  onClose: () => void;
  /** Called after an entry was saved, so the run's paid figures and the line can be read again. */
  onSaved: () => void;
}

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; payment: Payment };

function describe(entry: PaymentEntry): string {
  if (!entry.paid) return "Marked not paid";
  const mode = entry.mode ? PAYMENT_MODE_LABELS[entry.mode] : "";
  return `Paid on ${formatDate(entry.paidOn)} by ${mode}${entry.reference ? `, reference ${entry.reference}` : ""}`;
}

/**
 * Records, and corrects, the payment of one line of an approved run (spec 013b US7). Payment happens outside the
 * system: this only notes it. A correction is a new entry and the earlier ones stay in the list below.
 */
export function PaymentDialog({
  runId,
  lineId,
  personName,
  canRecord,
  onClose,
  onSaved,
}: Props) {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [paid, setPaid] = useState(true);
  const [paidOn, setPaidOn] = useState("");
  const [mode, setMode] = useState<PaymentMode | "">("");
  const [reference, setReference] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  useEffect(() => {
    if (!lineId) return;
    let cancelled = false;
    void (async () => {
      setState({ kind: "loading" });
      setServerError(null);
      setSubmitted(false);
      const result = await getPayment(authFetch, runId, lineId);
      if (cancelled) return;
      if (!result.ok) {
        setState({ kind: "error", reason: result.reason });
        return;
      }
      const current = result.data.current;
      setPaid(current ? current.paid : true);
      setPaidOn(current?.paidOn ?? today());
      setMode(current?.mode ?? "");
      setReference(current?.reference ?? "");
      setState({ kind: "ready", payment: result.data });
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, runId, lineId]);

  const problem = paymentProblem(paid, paidOn, mode, reference);

  const save = async () => {
    if (!lineId) return;
    setSubmitted(true);
    setServerError(null);
    if (problem) return;
    setSaving(true);
    const result = await recordPayment(authFetch, runId, lineId, {
      paid,
      paidOn: paid ? paidOn : null,
      mode: paid && mode !== "" ? mode : null,
      reference: paid && reference.trim() !== "" ? reference.trim() : null,
    });
    setSaving(false);
    if (!result.ok) {
      setServerError(result.reason);
      return;
    }
    setState({ kind: "ready", payment: result.data });
    setSubmitted(false);
    onSaved();
  };

  const payment = state.kind === "ready" ? state.payment : null;
  const history: PaymentEntry[] = payment
    ? [...(payment.current ? [payment.current] : []), ...payment.earlier]
    : [];

  return (
    <Dialog
      open={lineId !== null}
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="payment-title"
    >
      <DialogTitle id="payment-title">Payment for {personName}</DialogTitle>
      <DialogContent dividers>
        {state.kind === "loading" && (
          <Stack direction="row" sx={{ gap: 1.5, alignItems: "center" }}>
            <CircularProgress size={22} aria-label="Loading the payment" />
            <Typography>Loading the payment details…</Typography>
          </Stack>
        )}
        {state.kind === "error" && (
          <Alert severity="error">{state.reason}</Alert>
        )}
        {payment && (
          <Stack spacing={2}>
            <Typography color="text.secondary">
              Payment is made outside this system. Note here whether it was
              paid; the amounts of the run never change.
            </Typography>
            {serverError && <Alert severity="error">{serverError}</Alert>}
            {canRecord && (
              <Stack spacing={2}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={paid}
                      onChange={(e) => setPaid(e.target.checked)}
                    />
                  }
                  label={paid ? "Paid" : "Not paid"}
                />
                {paid && (
                  <>
                    <TextField
                      label="Payment date"
                      type="date"
                      value={paidOn}
                      onChange={(e) => setPaidOn(e.target.value)}
                      required
                      slotProps={{
                        inputLabel: { shrink: true },
                        htmlInput: { max: today() },
                      }}
                    />
                    <TextField
                      select
                      label="Paid by"
                      value={mode}
                      onChange={(e) => setMode(e.target.value as PaymentMode)}
                      required
                    >
                      {(
                        Object.entries(PAYMENT_MODE_LABELS) as [
                          PaymentMode,
                          string,
                        ][]
                      ).map(([value, label]) => (
                        <MenuItem key={value} value={value}>
                          {label}
                        </MenuItem>
                      ))}
                    </TextField>
                    <TextField
                      label="Reference (optional)"
                      value={reference}
                      onChange={(e) => setReference(e.target.value)}
                      helperText={`${reference.length}/${MAX_REFERENCE}`}
                    />
                  </>
                )}
                {submitted && problem && (
                  <Alert severity="error">{problem}</Alert>
                )}
              </Stack>
            )}
            <Box component="section" aria-labelledby="payment-history">
              <Typography
                id="payment-history"
                variant="h3"
                sx={{ fontSize: "1rem", mb: 1 }}
              >
                Payment history
              </Typography>
              {history.length === 0 ? (
                <Typography>Nothing has been recorded yet.</Typography>
              ) : (
                <Box component="ul" sx={{ m: 0, pl: 3 }}>
                  {history.map((entry, index) => (
                    <li key={entry.id}>
                      {describe(entry)} (entered by {entry.enteredByName} on{" "}
                      {dateTime(entry.enteredAt)}
                      {index === 0 ? ", current" : ""})
                    </li>
                  ))}
                </Box>
              )}
            </Box>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          Close
        </Button>
        {canRecord && payment && (
          <Button
            variant="contained"
            onClick={() => void save()}
            disabled={saving}
          >
            {saving ? "Saving…" : "Save payment"}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
