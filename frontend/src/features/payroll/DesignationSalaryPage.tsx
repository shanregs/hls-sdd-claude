import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Link,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate, formatRupees } from "../teachers/formatters";
import {
  getDesignationSalary,
  KIND_LABELS,
  recordSalary,
  type DesignationSalaryDetail,
} from "./salaryStructuresApi";
import {
  amountProblem,
  effectiveDateProblem,
  firstOfThisMonthIso,
  MAX_NOTE,
  noteProblem,
  focusFirstInvalid,
} from "./validation";

const ROUTE = "/operations/salary-structures";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; detail: DesignationSalaryDetail };

/**
 * One designation's salary history, newest first, and the form that records a new salary (spec 013a US1, US4). A
 * recorded row is never edited: a correction is a new row with the same or a later date.
 */
export function DesignationSalaryPage() {
  const { designationId = "" } = useParams();
  const { authFetch } = useAuth();
  const canCreate = useGrantedActions(ROUTE).has("CREATE");

  const [state, setState] = useState<State>({ kind: "loading" });
  const [amount, setAmount] = useState("");
  const [effectiveOn, setEffectiveOn] = useState(firstOfThisMonthIso());
  const [note, setNote] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const load = useCallback(async () => {
    const result = await getDesignationSalary(authFetch, designationId);
    setState(
      result.ok
        ? { kind: "ready", detail: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, designationId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- the screen loads its own data on mount
    void load();
  }, [load]);

  const problems = {
    amount: amountProblem(amount),
    effectiveOn: effectiveDateProblem(effectiveOn),
    note: noteProblem(note),
  };
  const show = (key: keyof typeof problems) =>
    submitted ? problems[key] : null;

  const submit = async () => {
    setSubmitted(true);
    setSaved(false);
    setServerError(null);
    if (problems.amount || problems.effectiveOn || problems.note) return;
    setSaving(true);
    const result = await recordSalary(authFetch, designationId, {
      amount: amount.trim(),
      effectiveOn,
      note: note.trim() === "" ? null : note.trim(),
    });
    setSaving(false);
    if (!result.ok) {
      setServerError(result.reason);
      return;
    }
    setAmount("");
    setNote("");
    setSubmitted(false);
    setSaved(true);
    await load();
  };

  if (state.kind === "loading") {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <CircularProgress aria-label="Loading the designation's salary" />
      </Box>
    );
  }
  if (state.kind === "error") {
    return (
      <Box>
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {state.reason}
        </Alert>
        <Link component={RouterLink} to={ROUTE}>
          Back to Salary Structures
        </Link>
      </Box>
    );
  }

  const { detail } = state;
  return (
    <Box>
      <Link component={RouterLink} to={ROUTE}>
        Salary Structures
      </Link>
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", my: 1 }}>
        <Typography variant="h5" component="h1">
          {detail.name}
        </Typography>
        <Chip size="small" label={KIND_LABELS[detail.kind]} />
        {detail.retired && <Chip size="small" label="Retired" />}
      </Stack>

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          Suggested default in effect today
        </Typography>
        {detail.inEffect ? (
          <Typography>
            {formatRupees(Number(detail.inEffect.amount))} a month, from{" "}
            {formatDate(detail.inEffect.effectiveOn)}
          </Typography>
        ) : (
          <Typography color="text.secondary">No default yet</Typography>
        )}
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          {detail.defaultNote} Everyone is paid their own salary, set on the
          People tab.
        </Typography>
        {detail.missingSalary.flag && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            {detail.missingSalary.people}{" "}
            {detail.missingSalary.people === 1 ? "person holds" : "people hold"}{" "}
            this designation without a salary of their own.
          </Alert>
        )}
      </Paper>

      {canCreate && (
        <Paper
          variant="outlined"
          component="form"
          noValidate
          aria-label="Record a suggested default"
          sx={{ p: 2, mb: 2 }}
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
            focusFirstInvalid(e.currentTarget);
          }}
        >
          <Typography variant="h6" component="h2" gutterBottom>
            Record a suggested default
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            The new default applies from its date. Earlier rows are kept as they
            are, and a mistake is fixed by recording a new row. It changes
            nobody&apos;s pay.
          </Typography>
          {serverError && (
            <Alert severity="error" role="alert" sx={{ mb: 2 }}>
              {serverError}
            </Alert>
          )}
          {saved && (
            <Alert severity="success" sx={{ mb: 2 }}>
              The default was recorded.
            </Alert>
          )}
          <Stack spacing={2}>
            <TextField
              required
              label="Suggested monthly salary (₹)"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              error={Boolean(show("amount"))}
              helperText={show("amount") ?? "For example 30000 or 30000.50"}
              slotProps={{ htmlInput: { inputMode: "decimal" } }}
            />
            <TextField
              required
              type="date"
              label="Effective from"
              value={effectiveOn}
              onChange={(e) => setEffectiveOn(e.target.value)}
              error={Boolean(show("effectiveOn"))}
              helperText={
                show("effectiveOn") ??
                "Not earlier than the first day of this month"
              }
              slotProps={{
                inputLabel: { shrink: true },
                htmlInput: { min: firstOfThisMonthIso() },
              }}
            />
            <TextField
              multiline
              minRows={2}
              label="Note (optional)"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              error={Boolean(show("note"))}
              helperText={
                show("note") ?? `${note.length} of ${MAX_NOTE} characters`
              }
            />
            <Box>
              <Button type="submit" variant="contained" disabled={saving}>
                Record default
              </Button>
            </Box>
          </Stack>
        </Paper>
      )}

      <Typography variant="h6" component="h2" gutterBottom>
        History
      </Typography>
      <Paper variant="outlined">
        <TableContainer>
          <Table size="small" aria-label="Default history, newest first">
            <TableHead>
              <TableRow>
                <TableCell>Salary</TableCell>
                <TableCell>From</TableCell>
                <TableCell>Recorded by</TableCell>
                <TableCell>Recorded on</TableCell>
                <TableCell>Note</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {detail.history.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5}>
                    No salary has been recorded.
                  </TableCell>
                </TableRow>
              )}
              {detail.history.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>{formatRupees(Number(row.amount))}</TableCell>
                  <TableCell>{formatDate(row.effectiveOn)}</TableCell>
                  <TableCell>{row.recordedBy.name}</TableCell>
                  <TableCell>
                    {new Date(row.recordedAt).toLocaleString("en-IN")}
                  </TableCell>
                  <TableCell>{row.note ?? ""}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Box>
  );
}
