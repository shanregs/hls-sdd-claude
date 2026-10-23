import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Link,
  MenuItem,
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
import { formatDate, todayIso } from "../teachers/formatters";
import {
  FEEDBACK_STATUS_LABELS,
  PERFORMANCE_ROUTE,
  getFeedback,
  getOptions,
  listFeedback,
  openIssueFromFeedback,
  planFromFeedback,
  recordFeedback,
  withdrawFeedback,
  type FeedbackRow,
  type FeedbackView,
  type Option,
} from "./performanceApi";
import { PerformanceNav } from "./PerformanceNav";
import { ReasonDialog } from "./ReasonDialog";
import { TeacherPicker } from "./TeacherPicker";

type Rows =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: FeedbackRow[] };

/**
 * OPERATIONS -> Teacher Performance -> Principal feedback, spec 026 US3: what a Principal said about a Teacher, recorded apart
 * from the observation ratings. Feedback that needs action can open a performance issue or plan an observation.
 */
export function FeedbackPage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(PERFORMANCE_ROUTE);
  const [category, setCategory] = useState("");
  const [actionOnly, setActionOnly] = useState(false);
  const [categories, setCategories] = useState<Option[]>([]);
  const [rows, setRows] = useState<Rows>({ kind: "loading" });
  const [recording, setRecording] = useState(false);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setRows({ kind: "loading" });
    const list = await listFeedback(authFetch, {
      category: category || undefined,
      actionRequired: actionOnly || undefined,
    });
    setRows(
      list.ok
        ? { kind: "ready", rows: list.data.content }
        : { kind: "error", reason: list.reason },
    );
  }, [authFetch, category, actionOnly]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  useEffect(() => {
    (async () => {
      const options = await getOptions(authFetch);
      if (options.ok) setCategories(options.data.feedbackCategories);
    })();
  }, [authFetch]);

  return (
    <Box>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{
          alignItems: { sm: "center" },
          justifyContent: "space-between",
          mb: 2,
        }}
      >
        <Typography variant="h5" component="h1">
          Teacher Performance
        </Typography>
        {granted.has("CREATE") && (
          <Button variant="contained" onClick={() => setRecording(true)}>
            Record feedback
          </Button>
        )}
      </Stack>
      <PerformanceNav />

      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={2}
        sx={{ mb: 2, alignItems: { sm: "center" } }}
      >
        <TextField
          select
          size="small"
          label="Category"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value="">All categories</MenuItem>
          {categories.map((c) => (
            <MenuItem key={c.value} value={c.value}>
              {c.label}
            </MenuItem>
          ))}
        </TextField>
        <FormControlLabel
          control={
            <Checkbox
              checked={actionOnly}
              onChange={(e) => setActionOnly(e.target.checked)}
            />
          }
          label="Action required only"
        />
      </Stack>

      {rows.kind === "loading" && (
        <CircularProgress aria-label="Loading Principal feedback" />
      )}
      {rows.kind === "error" && <Alert severity="error">{rows.reason}</Alert>}
      {rows.kind === "ready" && rows.rows.length === 0 && (
        <Typography color="text.secondary">
          No Principal feedback here.
        </Typography>
      )}
      {rows.kind === "ready" && rows.rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label="Principal feedback">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>Teacher</TableCell>
                <TableCell>School</TableCell>
                <TableCell>Principal</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.rows.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>
                    <Link
                      component="button"
                      type="button"
                      onClick={() => setOpenId(row.id)}
                    >
                      {formatDate(row.givenOn)}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <Link
                      component={RouterLink}
                      to={`${PERFORMANCE_ROUTE}/teachers/${row.teacherId}`}
                    >
                      {row.teacherName}
                    </Link>
                  </TableCell>
                  <TableCell>{row.schoolName}</TableCell>
                  <TableCell>{row.principalName}</TableCell>
                  <TableCell>
                    {row.categoryLabel}
                    {row.actionRequired && (
                      <Chip
                        size="small"
                        color="warning"
                        label="Action required"
                        sx={{ ml: 1 }}
                      />
                    )}
                  </TableCell>
                  <TableCell>
                    {FEEDBACK_STATUS_LABELS[row.status] ?? row.status}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {recording && (
        <RecordDialog
          categories={categories}
          onClose={() => setRecording(false)}
          onRecorded={(view) => {
            setRecording(false);
            setOpenId(view.id);
            void load();
          }}
        />
      )}
      {openId && (
        <FeedbackDialog
          id={openId}
          onClose={() => setOpenId(null)}
          onChanged={() => void load()}
        />
      )}
    </Box>
  );
}

function RecordDialog({
  categories,
  onClose,
  onRecorded,
}: {
  categories: Option[];
  onClose: () => void;
  onRecorded: (view: FeedbackView) => void;
}) {
  const { authFetch } = useAuth();
  const [teacher, setTeacher] = useState("");
  const [principal, setPrincipal] = useState("");
  const [givenOn, setGivenOn] = useState(todayIso());
  const [category, setCategory] = useState("");
  const [comments, setComments] = useState("");
  const [actionRequired, setActionRequired] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!teacher) return setProblem("Choose the Teacher.");
    if (!principal.trim()) return setProblem("Write the Principal's name.");
    if (!category) return setProblem("Choose the category.");
    if (givenOn > todayIso())
      return setProblem("The date cannot be in the future.");
    if (comments.trim().length < 10)
      return setProblem("The comments must be at least 10 characters.");
    setProblem(null);
    setBusy(true);
    const result = await recordFeedback(authFetch, {
      teacherId: teacher,
      principalName: principal.trim(),
      givenOn,
      category,
      comments: comments.trim(),
      actionRequired,
    });
    setBusy(false);
    if (result.ok) onRecorded(result.data);
    else setProblem(result.reason);
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="record-feedback-title"
    >
      <DialogTitle id="record-feedback-title">
        Record Principal feedback
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          <TeacherPicker value={teacher} onChange={setTeacher} />
          <TextField
            label="Principal's name"
            required
            value={principal}
            onChange={(e) => setPrincipal(e.target.value)}
          />
          <TextField
            label="Date"
            type="date"
            required
            value={givenOn}
            onChange={(e) => setGivenOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            select
            required
            label="Category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            {categories.map((c) => (
              <MenuItem key={c.value} value={c.value}>
                {c.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="What the Principal said"
            required
            value={comments}
            onChange={(e) => setComments(e.target.value)}
            multiline
            minRows={3}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
          <FormControlLabel
            control={
              <Checkbox
                checked={actionRequired}
                onChange={(e) => setActionRequired(e.target.checked)}
              />
            }
            label="Action is required"
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
          Record feedback
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** One feedback record with the buttons its `actions` allow: withdraw, open an issue, plan an observation. */
function FeedbackDialog({
  id,
  onClose,
  onChanged,
}: {
  id: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const granted = useGrantedActions(PERFORMANCE_ROUTE);
  const [view, setView] = useState<FeedbackView | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [withdrawing, setWithdrawing] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      const result = await getFeedback(authFetch, id);
      if (result.ok) setView(result.data);
      else setProblem(result.reason);
    })();
  }, [authFetch, id]);

  async function openIssue() {
    if (!view) return;
    setBusy(true);
    const result = await openIssueFromFeedback(authFetch, view.id);
    setBusy(false);
    if (result.ok) {
      onChanged();
      navigate(`${PERFORMANCE_ROUTE}/issues/${result.data.id}`);
    } else setProblem(result.reason);
  }

  async function plan() {
    if (!view) return;
    setBusy(true);
    const result = await planFromFeedback(authFetch, view.id);
    setBusy(false);
    if (result.ok) {
      onChanged();
      navigate(`${PERFORMANCE_ROUTE}/planned`);
    } else setProblem(result.reason);
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="feedback-title"
    >
      <DialogTitle id="feedback-title">Principal feedback</DialogTitle>
      <DialogContent>
        {!view && !problem && (
          <CircularProgress aria-label="Loading feedback" />
        )}
        {problem && <Alert severity="error">{problem}</Alert>}
        {view && (
          <Stack spacing={1} sx={{ mt: 1 }}>
            <Typography>
              <strong>{view.teacherName}</strong> at {view.schoolName}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {view.principalName} on {formatDate(view.givenOn)} -{" "}
              {view.categoryLabel}
              {view.versionNo > 1 ? ` - version ${view.versionNo}` : ""}
            </Typography>
            <Typography sx={{ whiteSpace: "pre-wrap" }}>
              {view.comments}
            </Typography>
            {view.actionRequired && (
              <Chip
                size="small"
                color="warning"
                label="Action required"
                sx={{ alignSelf: "flex-start" }}
              />
            )}
            {view.status === "WITHDRAWN" && (
              <Alert severity="info">Withdrawn: {view.withdrawnReason}</Alert>
            )}
            {view.issueId && (
              <Link
                component={RouterLink}
                to={`${PERFORMANCE_ROUTE}/issues/${view.issueId}`}
              >
                Open the performance issue
              </Link>
            )}
            {view.offerIssue && (
              <Alert severity="warning">
                This feedback needs action. Open a performance issue or plan an
                observation.
              </Alert>
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        {view?.actions.openIssue &&
          view.offerIssue &&
          granted.has("CREATE") && (
            <Button disabled={busy} onClick={() => void openIssue()}>
              Open performance issue
            </Button>
          )}
        {view?.actions.planObservation &&
          view.actionRequired &&
          granted.has("CREATE") && (
            <Button disabled={busy} onClick={() => void plan()}>
              Plan an observation
            </Button>
          )}
        {view?.actions.withdraw && granted.has("EDIT") && (
          <Button color="error" onClick={() => setWithdrawing(true)}>
            Withdraw
          </Button>
        )}
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
      {withdrawing && view && (
        <ReasonDialog
          title="Withdraw feedback"
          label="Why is it withdrawn?"
          min={5}
          button="Withdraw"
          destructive
          onClose={() => setWithdrawing(false)}
          onSubmit={async (reason) => {
            const result = await withdrawFeedback(
              authFetch,
              view.id,
              reason,
              view.version,
            );
            if (!result.ok) return result.reason;
            setView(result.data);
            setWithdrawing(false);
            onChanged();
            return null;
          }}
        />
      )}
    </Dialog>
  );
}
