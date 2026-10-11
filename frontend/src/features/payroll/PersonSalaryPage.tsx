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
  getPerson,
  PERSON_KIND_LABELS,
  recordPersonSalary,
  setStaffEmployment,
  type PersonDetail,
  type PersonKind,
} from "./salaryStructuresApi";
import {
  amountProblem,
  effectiveDateProblem,
  firstOfThisMonthIso,
  MAX_NOTE,
  noteProblem,
} from "./validation";

const ROUTE = "/operations/salary-structures";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; detail: PersonDetail };

function isPersonKind(value: string | undefined): value is PersonKind {
  return value === "TEACHER" || value === "MANAGER" || value === "STAFF";
}

/**
 * One person's own salary (spec 013a User Story 5): the salary in effect, the history newest first, and the form that
 * records a new salary. A Teacher's salary is read-only here and changed on the Teachers screen. A staff member's
 * joining and exit dates are set here. The designation's amount is shown only as a suggestion.
 */
export function PersonSalaryPage() {
  const { kind: kindParam, personId = "" } = useParams();
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

  const [joining, setJoining] = useState("");
  const [exit, setExit] = useState("");
  const [datesError, setDatesError] = useState<string | null>(null);
  const [datesSaved, setDatesSaved] = useState(false);

  const kind: PersonKind | null = isPersonKind(kindParam) ? kindParam : null;

  const load = useCallback(async () => {
    if (!kind) {
      setState({ kind: "error", reason: "That kind of person is not known." });
      return;
    }
    const result = await getPerson(authFetch, kind, personId);
    if (result.ok) {
      setJoining(result.data.person.joiningDate ?? "");
      setExit(result.data.person.exitDate ?? "");
    }
    setState(
      result.ok
        ? { kind: "ready", detail: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, kind, personId]);

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
    if (!kind || problems.amount || problems.effectiveOn || problems.note) {
      return;
    }
    setSaving(true);
    const result = await recordPersonSalary(authFetch, kind, personId, {
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

  const saveDates = async () => {
    setDatesError(null);
    setDatesSaved(false);
    if (joining && exit && exit < joining) {
      setDatesError("The exit date cannot be before the joining date.");
      return;
    }
    const result = await setStaffEmployment(authFetch, personId, {
      joiningDate: joining || null,
      exitDate: exit || null,
    });
    if (!result.ok) {
      setDatesError(result.reason);
      return;
    }
    setDatesSaved(true);
    await load();
  };

  if (state.kind === "loading") {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <CircularProgress aria-label="Loading this person's salary" />
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
  const person = detail.person;
  const suggested = detail.suggestedDefault;
  return (
    <Box>
      <Link component={RouterLink} to={ROUTE}>
        Salary Structures
      </Link>
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: "center", my: 1, flexWrap: "wrap" }}
      >
        <Typography variant="h5" component="h1">
          {person.name}
        </Typography>
        <Chip size="small" label={PERSON_KIND_LABELS[person.kind]} />
        {person.designation && (
          <Chip size="small" variant="outlined" label={person.designation} />
        )}
        {!person.active && <Chip size="small" label="Not working here" />}
      </Stack>

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          Salary in effect today
        </Typography>
        {person.inEffect ? (
          <Typography>
            {formatRupees(Number(person.inEffect.amount))} a month, from{" "}
            {formatDate(person.inEffect.effectiveOn)}
          </Typography>
        ) : (
          <Alert severity="warning">
            No salary yet. This person cannot be paid until one is recorded.
          </Alert>
        )}
      </Paper>

      {person.kind === "TEACHER" && (
        <Alert severity="info" sx={{ mb: 2 }}>
          A Teacher&apos;s salary is changed on the{" "}
          <Link component={RouterLink} to="/master-data/teachers">
            Teachers screen
          </Link>
          , where it is kept with their record. It is shown here for reference.
        </Alert>
      )}

      {person.kind === "STAFF" && canCreate && (
        <Paper
          variant="outlined"
          component="form"
          noValidate
          aria-label="Joining and exit dates"
          sx={{ p: 2, mb: 2 }}
          onSubmit={(e) => {
            e.preventDefault();
            void saveDates();
          }}
        >
          <Typography variant="h6" component="h2" gutterBottom>
            Joining and exit dates
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Pay runs from the joining date to the exit date. Leave a date empty
            while it is not known.
          </Typography>
          {datesError && (
            <Alert severity="error" role="alert" sx={{ mb: 2 }}>
              {datesError}
            </Alert>
          )}
          {datesSaved && (
            <Alert severity="success" sx={{ mb: 2 }}>
              The dates were saved.
            </Alert>
          )}
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              type="date"
              label="Joining date"
              value={joining}
              onChange={(e) => setJoining(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              type="date"
              label="Exit date"
              value={exit}
              onChange={(e) => setExit(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <Box sx={{ alignSelf: "center" }}>
              <Button type="submit" variant="outlined">
                Save dates
              </Button>
            </Box>
          </Stack>
        </Paper>
      )}

      {person.salaryEditable && canCreate && (
        <Paper
          variant="outlined"
          component="form"
          noValidate
          aria-label="Record a salary"
          sx={{ p: 2, mb: 2 }}
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
        >
          <Typography variant="h6" component="h2" gutterBottom>
            Record a salary
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            This person&apos;s own monthly salary, from the date below. Earlier
            rows are kept as they are, and a mistake is fixed by recording a new
            row.
          </Typography>
          {suggested && (
            <Alert
              severity="info"
              sx={{ mb: 2 }}
              action={
                <Button
                  color="inherit"
                  size="small"
                  onClick={() =>
                    setAmount(suggested.amount.replace(/\.00$/, ""))
                  }
                >
                  Use suggestion
                </Button>
              }
            >
              The designation&apos;s suggested default is{" "}
              {formatRupees(Number(suggested.amount))}. It is only a suggestion
              and is not saved unless you record it.
            </Alert>
          )}
          {serverError && (
            <Alert severity="error" role="alert" sx={{ mb: 2 }}>
              {serverError}
            </Alert>
          )}
          {saved && (
            <Alert severity="success" sx={{ mb: 2 }}>
              The salary was recorded.
            </Alert>
          )}
          <Stack spacing={2}>
            <TextField
              required
              label="Monthly salary (₹)"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              error={Boolean(show("amount"))}
              helperText={show("amount") ?? "For example 50000 or 50000.50"}
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
                Record salary
              </Button>
            </Box>
          </Stack>
        </Paper>
      )}

      {person.kind !== "TEACHER" && (
        <>
          <Typography variant="h6" component="h2" gutterBottom>
            History
          </Typography>
          <Paper variant="outlined">
            <TableContainer>
              <Table size="small" aria-label="Salary history, newest first">
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
        </>
      )}
    </Box>
  );
}
