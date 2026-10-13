import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  FormControlLabel,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { dateRange, requestColor, requestStatusText } from "./statusDisplay";
import {
  EMPTY_LEAVE,
  MAX_REASON,
  validateLeave,
  type LeaveValues,
} from "./formRules";
import {
  cancelLeave,
  listLeaveTypes,
  listMyLeave,
  requestLeave,
  type LeaveType,
  type ManagerLeaveRequest,
} from "./managerAttendanceApi";

const ROUTE = "/manager-attendance/me/leave";

type State =
  { kind: "loading" } | { kind: "error"; reason: string } | { kind: "ready" };

/** A Manager's own leave (spec 032 US3): the request form and the Manager's requests with cancel. */
export function MyManagerLeavePage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(ROUTE);
  const canCreate = granted.has("CREATE");
  const canDelete = granted.has("DELETE");

  const [state, setState] = useState<State>({ kind: "loading" });
  const [types, setTypes] = useState<LeaveType[]>([]);
  const [requests, setRequests] = useState<ManagerLeaveRequest[]>([]);
  const [form, setForm] = useState<LeaveValues>(EMPTY_LEAVE);
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [t, r] = await Promise.all([
      listLeaveTypes(authFetch),
      listMyLeave(authFetch),
    ]);
    if (!t.ok) {
      setState({ kind: "error", reason: t.reason });
      return;
    }
    if (!r.ok) {
      setState({ kind: "error", reason: r.reason });
      return;
    }
    setTypes(t.data);
    setRequests(r.data);
    setState({ kind: "ready" });
  }, [authFetch]);

  useEffect(() => {
    void (async () => {
      await load();
    })();
  }, [load]);

  const problem = validateLeave(form);
  const set = <K extends keyof LeaveValues>(key: K, value: LeaveValues[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const submit = async () => {
    setTouched(true);
    setMessage(null);
    if (problem) return;
    setBusy(true);
    const result = await requestLeave(authFetch, {
      ...form,
      reason: form.reason.trim(),
    });
    setBusy(false);
    if (!result.ok) {
      setMessage(result.reason);
      return;
    }
    setForm(EMPTY_LEAVE);
    setTouched(false);
    await load();
  };

  const cancel = async (id: string) => {
    setMessage(null);
    const result = await cancelLeave(authFetch, id);
    if (!result.ok) setMessage(result.reason);
    else await load();
  };

  if (state.kind === "loading") {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <CircularProgress aria-label="Loading your leave" />
      </Box>
    );
  }
  if (state.kind === "error") {
    return (
      <Alert severity="error" role="alert">
        {state.reason}
      </Alert>
    );
  }

  return (
    <Box sx={{ maxWidth: 720 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        My leave
      </Typography>

      {message && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {message}
        </Alert>
      )}

      {canCreate && (
        <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
          <Typography variant="h6" component="h2" gutterBottom>
            Ask for leave
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Weekly offs and holidays in the range are not counted. An Admin or
            Director decides.
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
              select
              label="Leave type"
              value={form.leaveTypeId}
              onChange={(e) => set("leaveTypeId", e.target.value)}
            >
              {types.map((t) => (
                <MenuItem key={t.id} value={t.id}>
                  {t.name}
                  {t.lossOfPay ? " (unpaid)" : ""}
                </MenuItem>
              ))}
            </TextField>
            <Stack direction="row" spacing={2}>
              <TextField
                label="First day"
                type="date"
                value={form.firstDate}
                onChange={(e) => set("firstDate", e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                label="Last day"
                type="date"
                value={form.lastDate}
                onChange={(e) => set("lastDate", e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Stack>
            <Stack direction="row" spacing={2}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={form.firstHalf}
                    onChange={(e) => set("firstHalf", e.target.checked)}
                  />
                }
                label="First day is a half day"
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={form.lastHalf}
                    onChange={(e) => set("lastHalf", e.target.checked)}
                  />
                }
                label="Last day is a half day"
              />
            </Stack>
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
            component="ul"
            spacing={1.5}
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
                  {r.leaveTypeName}, {dateRange(r.firstDate, r.lastDate)}
                </Typography>
                <Chip
                  size="small"
                  color={requestColor(r.status)}
                  label={requestStatusText(r.status)}
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
                    aria-label={`Cancel the leave ${dateRange(r.firstDate, r.lastDate)}`}
                  >
                    Cancel
                  </Button>
                )}
              </Stack>
            ))}
          </Stack>
        )}
      </Paper>
    </Box>
  );
}
