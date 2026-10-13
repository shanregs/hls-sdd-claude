import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControl,
  FormControlLabel,
  FormLabel,
  Paper,
  Radio,
  RadioGroup,
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
import { shiftMonth } from "../attendance/monthUtils";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate, todayIso } from "../teachers/formatters";
import {
  cancelRegularization,
  getMyMonth,
  listMyRegularizations,
  requestRegularization,
  type DayType,
  type MonthView,
  type Regularization,
} from "./managerAttendanceApi";
import {
  EMPTY_REQUEST,
  MAX_PURPOSE,
  MAX_REASON,
  validateRequest,
  type RequestValues,
} from "./formRules";
import { figure, flagLabel, statusStyle, timeOf } from "./statusDisplay";

const ROUTE = "/manager-attendance/me/month";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; view: MonthView };

function monthLabel(month: string): string {
  const [y, m] = month.split("-").map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString("en-IN", {
    month: "long",
    year: "numeric",
  });
}

/** A Manager's own month (spec 032 US5): a status for each day, the month figures and the forgotten check-in request. */
export function MyManagerMonthPage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(ROUTE);
  const canCreate = granted.has("CREATE");
  const canDelete = granted.has("DELETE");
  const today = todayIso();

  const [month, setMonth] = useState(today.slice(0, 7));
  const [state, setState] = useState<State>({ kind: "loading" });
  const [requests, setRequests] = useState<Regularization[]>([]);
  const [form, setForm] = useState<RequestValues>(EMPTY_REQUEST);
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [m, r] = await Promise.all([
      getMyMonth(authFetch, month),
      listMyRegularizations(authFetch),
    ]);
    setState(
      m.ok
        ? { kind: "ready", view: m.data }
        : { kind: "error", reason: m.reason },
    );
    if (r.ok) setRequests(r.data);
  }, [authFetch, month]);

  useEffect(() => {
    void (async () => {
      setState({ kind: "loading" });
      await load();
    })();
  }, [load]);

  const problem = validateRequest(form, today);
  const set = <K extends keyof RequestValues>(
    key: K,
    value: RequestValues[K],
  ) => setForm((prev) => ({ ...prev, [key]: value }));

  const submit = async () => {
    setTouched(true);
    setMessage(null);
    if (problem) return;
    setBusy(true);
    const result = await requestRegularization(authFetch, {
      date: form.date,
      proposedTime: form.time,
      dayType: form.dayType,
      purpose: form.dayType === "FIELD" ? form.purpose.trim() : null,
      reason: form.reason.trim(),
    });
    setBusy(false);
    if (!result.ok) {
      setMessage(result.reason);
      return;
    }
    setForm(EMPTY_REQUEST);
    setTouched(false);
    await load();
  };

  const cancel = async (id: string) => {
    setMessage(null);
    const result = await cancelRegularization(authFetch, id);
    if (!result.ok) setMessage(result.reason);
    else await load();
  };

  const pending = requests.filter((r) => r.status === "PENDING");

  return (
    <Box sx={{ maxWidth: 900 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        My month
      </Typography>

      <Stack direction="row" spacing={1} sx={{ mb: 2, alignItems: "center" }}>
        <Button
          onClick={() => setMonth((m) => shiftMonth(m, -1))}
          aria-label="Previous month"
        >
          Previous
        </Button>
        <Typography component="h2" variant="h6" aria-live="polite">
          {monthLabel(month)}
        </Typography>
        <Button
          onClick={() => setMonth((m) => shiftMonth(m, 1))}
          aria-label="Next month"
        >
          Next
        </Button>
      </Stack>

      {message && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {message}
        </Alert>
      )}

      {state.kind === "loading" && (
        <Box sx={{ p: 3, textAlign: "center" }}>
          <CircularProgress aria-label="Loading the month" />
        </Box>
      )}
      {state.kind === "error" && (
        <Alert severity="error" role="alert">
          {state.reason}
        </Alert>
      )}

      {state.kind === "ready" && (
        <>
          {state.view.locked && (
            <Alert severity="info" sx={{ mb: 2 }}>
              This month is locked for payroll. It can no longer be changed.
            </Alert>
          )}
          {state.view.figures ? (
            <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
              <Typography variant="h6" component="h2" gutterBottom>
                Month figures
              </Typography>
              <Stack direction="row" sx={{ gap: 3, flexWrap: "wrap" }}>
                <Fig
                  label="Working days"
                  value={state.view.figures.workingDays}
                />
                <Fig
                  label="Days worked"
                  value={state.view.figures.daysWorked}
                />
                <Fig label="Field days" value={state.view.figures.fieldDays} />
                <Fig label="Paid leave" value={state.view.figures.leavePaid} />
                <Fig
                  label="Unpaid leave"
                  value={state.view.figures.leaveUnpaid}
                />
                <Fig label="Unpaid days" value={state.view.figures.unpaid} />
              </Stack>
              {state.view.figures.pending.length > 0 && (
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ mt: 1 }}
                >
                  {state.view.figures.pending.length} day(s) are still to come
                  and not counted.
                </Typography>
              )}
            </Paper>
          ) : (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {state.view.figuresUnavailable ??
                "The month figures are not available."}
            </Alert>
          )}

          <TableContainer component={Paper} variant="outlined" sx={{ mb: 3 }}>
            <Table size="small" aria-label="Days of the month">
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>In</TableCell>
                  <TableCell>Out</TableCell>
                  <TableCell>Purpose</TableCell>
                  <TableCell>Notes</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {state.view.days.map((d) => {
                  const style = statusStyle(d.status);
                  return (
                    <TableRow key={d.date}>
                      <TableCell>{formatDate(d.date)}</TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          color={style.color}
                          label={style.label}
                          variant={
                            style.color === "default" ? "outlined" : "filled"
                          }
                        />
                      </TableCell>
                      <TableCell>{timeOf(d.checkInAt)}</TableCell>
                      <TableCell>{timeOf(d.checkOutAt)}</TableCell>
                      <TableCell>{d.purpose ?? ""}</TableCell>
                      <TableCell>
                        {d.flags.map((f) => flagLabel(f)).join(", ")}
                        {d.leave
                          ? `${d.flags.length > 0 ? ", " : ""}${
                              d.leave.fraction >= 1 ? "Full" : "Half"
                            } day ${d.leave.unpaid ? "unpaid " : ""}leave`
                          : ""}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      {canCreate && (
        <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
          <Typography variant="h6" component="h2" gutterBottom>
            Forgot to check in?
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Ask for a past day to be added. An Admin or Director decides.
          </Typography>
          <Stack
            spacing={2}
            component="form"
            noValidate
            onSubmit={(e) => e.preventDefault()}
          >
            {touched && problem && (
              <Alert severity="error" role="alert">
                {problem}
              </Alert>
            )}
            <TextField
              label="Day"
              type="date"
              value={form.date}
              onChange={(e) => set("date", e.target.value)}
              slotProps={{
                inputLabel: { shrink: true },
                htmlInput: { max: today },
              }}
            />
            <TextField
              label="Time you arrived"
              type="time"
              value={form.time}
              onChange={(e) => set("time", e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <FormControl>
              <FormLabel id="regularization-day-type">Day type</FormLabel>
              <RadioGroup
                row
                aria-labelledby="regularization-day-type"
                value={form.dayType}
                onChange={(e) => set("dayType", e.target.value as DayType)}
              >
                <FormControlLabel
                  value="OFFICE"
                  control={<Radio />}
                  label="Office"
                />
                <FormControlLabel
                  value="FIELD"
                  control={<Radio />}
                  label="Field"
                />
              </RadioGroup>
            </FormControl>
            {form.dayType === "FIELD" && (
              <TextField
                label="Purpose"
                multiline
                minRows={2}
                value={form.purpose}
                onChange={(e) => set("purpose", e.target.value)}
                helperText={`${form.purpose.length}/${MAX_PURPOSE}`}
              />
            )}
            <TextField
              label="Reason"
              multiline
              minRows={2}
              value={form.reason}
              onChange={(e) => set("reason", e.target.value)}
              helperText={`${form.reason.length}/${MAX_REASON}`}
            />
            <Box>
              <Button
                variant="contained"
                onClick={() => void submit()}
                disabled={busy}
              >
                Send request
              </Button>
            </Box>
          </Stack>
        </Paper>
      )}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          My requests
        </Typography>
        {requests.length === 0 ? (
          <Typography color="text.secondary">No requests yet.</Typography>
        ) : (
          <Stack
            spacing={1}
            component="ul"
            sx={{ listStyle: "none", p: 0, m: 0 }}
          >
            {requests.map((r) => (
              <Stack
                key={r.id}
                component="li"
                direction="row"
                sx={{ gap: 1, alignItems: "center", flexWrap: "wrap" }}
              >
                <Typography>
                  {formatDate(r.date)} at {r.proposedTime.slice(0, 5)}
                </Typography>
                <Chip
                  size="small"
                  label={r.status.charAt(0) + r.status.slice(1).toLowerCase()}
                  color={
                    r.status === "APPROVED"
                      ? "success"
                      : r.status === "REJECTED"
                        ? "error"
                        : "default"
                  }
                />
                {r.decisionReason && (
                  <Typography variant="body2" color="text.secondary">
                    {r.decisionReason}
                  </Typography>
                )}
                {r.status === "PENDING" && canDelete && (
                  <Button
                    size="small"
                    onClick={() => void cancel(r.id)}
                    aria-label={`Cancel the request for ${formatDate(r.date)}`}
                  >
                    Cancel
                  </Button>
                )}
              </Stack>
            ))}
          </Stack>
        )}
        {pending.length > 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {pending.length} waiting for a decision.
          </Typography>
        )}
      </Paper>
    </Box>
  );
}

function Fig({ label, value }: { label: string; value: number }) {
  return (
    <Box>
      <Typography variant="h6" component="p">
        {figure(value)}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    </Box>
  );
}
