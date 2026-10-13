import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "../teachers/formatters";
import { MAX_REASON, validateDecisionReason } from "./formRules";
import {
  decideLeave,
  decideRegularization,
  listLeaveDecisions,
  listRegularizations,
  type ManagerLeaveRequest,
  type Regularization,
} from "./managerAttendanceApi";
import { dateRange, requestColor, requestStatusText } from "./statusDisplay";

const ROUTE = "/operations/manager-leave";

type Filter = "PENDING" | "APPROVED" | "ALL";

type Pending =
  | { kind: "leave"; id: string; action: "reject" | "revoke"; label: string }
  | { kind: "regularization"; id: string; action: "reject"; label: string };

type State =
  { kind: "loading" } | { kind: "error"; reason: string } | { kind: "ready" };

/** The Admin and Director decisions (spec 032 US3): Managers' leave and their forgotten check-in requests. */
export function ManagerLeaveDecisionsPage() {
  const { authFetch } = useAuth();
  const canDecide = useGrantedActions(ROUTE).has("APPROVE");

  const [filter, setFilter] = useState<Filter>("PENDING");
  const [state, setState] = useState<State>({ kind: "loading" });
  const [leave, setLeave] = useState<ManagerLeaveRequest[]>([]);
  const [checkIns, setCheckIns] = useState<Regularization[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [asking, setAsking] = useState<Pending | null>(null);
  const [reason, setReason] = useState("");
  const [reasonTouched, setReasonTouched] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const status = filter === "ALL" ? undefined : filter;
    const [l, r] = await Promise.all([
      listLeaveDecisions(authFetch, { status }),
      listRegularizations(authFetch, { status: "PENDING" }),
    ]);
    if (!l.ok) {
      setState({ kind: "error", reason: l.reason });
      return;
    }
    if (!r.ok) {
      setState({ kind: "error", reason: r.reason });
      return;
    }
    setLeave(l.data);
    setCheckIns(r.data);
    setState({ kind: "ready" });
  }, [authFetch, filter]);

  useEffect(() => {
    void (async () => {
      await load();
    })();
  }, [load]);

  const approveLeave = async (id: string) => {
    setMessage(null);
    setBusy(true);
    const result = await decideLeave(authFetch, id, "approve");
    setBusy(false);
    if (!result.ok) setMessage(result.reason);
    else await load();
  };

  const approveCheckIn = async (id: string) => {
    setMessage(null);
    setBusy(true);
    const result = await decideRegularization(authFetch, id, "approve");
    setBusy(false);
    if (!result.ok) setMessage(result.reason);
    else await load();
  };

  const closeAsk = () => {
    setAsking(null);
    setReason("");
    setReasonTouched(false);
  };

  const confirmReason = async () => {
    setReasonTouched(true);
    if (!asking || validateDecisionReason(reason)) return;
    setMessage(null);
    setBusy(true);
    const result =
      asking.kind === "leave"
        ? await decideLeave(authFetch, asking.id, asking.action, reason.trim())
        : await decideRegularization(
            authFetch,
            asking.id,
            asking.action,
            reason.trim(),
          );
    setBusy(false);
    closeAsk();
    if (!result.ok) setMessage(result.reason);
    else await load();
  };

  if (state.kind === "loading") {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <CircularProgress aria-label="Loading requests" />
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

  const reasonProblem = reasonTouched ? validateDecisionReason(reason) : null;

  return (
    <Box sx={{ maxWidth: 900 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        Manager leave
      </Typography>

      {message && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {message}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
        <Stack
          direction="row"
          sx={{ gap: 2, alignItems: "center", flexWrap: "wrap", mb: 1 }}
        >
          <Typography variant="h6" component="h2">
            Leave requests
          </Typography>
          <TextField
            select
            size="small"
            label="Show"
            value={filter}
            onChange={(e) => setFilter(e.target.value as Filter)}
            sx={{ minWidth: 140 }}
          >
            <MenuItem value="PENDING">Pending</MenuItem>
            <MenuItem value="APPROVED">Approved</MenuItem>
            <MenuItem value="ALL">All</MenuItem>
          </TextField>
        </Stack>
        {leave.length === 0 ? (
          <Typography color="text.secondary">No leave requests.</Typography>
        ) : (
          <Stack
            component="ul"
            spacing={2}
            sx={{ listStyle: "none", p: 0, m: 0 }}
          >
            {leave.map((r) => {
              const when = dateRange(r.firstDate, r.lastDate);
              return (
                <Stack key={r.id} component="li" spacing={0.5}>
                  <Stack
                    direction="row"
                    sx={{ gap: 1, alignItems: "center", flexWrap: "wrap" }}
                  >
                    <Typography>
                      <strong>{r.managerName}</strong>: {r.leaveTypeName},{" "}
                      {when}
                    </Typography>
                    {r.lossOfPay && (
                      <Chip size="small" variant="outlined" label="Unpaid" />
                    )}
                    <Chip
                      size="small"
                      color={requestColor(r.status)}
                      label={requestStatusText(r.status)}
                    />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    {r.reason}
                    {r.firstHalf ? " (first day is a half day)" : ""}
                    {r.lastHalf ? " (last day is a half day)" : ""}
                    {r.decisionReason ? `. Decision: ${r.decisionReason}` : ""}
                  </Typography>
                  {canDecide && r.status === "PENDING" && (
                    <Stack direction="row" spacing={1}>
                      <Button
                        size="small"
                        variant="contained"
                        disabled={busy}
                        onClick={() => void approveLeave(r.id)}
                        aria-label={`Approve leave of ${r.managerName}, ${when}`}
                      >
                        Approve
                      </Button>
                      <Button
                        size="small"
                        disabled={busy}
                        onClick={() =>
                          setAsking({
                            kind: "leave",
                            id: r.id,
                            action: "reject",
                            label: `Reject leave of ${r.managerName}, ${when}`,
                          })
                        }
                        aria-label={`Reject leave of ${r.managerName}, ${when}`}
                      >
                        Reject
                      </Button>
                    </Stack>
                  )}
                  {canDecide && r.status === "APPROVED" && (
                    <Box>
                      <Button
                        size="small"
                        disabled={busy}
                        onClick={() =>
                          setAsking({
                            kind: "leave",
                            id: r.id,
                            action: "revoke",
                            label: `Revoke leave of ${r.managerName}, ${when}`,
                          })
                        }
                        aria-label={`Revoke leave of ${r.managerName}, ${when}`}
                      >
                        Revoke
                      </Button>
                    </Box>
                  )}
                </Stack>
              );
            })}
          </Stack>
        )}
      </Paper>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          Forgotten check-ins
        </Typography>
        {checkIns.length === 0 ? (
          <Typography color="text.secondary">
            No forgotten check-ins waiting.
          </Typography>
        ) : (
          <Stack
            component="ul"
            spacing={2}
            sx={{ listStyle: "none", p: 0, m: 0 }}
          >
            {checkIns.map((r) => {
              const when = `${formatDate(r.date)} at ${r.proposedTime.slice(0, 5)}`;
              return (
                <Stack key={r.id} component="li" spacing={0.5}>
                  <Typography>
                    <strong>{r.managerName}</strong>: {when},{" "}
                    {r.dayType === "FIELD" ? "Field" : "Office"}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {r.reason}
                    {r.purpose ? `. Purpose: ${r.purpose}` : ""}
                  </Typography>
                  {canDecide && (
                    <Stack direction="row" spacing={1}>
                      <Button
                        size="small"
                        variant="contained"
                        disabled={busy}
                        onClick={() => void approveCheckIn(r.id)}
                        aria-label={`Approve check-in of ${r.managerName}, ${when}`}
                      >
                        Approve
                      </Button>
                      <Button
                        size="small"
                        disabled={busy}
                        onClick={() =>
                          setAsking({
                            kind: "regularization",
                            id: r.id,
                            action: "reject",
                            label: `Reject check-in of ${r.managerName}, ${when}`,
                          })
                        }
                        aria-label={`Reject check-in of ${r.managerName}, ${when}`}
                      >
                        Reject
                      </Button>
                    </Stack>
                  )}
                </Stack>
              );
            })}
          </Stack>
        )}
      </Paper>

      <Dialog
        open={asking !== null}
        onClose={closeAsk}
        fullWidth
        maxWidth="sm"
        aria-labelledby="decision-reason-title"
      >
        <DialogTitle id="decision-reason-title">{asking?.label}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            margin="dense"
            label="Reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            error={reasonProblem !== null}
            helperText={reasonProblem ?? `${reason.length}/${MAX_REASON}`}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={closeAsk}>Cancel</Button>
          <Button
            variant="contained"
            disabled={busy}
            onClick={() => void confirmReason()}
          >
            {asking?.action === "revoke" ? "Revoke" : "Reject"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
