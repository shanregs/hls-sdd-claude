import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Link,
  MenuItem,
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
import { formatDate } from "../teachers/formatters";
import {
  DIVISOR_LABELS,
  getPayPolicy,
  PAY_MONTH_LABELS,
  recordPolicy,
  ROUNDING_LABELS,
  type PolicyResponse,
  type PolicyVersion,
  type RoundingRule,
} from "./salaryStructuresApi";
import {
  effectiveDateProblem,
  firstOfThisMonthIso,
  fractionProblem,
  MAX_NOTE,
  noteProblem,
  focusFirstInvalid,
} from "./validation";

const ROUTE = "/operations/salary-structures";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; policy: PolicyResponse };

function Rules({ version }: { version: PolicyVersion }) {
  return (
    <Stack component="dl" spacing={0.5} sx={{ m: 0 }}>
      <Box>
        <Typography component="dt" variant="body2" color="text.secondary">
          A day of loss of pay is the salary divided by
        </Typography>
        <Typography component="dd" sx={{ m: 0 }}>
          {DIVISOR_LABELS[version.lopDivisor] ?? version.lopDivisor}
        </Typography>
      </Box>
      <Box>
        <Typography component="dt" variant="body2" color="text.secondary">
          A half day counts as
        </Typography>
        <Typography component="dd" sx={{ m: 0 }}>
          {version.halfDayFraction} of a day
        </Typography>
      </Box>
      <Box>
        <Typography component="dt" variant="body2" color="text.secondary">
          The month&apos;s total deduction is rounded
        </Typography>
        <Typography component="dd" sx={{ m: 0 }}>
          {ROUNDING_LABELS[version.rounding]}
        </Typography>
      </Box>
      <Box>
        <Typography component="dt" variant="body2" color="text.secondary">
          Pay month
        </Typography>
        <Typography component="dd" sx={{ m: 0 }}>
          {PAY_MONTH_LABELS[version.payMonth] ?? version.payMonth}
        </Typography>
      </Box>
    </Stack>
  );
}

/**
 * The pay policy in effect, its full history, and the form that adds a new dated version (spec 013a US2). Versions are
 * never edited: the version in effect on a date is the latest one starting on or before it.
 */
export function PayPolicyPage() {
  const { authFetch } = useAuth();
  const canCreate = useGrantedActions(ROUTE).has("CREATE");

  const [state, setState] = useState<State>({ kind: "loading" });
  const [effectiveOn, setEffectiveOn] = useState(firstOfThisMonthIso());
  const [fraction, setFraction] = useState("");
  const [rounding, setRounding] = useState<RoundingRule>("NEAREST_RUPEE");
  const [note, setNote] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const load = useCallback(async () => {
    const result = await getPayPolicy(authFetch);
    setState(
      result.ok
        ? { kind: "ready", policy: result.data }
        : { kind: "error", reason: result.reason },
    );
    return result.ok ? result.data : null;
  }, [authFetch]);

  useEffect(() => {
    void (async () => {
      const policy = await load();
      // start the form from the rules in effect, so only what changes needs typing
      if (policy) {
        setFraction(policy.inEffect.halfDayFraction);
        setRounding(policy.inEffect.rounding);
      }
    })();
  }, [load]);

  const problems = {
    effectiveOn: effectiveDateProblem(effectiveOn),
    fraction: fractionProblem(fraction),
    note: noteProblem(note),
  };
  const show = (key: keyof typeof problems) =>
    submitted ? problems[key] : null;

  const submit = async () => {
    setSubmitted(true);
    setSaved(false);
    setServerError(null);
    if (state.kind !== "ready") return;
    if (problems.effectiveOn || problems.fraction || problems.note) return;
    setSaving(true);
    const result = await recordPolicy(authFetch, {
      effectiveOn,
      lopDivisor: state.policy.inEffect.lopDivisor,
      halfDayFraction: fraction.trim(),
      rounding,
      payMonth: state.policy.inEffect.payMonth,
      note: note.trim() === "" ? null : note.trim(),
    });
    setSaving(false);
    if (!result.ok) {
      setServerError(result.reason);
      return;
    }
    setNote("");
    setSubmitted(false);
    setSaved(true);
    await load();
  };

  if (state.kind === "loading") {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <CircularProgress aria-label="Loading the pay policy" />
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

  const { policy } = state;
  return (
    <Box>
      <Link component={RouterLink} to={ROUTE}>
        Salary Structures
      </Link>
      <Typography variant="h5" component="h1" sx={{ my: 1 }}>
        Pay policy
      </Typography>

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          In effect today
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          From {formatDate(policy.inEffect.effectiveOn)}
        </Typography>
        <Rules version={policy.inEffect} />
      </Paper>

      {canCreate && (
        <Paper
          variant="outlined"
          component="form"
          noValidate
          aria-label="Record a new policy version"
          sx={{ p: 2, mb: 2 }}
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
            focusFirstInvalid(e.currentTarget);
          }}
        >
          <Typography variant="h6" component="h2" gutterBottom>
            New version
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            A month is always calculated under the version in effect on its last
            day. Earlier versions are kept as they are.
          </Typography>
          {serverError && (
            <Alert severity="error" role="alert" sx={{ mb: 2 }}>
              {serverError}
            </Alert>
          )}
          {saved && (
            <Alert severity="success" sx={{ mb: 2 }}>
              The new version was recorded.
            </Alert>
          )}
          <Stack spacing={2}>
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
              required
              label="Half day counts as (fraction of a day)"
              value={fraction}
              onChange={(e) => setFraction(e.target.value)}
              error={Boolean(show("fraction"))}
              helperText={
                show("fraction") ??
                "More than 0 and less than 1, for example 0.5"
              }
              slotProps={{ htmlInput: { inputMode: "decimal" } }}
            />
            <TextField
              select
              required
              label="Round the month's total deduction"
              value={rounding}
              onChange={(e) => setRounding(e.target.value as RoundingRule)}
            >
              {(Object.keys(ROUNDING_LABELS) as RoundingRule[]).map((r) => (
                <MenuItem key={r} value={r}>
                  {ROUNDING_LABELS[r]}
                </MenuItem>
              ))}
            </TextField>
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
                Record version
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
          <Table size="small" aria-label="Pay policy history, newest first">
            <TableHead>
              <TableRow>
                <TableCell>From</TableCell>
                <TableCell>Half day</TableCell>
                <TableCell>Rounding</TableCell>
                <TableCell>Recorded by</TableCell>
                <TableCell>Note</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {policy.history.map((v) => (
                <TableRow key={v.id}>
                  <TableCell>{formatDate(v.effectiveOn)}</TableCell>
                  <TableCell>{v.halfDayFraction}</TableCell>
                  <TableCell>{ROUNDING_LABELS[v.rounding]}</TableCell>
                  <TableCell>
                    {v.recordedBy?.name ?? "Standard rules"}
                  </TableCell>
                  <TableCell>{v.note ?? ""}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Box>
  );
}
