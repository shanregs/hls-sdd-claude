import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate, todayIso } from "../teachers/formatters";
import {
  KIND_LABELS,
  REASON_LABELS,
  STATUS_LABELS,
  approveRequest,
  cancelRequest,
  changeRequest,
  rejectRequest,
  type MovementRequest,
} from "./transfersApi";

const STALE = "This request changed, reload";

/** One request with its live checks and history; buttons appear only for the actions the server sent. */
export function RequestDetail({
  request,
  onClose,
  onChanged,
}: {
  request: MovementRequest;
  onClose: () => void;
  onChanged: (request: MovementRequest) => void;
}) {
  const { authFetch } = useAuth();
  const [mode, setMode] = useState<"reject" | "cancel" | "change" | null>(null);
  const [text, setText] = useState("");
  const [date, setDate] = useState(request.effectiveOn);
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const can = (a: string) => (request.actions as string[]).includes(a);

  async function run(
    action: () => Promise<Awaited<ReturnType<typeof approveRequest>>>,
  ) {
    setBusy(true);
    setProblem(null);
    const result = await action();
    setBusy(false);
    if (result.ok) {
      setMode(null);
      onChanged(result.data);
    } else {
      setProblem(
        result.status === 409 && /changed/i.test(result.reason)
          ? STALE
          : result.reason,
      );
    }
  }

  function submitReason() {
    if (!text.trim()) {
      setProblem("Give a reason.");
      return;
    }
    void run(() =>
      mode === "reject"
        ? rejectRequest(authFetch, request.id, request.version, text.trim())
        : cancelRequest(authFetch, request.id, request.version, text.trim()),
    );
  }

  function submitDate() {
    if (!date || date < todayIso()) {
      setProblem("Choose today or a later date.");
      return;
    }
    void run(() =>
      changeRequest(authFetch, request.id, {
        version: request.version,
        effectiveOn: date,
      }),
    );
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="md"
      aria-labelledby="request-title"
    >
      <DialogTitle id="request-title">
        {KIND_LABELS[request.kind]}: {request.teacher.name}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={1.5} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          <Typography>
            Status: <strong>{STATUS_LABELS[request.status]}</strong>
            {request.statusNote ? ` (${request.statusNote})` : ""}
          </Typography>
          <Typography>
            {request.kind === "TRANSFER"
              ? `From ${request.fromSchool.name} to ${request.toSchool.name}`
              : `At ${request.fromSchool.name}, replaced by ${request.replacementTeacher?.name ?? ""}`}
            {request.position?.number
              ? `, position ${request.position.number}`
              : ""}
          </Typography>
          {request.outgoingDestinationSchool && (
            <Typography>
              {request.teacher.name} then goes to{" "}
              {request.outgoingDestinationSchool.name}
            </Typography>
          )}
          <Typography>Effective: {formatDate(request.effectiveOn)}</Typography>
          <Typography>
            Reason:{" "}
            {REASON_LABELS[request.reasonCategory] ?? request.reasonCategory}
            {request.note ? ` - ${request.note}` : ""}
          </Typography>
          <Typography>
            Requested by {request.requestedBy.name}
            {request.decidedBy ? `; decided by ${request.decidedBy.name}` : ""}
            {request.decisionReason ? ` - ${request.decisionReason}` : ""}
          </Typography>

          {request.checks.length > 0 && (
            <>
              <Divider />
              <Typography variant="subtitle2" component="h2">
                Checks
              </Typography>
              <List dense aria-label="Checks">
                {request.checks.map((c) => (
                  <ListItem key={c.text}>
                    <ListItemText
                      primary={`${c.ok ? "OK" : "Problem"}: ${c.text}`}
                    />
                  </ListItem>
                ))}
              </List>
            </>
          )}

          <Divider />
          <Typography variant="subtitle2" component="h2">
            History
          </Typography>
          <List dense aria-label="History">
            {request.history.map((h) => (
              <ListItem key={`${h.action}-${h.at}`}>
                <ListItemText
                  primary={`${h.action.replace("_", " ").toLowerCase()} by ${h.actor ?? "System"}`}
                  secondary={`${new Date(h.at).toLocaleString("en-IN")}${h.note ? ` - ${h.note}` : ""}`}
                />
              </ListItem>
            ))}
          </List>

          {mode === "change" && (
            <TextField
              label="New effective date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              slotProps={{
                inputLabel: { shrink: true },
                htmlInput: { min: todayIso() },
              }}
            />
          )}
          {(mode === "reject" || mode === "cancel") && (
            <TextField
              label={
                mode === "reject"
                  ? "Reason for rejecting"
                  : "Reason for cancelling"
              }
              required
              multiline
              minRows={2}
              value={text}
              onChange={(e) => setText(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 1000 } }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {mode === null && can("CHANGE") && (
          <Button onClick={() => setMode("change")}>Change date</Button>
        )}
        {mode === null && can("CANCEL") && (
          <Button color="error" onClick={() => setMode("cancel")}>
            Cancel request
          </Button>
        )}
        {mode === null && can("REJECT") && (
          <Button color="error" onClick={() => setMode("reject")}>
            Reject
          </Button>
        )}
        {mode === null && can("APPROVE") && (
          <Button
            variant="contained"
            disabled={busy}
            onClick={() =>
              void run(() =>
                approveRequest(authFetch, request.id, request.version),
              )
            }
          >
            Approve
          </Button>
        )}
        {mode === "change" && (
          <Button variant="contained" disabled={busy} onClick={submitDate}>
            Save date
          </Button>
        )}
        {(mode === "reject" || mode === "cancel") && (
          <Button
            variant="contained"
            color="error"
            disabled={busy}
            onClick={submitReason}
          >
            {mode === "reject" ? "Confirm rejection" : "Confirm cancellation"}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
