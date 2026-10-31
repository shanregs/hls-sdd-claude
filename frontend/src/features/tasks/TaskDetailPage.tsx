import { useCallback, useEffect, useRef, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
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
  Link,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import type { ApiResult } from "../common/masterDataApi";
import { formatDate, todayIso } from "../teachers/formatters";
import {
  STATUS_LABELS,
  TASKS_ROUTE,
  actOnTask,
  getTask,
  uploadEvidence,
  type TaskAction,
  type TaskDetail,
} from "./taskApi";
import { StatusChip } from "../../theme/StatusChip";

type Ask =
  | {
      action: "complete";
      title: string;
      field: "note";
      label: string;
      min: number;
      button: string;
    }
  | {
      action: "overdue-reason";
      title: string;
      field: "reason";
      label: string;
      min: number;
      button: string;
    }
  | {
      action: "send-back";
      title: string;
      field: "reason";
      label: string;
      min: number;
      button: string;
    }
  | {
      action: "done-on-the-spot";
      title: string;
      field: "note";
      label: string;
      min: number;
      button: string;
    }
  | {
      action: "cancel";
      title: string;
      field: "reason";
      label: string;
      min: number;
      button: string;
    };

const ASKS: Record<Ask["action"], Ask> = {
  complete: {
    action: "complete",
    title: "Complete this task",
    field: "note",
    label: "What was done (completion note)",
    min: 10,
    button: "Complete task",
  },
  "overdue-reason": {
    action: "overdue-reason",
    title: "Why is this task overdue?",
    field: "reason",
    label: "Reason",
    min: 5,
    button: "Save reason",
  },
  "send-back": {
    action: "send-back",
    title: "Send back to the Manager",
    field: "reason",
    label: "What needs to change",
    min: 3,
    button: "Send back",
  },
  "done-on-the-spot": {
    action: "done-on-the-spot",
    title: "Mark done on the spot",
    field: "note",
    label: "What was done (completion note)",
    min: 10,
    button: "Mark done",
  },
  cancel: {
    action: "cancel",
    title: "Cancel this task",
    field: "reason",
    label: "Reason",
    min: 3,
    button: "Cancel task",
  },
};

/**
 * One task (spec 025): its facts, the evidence, the history, and exactly the actions the server says the caller may take
 * (acknowledge, start, complete, give an overdue reason, add evidence, verify, send back, cancel).
 */
export function TaskDetailPage() {
  const { id } = useParams();
  const { authFetch } = useAuth();
  const [detail, setDetail] = useState<TaskDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [ask, setAsk] = useState<Ask | null>(null);
  const [busy, setBusy] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    if (!id) return;
    const result = await getTask(authFetch, id);
    if (result.ok) {
      setDetail(result.data);
      setError(null);
    } else {
      setError(result.reason);
    }
  }, [authFetch, id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  async function run(
    call: Promise<ApiResult<TaskDetail>>,
  ): Promise<string | null> {
    setBusy(true);
    const result = await call;
    setBusy(false);
    if (!result.ok) {
      setError(result.reason);
      return result.reason;
    }
    setDetail(result.data);
    setError(null);
    return null;
  }

  if (!detail) {
    return error ? (
      <Alert severity="error">{error}</Alert>
    ) : (
      <CircularProgress aria-label="Loading task" />
    );
  }
  const { task, actions } = detail;
  const simple = (action: TaskAction) =>
    run(actOnTask(authFetch, task.id, action));

  return (
    <Box>
      <Link component={RouterLink} to={TASKS_ROUTE}>
        Back to tasks
      </Link>
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: "center", my: 1, flexWrap: "wrap" }}
      >
        <Typography variant="h5" component="h1">
          {task.title}
        </Typography>
        <Chip label={STATUS_LABELS[task.status]} />
        {task.sameDay && <Chip variant="outlined" label="Same-day" />}
        {task.overdue && (
          <StatusChip
            kind="rejected"
            label={`${task.daysOverdue} days overdue`}
          />
        )}
        {task.escalated && <StatusChip kind="pending" label="Escalated" />}
      </Stack>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack spacing={0.5}>
          <Typography>Type: {task.typeLabel}</Typography>
          <Typography>Manager: {task.assigneeName}</Typography>
          <Typography>Due: {formatDate(task.dueOn)}</Typography>
          {task.linkedLabel && (
            <Typography>Linked to: {task.linkedLabel}</Typography>
          )}
          {task.place && <Typography>Place: {task.place}</Typography>}
          {detail.description && <Typography>{detail.description}</Typography>}
          <Typography color="text.secondary">
            Created by {detail.createdByName ?? "System"}; escalates after{" "}
            {detail.escalateAfterDays} days overdue.
          </Typography>
          {detail.overdueReason && (
            <Typography>Overdue reason: {detail.overdueReason}</Typography>
          )}
          {detail.completionNote && (
            <Typography>Completion note: {detail.completionNote}</Typography>
          )}
          {detail.cancelReason && (
            <Typography>Cancelled: {detail.cancelReason}</Typography>
          )}
          {detail.parentTaskId && (
            <Link
              component={RouterLink}
              to={`${TASKS_ROUTE}/${detail.parentTaskId}`}
            >
              Follow-up of an overdue task
            </Link>
          )}
          {detail.followUpTaskIds.map((f) => (
            <Link key={f} component={RouterLink} to={`${TASKS_ROUTE}/${f}`}>
              Follow-up task
            </Link>
          ))}
        </Stack>
      </Paper>

      <Stack
        direction="row"
        spacing={1}
        sx={{ mb: 2, flexWrap: "wrap", rowGap: 1 }}
      >
        {actions.acknowledge && (
          <Button
            variant="contained"
            disabled={busy}
            onClick={() => void simple("acknowledge")}
          >
            Acknowledge
          </Button>
        )}
        {actions.start && (
          <Button
            variant="contained"
            disabled={busy}
            onClick={() => void simple("start")}
          >
            Start
          </Button>
        )}
        {actions.overdueReason && (
          <Button
            variant="outlined"
            disabled={busy}
            onClick={() => setAsk(ASKS["overdue-reason"])}
          >
            Give overdue reason
          </Button>
        )}
        {actions.addEvidence && (
          <Button
            variant="outlined"
            disabled={busy}
            onClick={() => fileInput.current?.click()}
          >
            Add evidence
          </Button>
        )}
        {actions.complete && (
          <Button
            variant="contained"
            disabled={busy}
            onClick={() => setAsk(ASKS.complete)}
          >
            Complete
          </Button>
        )}
        {(actions.acknowledge || actions.start || actions.complete) &&
          task.dueOn === todayIso() && (
            <Button
              variant="outlined"
              disabled={busy}
              onClick={() => setAsk(ASKS["done-on-the-spot"])}
            >
              Done on the spot
            </Button>
          )}
        {actions.verify && (
          <Button
            variant="contained"
            disabled={busy}
            onClick={() => void simple("verify")}
          >
            Verify
          </Button>
        )}
        {actions.sendBack && (
          <Button
            variant="outlined"
            disabled={busy}
            onClick={() => setAsk(ASKS["send-back"])}
          >
            Send back
          </Button>
        )}
        {actions.cancel && (
          <Button
            color="error"
            variant="outlined"
            disabled={busy}
            onClick={() => setAsk(ASKS.cancel)}
          >
            Cancel task
          </Button>
        )}
        <input
          ref={fileInput}
          type="file"
          hidden
          aria-label="Evidence file"
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = "";
            if (file) void run(uploadEvidence(authFetch, task.id, file));
          }}
        />
      </Stack>

      <Typography variant="h6" component="h2">
        Evidence
      </Typography>
      {detail.evidence.length === 0 ? (
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          No files yet.
        </Typography>
      ) : (
        <List dense sx={{ mb: 2 }}>
          {detail.evidence.map((f) => (
            <ListItem key={f.fileId}>
              <ListItemText
                primary={f.name}
                secondary={`${Math.ceil(f.sizeBytes / 1024)} KB`}
              />
            </ListItem>
          ))}
        </List>
      )}

      <Typography variant="h6" component="h2">
        History
      </Typography>
      <List dense aria-label="Task history">
        {detail.history.map((h, i) => (
          <ListItem key={i}>
            <ListItemText
              primary={`${h.event.replace(/_/g, " ").toLowerCase()} by ${h.actorName}`}
              secondary={[
                new Date(h.at).toLocaleString("en-IN"),
                h.before || h.after
                  ? `${h.before ?? ""} to ${h.after ?? ""}`
                  : null,
                h.note,
              ]
                .filter(Boolean)
                .join(" - ")}
            />
          </ListItem>
        ))}
      </List>

      {ask && (
        <AskDialog
          ask={ask}
          withFollowUp={ask.action === "overdue-reason"}
          onClose={() => setAsk(null)}
          onSubmit={async (text, followUpOn) => {
            const body: Record<string, unknown> = { [ask.field]: text };
            if (followUpOn) body.followUpOn = followUpOn;
            const failure = await run(
              actOnTask(authFetch, task.id, ask.action, body),
            );
            if (!failure) setAsk(null);
            return failure;
          }}
        />
      )}
    </Box>
  );
}

function AskDialog({
  ask,
  withFollowUp,
  onClose,
  onSubmit,
}: {
  ask: Ask;
  withFollowUp: boolean;
  onClose: () => void;
  onSubmit: (text: string, followUpOn: string) => Promise<string | null>;
}) {
  const [text, setText] = useState("");
  const [followUpOn, setFollowUpOn] = useState("");
  const [problem, setProblem] = useState<string | null>(null);

  async function submit() {
    if (text.trim().length < ask.min) {
      setProblem(`Write at least ${ask.min} characters.`);
      return;
    }
    if (followUpOn && followUpOn < todayIso()) {
      setProblem("The follow-up date cannot be in the past.");
      return;
    }
    setProblem(null);
    const failure = await onSubmit(text.trim(), followUpOn);
    if (failure) setProblem(failure);
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="ask-title"
    >
      <DialogTitle id="ask-title">{ask.title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          <TextField
            label={ask.label}
            value={text}
            onChange={(e) => setText(e.target.value)}
            multiline
            minRows={3}
            required
          />
          {withFollowUp && (
            <TextField
              label="Follow-up date (optional)"
              type="date"
              value={followUpOn}
              onChange={(e) => setFollowUpOn(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button variant="contained" onClick={() => void submit()}>
          {ask.button}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
