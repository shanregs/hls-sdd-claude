import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Divider,
  Drawer,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate, formatRupees } from "../teachers/formatters";
import {
  EVENT_LABELS,
  STATUS_LABELS,
  approveIncentive,
  getIncentive,
  rejectIncentive,
  reopenIncentive,
  type IncentiveDetail,
} from "./incentivesApi";
import type { ApiResult } from "../common/masterDataApi";

type Load =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; detail: IncentiveDetail };

type Asking = "reject" | "reopen" | null;

function when(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  const day = formatDate(d.toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" }));
  const time = d.toLocaleTimeString("en-GB", { timeZone: "Asia/Kolkata", hour: "2-digit", minute: "2-digit" });
  return `${day} ${time}`;
}

/** The detail of one incentive: facts, payroll status, history, and the decisions the server says this person may take. */
export function IncentiveDrawer({
  id,
  onClose,
  onChanged,
}: {
  id: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { authFetch } = useAuth();
  const [load, setLoad] = useState<Load>({ kind: "loading" });
  const [asking, setAsking] = useState<Asking>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- fetch when the opened incentive changes
    setLoad({ kind: "loading" });
    void getIncentive(authFetch, id).then((result) => {
      if (cancelled) return;
      setLoad(result.ok ? { kind: "ready", detail: result.data } : { kind: "error", reason: result.reason });
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch, id]);

  function done(result: ApiResult<IncentiveDetail>) {
    if (result.ok) {
      setLoad({ kind: "ready", detail: result.data });
      setError(null);
      setAsking(null);
      onChanged();
    } else {
      setError(result.reason);
    }
  }

  async function approve(detail: IncentiveDetail) {
    setBusy(true);
    done(await approveIncentive(authFetch, id, detail.incentive.version));
    setBusy(false);
  }

  return (
    <Drawer anchor="right" open onClose={onClose} slotProps={{ paper: { sx: { width: { xs: "100%", sm: 460 }, p: 2 }, "aria-label": "Incentive" } }}>
      <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 1 }}>
        <Typography variant="h6" component="h2">
          Incentive
        </Typography>
        <Button onClick={onClose}>Close</Button>
      </Stack>
      {load.kind === "loading" && <CircularProgress aria-label="Loading the incentive" />}
      {load.kind === "error" && (
        <Alert severity="error" role="alert">
          {load.reason}
        </Alert>
      )}
      {load.kind === "ready" && (
        <Box>
          <Typography variant="subtitle1" component="p">
            {load.detail.incentive.schoolName}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ my: 1, flexWrap: "wrap" }}>
            <Chip label={STATUS_LABELS[load.detail.incentive.status]} />
            {load.detail.incentive.approvedByEarner && <Chip color="info" label="Approved by the earner" />}
          </Stack>
          {load.detail.incentive.mouChanged && (
            <Alert severity="warning" sx={{ my: 1 }}>
              The MoU no longer counts as approved, but a pay run awaiting approval already holds this incentive. The Director has been told.
            </Alert>
          )}
          {error && (
            <Alert severity="error" role="alert" sx={{ my: 1 }}>
              {error}
            </Alert>
          )}
          <dl style={{ margin: 0 }}>
            <Fact label="Manager" value={load.detail.incentive.earnerName} />
            <Fact label="Zone" value={load.detail.incentive.zoneName} />
            <Fact label="Amount" value={formatRupees(load.detail.incentive.amount)} />
            <Fact label="Closed on" value={formatDate(load.detail.incentive.closureDate)} />
            {load.detail.incentive.decidedBy && (
              <Fact label="Decided by" value={`${load.detail.incentive.decidedBy}, ${when(load.detail.incentive.decidedAt)}`} />
            )}
            {load.detail.incentive.rejectReason && <Fact label="Reason for rejecting" value={load.detail.incentive.rejectReason} />}
            {load.detail.incentive.holdReason && <Fact label="Held because" value={load.detail.incentive.holdReason} />}
            {load.detail.incentive.payrollStatus && <Fact label="Payroll" value={load.detail.incentive.payrollStatus} />}
            {load.detail.incentive.runMonth && <Fact label="Pay run month" value={load.detail.incentive.runMonth.slice(0, 7)} />}
          </dl>
          <Stack direction="row" spacing={1} sx={{ my: 2, flexWrap: "wrap" }}>
            {load.detail.incentive.actions.includes("approve") && (
              <Button variant="contained" disabled={busy} onClick={() => void approve(load.detail)}>
                Approve
              </Button>
            )}
            {load.detail.incentive.actions.includes("reject") && (
              <Button color="error" variant="outlined" disabled={busy} onClick={() => setAsking("reject")}>
                Reject
              </Button>
            )}
            {load.detail.incentive.actions.includes("reopen") && (
              <Button variant="outlined" disabled={busy} onClick={() => setAsking("reopen")}>
                Reopen
              </Button>
            )}
          </Stack>
          <Divider sx={{ mb: 1 }} />
          <Typography variant="subtitle2" component="h3">
            History
          </Typography>
          <List dense aria-label="History">
            {load.detail.history.map((h, index) => (
              <ListItem key={`${h.kind}-${index}`} disableGutters>
                <ListItemText
                  primary={`${EVENT_LABELS[h.kind] ?? h.kind} by ${h.actor}`}
                  secondary={`${when(h.at)}${h.detail ? `. ${h.detail}` : ""}`}
                />
              </ListItem>
            ))}
          </List>
          {asking && (
            <ReasonDialog
              title={asking === "reject" ? "Reject this incentive" : "Reopen this incentive"}
              intro={
                asking === "reject"
                  ? "Say why this is not an incentive to pay. The Manager is told."
                  : "Say why the rejection was wrong. The incentive goes back to waiting for a decision."
              }
              label="Reason"
              confirmLabel={asking === "reject" ? "Reject" : "Reopen"}
              onSubmit={(reason) =>
                asking === "reject"
                  ? rejectIncentive(authFetch, id, load.detail.incentive.version, reason)
                  : reopenIncentive(authFetch, id, load.detail.incentive.version, reason)
              }
              onDone={done}
              onCancel={() => setAsking(null)}
            />
          )}
        </Box>
      )}
    </Drawer>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ mb: 1 }}>
      <Typography component="dt" variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0 }}>
        {value}
      </Typography>
    </Box>
  );
}

function ReasonDialog({
  title,
  intro,
  label,
  confirmLabel,
  onSubmit,
  onDone,
  onCancel,
}: {
  title: string;
  intro: string;
  label: string;
  confirmLabel: string;
  onSubmit: (reason: string) => Promise<ApiResult<IncentiveDetail>>;
  onDone: (result: ApiResult<IncentiveDetail>) => void;
  onCancel: () => void;
}) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function confirm() {
    if (reason.trim() === "") {
      setError(`${label} is required.`);
      return;
    }
    setBusy(true);
    const result = await onSubmit(reason.trim());
    setBusy(false);
    if (result.ok) onDone(result);
    else setError(result.reason);
  }

  return (
    <Dialog open onClose={onCancel} aria-labelledby="incentive-reason-title" fullWidth maxWidth="sm">
      <DialogTitle id="incentive-reason-title">{title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{intro}</DialogContentText>
        {error && (
          <Alert severity="error" role="alert" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <TextField
          label={label}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          fullWidth
          multiline
          minRows={2}
          required
          slotProps={{ htmlInput: { maxLength: 500 } }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant="contained" onClick={() => void confirm()} disabled={busy}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
