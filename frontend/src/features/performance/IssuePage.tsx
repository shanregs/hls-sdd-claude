import { useCallback, useEffect, useState } from "react";
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
  MenuItem,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate, todayIso } from "../teachers/formatters";
import {
  ISSUE_STATUS_LABELS,
  PERFORMANCE_ROUTE,
  RESOLUTION_LABELS,
  closeIssue,
  escalateIssue,
  getIssue,
  listIssues,
  recordCorrectiveAction,
  referIssue,
  type IssueListView,
  type IssueRow,
  type IssueView,
} from "./performanceApi";
import { PerformanceNav } from "./PerformanceNav";
import { ReasonDialog } from "./ReasonDialog";

const MIN_DAYS = 1;
const MAX_DAYS = 60;

type Rows =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: IssueRow[] };

function statusText(status: string, resolution: string | null): string {
  const base = ISSUE_STATUS_LABELS[status] ?? status;
  return resolution
    ? `${base} - ${RESOLUTION_LABELS[resolution] ?? resolution}`
    : base;
}

/** OPERATIONS -> Teacher Performance -> Issues, spec 026 US4 to US6: the Open issues and Referred lists. */
export function IssuesPage() {
  const { authFetch } = useAuth();
  const [view, setView] = useState<IssueListView>("open");
  const [rows, setRows] = useState<Rows>({ kind: "loading" });

  const load = useCallback(async () => {
    setRows({ kind: "loading" });
    const list = await listIssues(authFetch, view);
    setRows(
      list.ok
        ? { kind: "ready", rows: list.data.content }
        : { kind: "error", reason: list.reason },
    );
  }, [authFetch, view]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and tab-driven fetch
    void load();
  }, [load]);

  return (
    <Box>
      <Typography variant="h5" component="h1" sx={{ mb: 2 }}>
        Teacher Performance
      </Typography>
      <PerformanceNav />
      <Tabs
        value={view}
        onChange={(_, v: IssueListView) => setView(v)}
        aria-label="Issue lists"
        sx={{ mb: 2 }}
      >
        <Tab value="open" label="Open issues" />
        <Tab value="referred" label="Referred" />
        <Tab value="all" label="All" />
      </Tabs>

      {rows.kind === "loading" && (
        <CircularProgress aria-label="Loading performance issues" />
      )}
      {rows.kind === "error" && <Alert severity="error">{rows.reason}</Alert>}
      {rows.kind === "ready" && rows.rows.length === 0 && (
        <Typography color="text.secondary">No issues here.</Typography>
      )}
      {rows.kind === "ready" && rows.rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label="Performance issues">
            <TableHead>
              <TableRow>
                <TableCell>Opened</TableCell>
                <TableCell>Teacher</TableCell>
                <TableCell>School</TableCell>
                <TableCell>Manager</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Rounds</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.rows.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>
                    <Link
                      component={RouterLink}
                      to={`${PERFORMANCE_ROUTE}/issues/${row.id}`}
                    >
                      {formatDate(row.openedOn)}
                    </Link>
                  </TableCell>
                  <TableCell>{row.teacherName}</TableCell>
                  <TableCell>{row.schoolName}</TableCell>
                  <TableCell>{row.managerName}</TableCell>
                  <TableCell>
                    {statusText(row.status, row.resolution)}
                  </TableCell>
                  <TableCell>{row.correctionRounds}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
}

type Loaded =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; view: IssueView };

type Ask = "corrective" | "refer" | "cancel" | "continue" | "escalate" | null;

/**
 * One performance issue (spec 026 US4 and US5): the round number, every corrective action, the supporting observations and
 * feedback, and the buttons the server allows - record a corrective action, escalate, refer, cancel or close as continue.
 */
export function IssuePage() {
  const { id } = useParams();
  const { authFetch } = useAuth();
  const granted = useGrantedActions(PERFORMANCE_ROUTE);
  const [loaded, setLoaded] = useState<Loaded>({ kind: "loading" });
  const [ask, setAsk] = useState<Ask>(null);

  const load = useCallback(async () => {
    if (!id) return;
    const result = await getIssue(authFetch, id);
    setLoaded(
      result.ok
        ? { kind: "ready", view: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  if (loaded.kind === "loading") {
    return <CircularProgress aria-label="Loading issue" />;
  }
  if (loaded.kind === "error") {
    return <Alert severity="error">{loaded.reason}</Alert>;
  }
  const issue = loaded.view;
  const can = (action: string) => granted.has(action);
  const done = (view: IssueView) => {
    setLoaded({ kind: "ready", view });
    setAsk(null);
  };

  return (
    <Box>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{ alignItems: { sm: "center" }, mb: 1 }}
      >
        <Typography variant="h5" component="h1">
          Performance issue: {issue.teacherName}
        </Typography>
        <Chip size="small" label={statusText(issue.status, issue.resolution)} />
        <Chip size="small" label={`Round ${issue.correctionRounds}`} />
      </Stack>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        <Link
          component={RouterLink}
          to={`${PERFORMANCE_ROUTE}/teachers/${issue.teacherId}`}
        >
          {issue.teacherName}
        </Link>
        {" at "}
        {issue.schoolName}. Opened {formatDate(issue.openedOn)} by{" "}
        {issue.openedByName}.
        {issue.closedOn
          ? ` Closed ${formatDate(issue.closedOn)}${issue.closingReason ? `: ${issue.closingReason}` : ""}.`
          : ""}
      </Typography>

      {issue.needsCorrectiveAction && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          The follow-up observation still needs improvement. Record a further
          corrective action, or refer the Teacher.
        </Alert>
      )}
      {issue.followUpOn && !issue.needsCorrectiveAction && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Follow-up observation due {formatDate(issue.followUpOn)}.
        </Alert>
      )}

      <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", mb: 2 }}>
        {issue.actions.correctiveAction && can("EDIT") && (
          <Button variant="contained" onClick={() => setAsk("corrective")}>
            Record corrective action
          </Button>
        )}
        {issue.actions.escalate && can("EDIT") && (
          <Button variant="outlined" onClick={() => setAsk("escalate")}>
            Escalate to Director
          </Button>
        )}
        {issue.actions.refer && can("EDIT") && (
          <Button variant="outlined" onClick={() => setAsk("refer")}>
            Refer for transfer or replacement
          </Button>
        )}
        {issue.actions.closeContinue && can("EDIT") && (
          <Button variant="outlined" onClick={() => setAsk("continue")}>
            Close as continue
          </Button>
        )}
        {issue.actions.cancel && can("EDIT") && (
          <Button
            color="error"
            variant="outlined"
            onClick={() => setAsk("cancel")}
          >
            Cancel issue
          </Button>
        )}
      </Stack>

      {issue.referral && (
        <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
          <Typography variant="h6" component="h2">
            Referral
          </Typography>
          <Typography>
            Recommendation:{" "}
            {issue.referral.recommendation === "TRANSFER"
              ? "Transfer to another School"
              : "Replace the Teacher"}
          </Typography>
          <Typography sx={{ whiteSpace: "pre-wrap" }}>
            {issue.referral.reason}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {issue.referral.referredByName} on{" "}
            {formatDate(issue.referral.referredOn)}
            {issue.referral.decisionOutcome
              ? `. Decision: ${issue.referral.decisionOutcome.toLowerCase()}`
              : ". Waiting for a decision."}
          </Typography>
        </Paper>
      )}

      <Typography variant="h6" component="h2">
        Corrective actions
      </Typography>
      {issue.correctiveActions.length === 0 ? (
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          None yet.
        </Typography>
      ) : (
        <List dense aria-label="Corrective actions" sx={{ mb: 2 }}>
          {issue.correctiveActions.map((a) => (
            <ListItem key={a.round} alignItems="flex-start">
              <ListItemText
                primary={`Round ${a.round}: follow-up on ${formatDate(a.followUpOn)}`}
                secondary={`Discussed: ${a.discussed}. Must change: ${a.requiredChange}. Recorded by ${a.recordedByName} on ${formatDate(a.recordedOn)}.`}
              />
            </ListItem>
          ))}
        </List>
      )}

      <Typography variant="h6" component="h2">
        Supporting records
      </Typography>
      <List dense aria-label="Supporting records">
        {issue.links.map((l) => (
          <ListItem key={`${l.type}-${l.rootId}`}>
            <ListItemText
              primary={
                l.type === "OBSERVATION" && l.id ? (
                  <Link
                    component={RouterLink}
                    to={`${PERFORMANCE_ROUTE}/observations/${l.id}`}
                  >
                    {l.summary}
                  </Link>
                ) : (
                  l.summary
                )
              }
              secondary={formatDate(l.date)}
            />
          </ListItem>
        ))}
      </List>

      {ask === "corrective" && (
        <CorrectiveDialog
          onClose={() => setAsk(null)}
          onSubmit={async (body) => {
            const result = await recordCorrectiveAction(authFetch, issue.id, {
              ...body,
              version: issue.version,
            });
            if (!result.ok) return result.reason;
            done(result.data);
            return null;
          }}
        />
      )}
      {ask === "refer" && (
        <ReferDialog
          onClose={() => setAsk(null)}
          onSubmit={async (recommendation, reason) => {
            const result = await referIssue(authFetch, issue.id, {
              recommendation,
              reason,
              version: issue.version,
            });
            if (!result.ok) return result.reason;
            done(result.data);
            return null;
          }}
        />
      )}
      {ask === "escalate" && (
        <ReasonDialog
          title="Escalate to the Director"
          label="What does the Director need to know?"
          min={5}
          button="Escalate"
          onClose={() => setAsk(null)}
          onSubmit={async (note) => {
            const result = await escalateIssue(authFetch, issue.id, note);
            if (!result.ok) return result.reason;
            done(result.data);
            return null;
          }}
        />
      )}
      {(ask === "cancel" || ask === "continue") && (
        <ReasonDialog
          title={ask === "cancel" ? "Cancel issue" : "Close as continue"}
          label={
            ask === "cancel"
              ? "Why is the issue cancelled?"
              : "Why should the Teacher continue?"
          }
          min={3}
          button={ask === "cancel" ? "Cancel issue" : "Close issue"}
          destructive={ask === "cancel"}
          onClose={() => setAsk(null)}
          onSubmit={async (reason) => {
            const result = await closeIssue(authFetch, issue.id, {
              outcome: ask === "cancel" ? "CANCELLED" : "CONTINUE",
              reason,
              version: issue.version,
            });
            if (!result.ok) return result.reason;
            done(result.data);
            return null;
          }}
        />
      )}
    </Box>
  );
}

function CorrectiveDialog({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (body: {
    discussed: string;
    requiredChange: string;
    followUpOn: string;
  }) => Promise<string | null>;
}) {
  const [discussed, setDiscussed] = useState("");
  const [change, setChange] = useState("");
  const [followUpOn, setFollowUpOn] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (discussed.trim().length < 5)
      return setProblem("Write what was discussed with the Teacher.");
    if (change.trim().length < 5) return setProblem("Write what must change.");
    if (!followUpOn) return setProblem("Choose the follow-up date.");
    const days = Math.round(
      (new Date(followUpOn).getTime() - new Date(todayIso()).getTime()) /
        86_400_000,
    );
    if (days < MIN_DAYS || days > MAX_DAYS) {
      return setProblem(
        `The follow-up date must be ${MIN_DAYS} to ${MAX_DAYS} days from today.`,
      );
    }
    setProblem(null);
    setBusy(true);
    const failure = await onSubmit({
      discussed: discussed.trim(),
      requiredChange: change.trim(),
      followUpOn,
    });
    setBusy(false);
    if (failure) setProblem(failure);
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="corrective-title"
    >
      <DialogTitle id="corrective-title">Record corrective action</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          <TextField
            label="What was discussed with the Teacher"
            required
            multiline
            minRows={2}
            value={discussed}
            onChange={(e) => setDiscussed(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
          <TextField
            label="What must change"
            required
            multiline
            minRows={2}
            value={change}
            onChange={(e) => setChange(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
          <TextField
            label="Follow-up date"
            type="date"
            required
            value={followUpOn}
            onChange={(e) => setFollowUpOn(e.target.value)}
            helperText={`Between ${MIN_DAYS} and ${MAX_DAYS} days from today. A follow-up observation is planned for that day.`}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          disabled={busy}
          onClick={() => void submit()}
        >
          Record corrective action
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function ReferDialog({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (recommendation: string, reason: string) => Promise<string | null>;
}) {
  const [recommendation, setRecommendation] = useState("");
  const [reason, setReason] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!recommendation) return setProblem("Choose Transfer or Replace.");
    if (reason.trim().length < 20)
      return setProblem("The reason must be at least 20 characters.");
    setProblem(null);
    setBusy(true);
    const failure = await onSubmit(recommendation, reason.trim());
    setBusy(false);
    if (failure) setProblem(failure);
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="refer-title"
    >
      <DialogTitle id="refer-title">Refer the Teacher</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            The Directors are told. Nothing changes for the Teacher until the
            transfer or replacement is decided.
          </Typography>
          {problem && <Alert severity="error">{problem}</Alert>}
          <TextField
            select
            required
            label="Recommendation"
            value={recommendation}
            onChange={(e) => setRecommendation(e.target.value)}
          >
            <MenuItem value="TRANSFER">Transfer to another School</MenuItem>
            <MenuItem value="REPLACE">Replace the Teacher</MenuItem>
          </TextField>
          <TextField
            label="Reason"
            required
            multiline
            minRows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            helperText="At least 20 characters."
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          disabled={busy}
          onClick={() => void submit()}
        >
          Refer
        </Button>
      </DialogActions>
    </Dialog>
  );
}
