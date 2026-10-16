import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Link,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../../auth/useAuth";
import { useGrantedActions } from "../../common/useGrantedActions";
import { CancelRunDialog } from "./CancelRunDialog";
import {
  cancelRun,
  getRun,
  KIND_LABELS,
  listLines,
  PERSON_KIND_LABELS,
  recomputeRun,
  STATUS_LABELS,
  type Line,
  type LineState,
  type PersonKind,
  type RecomputeEntry,
  type RecomputeSummary,
  type Run,
} from "./payrollRunApi";
import { dateTime, money, monthLabel } from "./format";

const ROUTE = "/operations/payroll";
const PAGE_SIZE = 50;
const POLL_MS = 2000;

type LoadState =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; run: Run };

const isComputing = (run: Run) =>
  (run.computeState === "QUEUED" || run.computeState === "RUNNING") &&
  !run.stalled;

function ChangeList({
  title,
  entries,
}: {
  title: string;
  entries: RecomputeEntry[];
}) {
  if (entries.length === 0) return null;
  return (
    <Box>
      <Typography variant="subtitle2" component="h4">
        {title} ({entries.length})
      </Typography>
      <Box component="ul" sx={{ m: 0, pl: 3 }}>
        {entries.map((e) => (
          <li key={`${e.kind}-${e.personId}`}>
            {e.name}
            {e.oldNet && e.newNet
              ? `: ${money(e.oldNet)} to ${money(e.newNet)}`
              : ""}
            {e.reason ? ` - ${e.reason}` : ""}
          </li>
        ))}
      </Box>
    </Box>
  );
}

/** What the last "Compute again" changed, so the Admin sees exactly what moved and why (FR-008). */
function RecomputeSummaryPanel({ summary }: { summary: RecomputeSummary }) {
  const nothing =
    summary.added.length +
      summary.removed.length +
      summary.changed.length +
      summary.moved.length ===
    0;
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="h2" sx={{ fontSize: "1.1rem", mb: 1 }}>
        What changed when it was computed again
      </Typography>
      {nothing ? (
        <Typography>
          Nothing changed: every line was already up to date.
        </Typography>
      ) : (
        <Stack spacing={1.5}>
          <ChangeList title="Added to the run" entries={summary.added} />
          <ChangeList title="Taken out of the run" entries={summary.removed} />
          <ChangeList title="Net pay changed" entries={summary.changed} />
          <ChangeList
            title="Moved between paid and cannot be paid"
            entries={summary.moved}
          />
        </Stack>
      )}
    </Paper>
  );
}

function LinesTable({ lines }: { lines: Line[] }) {
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small" aria-label="Lines of the pay run">
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell>Role</TableCell>
            <TableCell align="right">Earned</TableCell>
            <TableCell align="right">Deduction</TableCell>
            <TableCell align="right">Adjustments</TableCell>
            <TableCell align="right">Net pay</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {lines.map((line) => (
            <TableRow key={line.id} hover>
              <TableCell>
                {line.name}
                {line.warning && (
                  <Typography
                    variant="caption"
                    color="warning.main"
                    sx={{ display: "block" }}
                  >
                    {line.warning}
                  </Typography>
                )}
              </TableCell>
              <TableCell>
                {line.label ?? PERSON_KIND_LABELS[line.personKind]}
              </TableCell>
              <TableCell align="right">{money(line.earned)}</TableCell>
              <TableCell align="right">{money(line.deduction)}</TableCell>
              <TableCell align="right">{money(line.adjustments)}</TableCell>
              <TableCell align="right">{money(line.net)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

/**
 * One pay run: its header and totals, the progress while it computes, every line, the people who cannot be paid with
 * their reasons, and the actions the caller may take (spec 013b US1).
 */
export function PayrollRunPage() {
  const { runId = "" } = useParams();
  const { authFetch } = useAuth();
  const actions = useGrantedActions(ROUTE);
  const canProcess = actions.has("PROCESS");

  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [actionError, setActionError] = useState<string | null>(null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const [stateFilter, setStateFilter] = useState<LineState | "">("PAYABLE");
  const [kindFilter, setKindFilter] = useState<PersonKind | "">("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [linesState, setLinesState] = useState<
    | { kind: "loading" }
    | { kind: "error"; reason: string }
    | { kind: "ready"; lines: Line[]; total: number }
  >({ kind: "loading" });
  const [cannotPay, setCannotPay] = useState<Line[]>([]);

  const loadRun = useCallback(async () => {
    const result = await getRun(authFetch, runId);
    setState(
      result.ok
        ? { kind: "ready", run: result.data }
        : { kind: "error", reason: result.reason },
    );
    return result.ok ? result.data : null;
  }, [authFetch, runId]);

  useEffect(() => {
    void (async () => {
      await loadRun();
    })();
  }, [loadRun]);

  const run = state.kind === "ready" ? state.run : null;
  const computing = run ? isComputing(run) : false;

  // progress: ask again every two seconds while the batch runs, and stop when it is done or failed
  useEffect(() => {
    if (!computing) return;
    const timer = window.setTimeout(() => void loadRun(), POLL_MS);
    return () => window.clearTimeout(timer);
  }, [computing, state, loadRun]);

  // the lines are loaded once the computation is done, and again each time it finishes (it leaves DONE while computing)
  const ready = run !== null && run.computeState === "DONE";
  useEffect(() => {
    if (!ready) return;
    let cancelled = false;
    void (async () => {
      const result = await listLines(authFetch, runId, {
        state: stateFilter,
        kind: kindFilter,
        query: query.trim(),
        page,
        size: PAGE_SIZE,
      });
      if (cancelled) return;
      setLinesState(
        result.ok
          ? {
              kind: "ready",
              lines: result.data.content,
              total: result.data.totalElements,
            }
          : { kind: "error", reason: result.reason },
      );
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, runId, ready, stateFilter, kindFilter, query, page]);

  useEffect(() => {
    if (!ready) return;
    let cancelled = false;
    void (async () => {
      const result = await listLines(authFetch, runId, {
        state: "CANNOT_PAY",
        size: 200,
      });
      if (!cancelled && result.ok) setCannotPay(result.data.content);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, runId, ready]);

  const recompute = async () => {
    setActionError(null);
    setBusy(true);
    const result = await recomputeRun(authFetch, runId);
    setBusy(false);
    if (!result.ok) {
      setActionError(result.reason);
      return;
    }
    setState({ kind: "ready", run: result.data });
  };

  const cancel = async (reason: string): Promise<string | null> => {
    const result = await cancelRun(authFetch, runId, reason);
    if (!result.ok) return result.reason;
    setState({ kind: "ready", run: result.data });
    setCancelOpen(false);
    return null;
  };

  if (state.kind === "loading") {
    return (
      <Stack direction="row" sx={{ p: 3, gap: 1.5, alignItems: "center" }}>
        <CircularProgress size={22} aria-label="Loading the pay run" />
        <Typography>Loading the pay run…</Typography>
      </Stack>
    );
  }
  if (state.kind === "error") {
    return (
      <Stack spacing={2} sx={{ p: 3 }}>
        <Alert severity="error">{state.reason}</Alert>
        <Link component={RouterLink} to={ROUTE}>
          Back to the pay runs
        </Link>
      </Stack>
    );
  }

  const r = state.run;
  const isDraft = r.status === "DRAFT";
  const runName = `${monthLabel(r.month)} (${KIND_LABELS[r.kind]})`;
  const percent =
    r.progressTotal > 0
      ? Math.round((r.progressDone / r.progressTotal) * 100)
      : 0;
  const canComputeAgain =
    canProcess && isDraft && !computing && r.computeState !== "QUEUED";

  return (
    <Stack spacing={3} sx={{ p: { xs: 2, md: 3 } }}>
      <Box>
        <Link component={RouterLink} to={ROUTE}>
          All pay runs
        </Link>
        <Stack
          direction="row"
          sx={{ gap: 1.5, flexWrap: "wrap", alignItems: "center", mt: 1 }}
        >
          <Typography variant="h1" sx={{ fontSize: "1.75rem" }}>
            {runName}
          </Typography>
          <Chip
            label={STATUS_LABELS[r.status]}
            color={
              r.status === "APPROVED"
                ? "success"
                : r.status === "CANCELLED"
                  ? "default"
                  : "primary"
            }
            variant={isDraft ? "outlined" : "filled"}
          />
        </Stack>
        <Typography color="text.secondary">
          Started {dateTime(r.startedAt)}
          {r.startedBy ? ` by ${r.startedBy.name}` : ""}
        </Typography>
        {r.status === "CANCELLED" && (
          <Alert severity="info" sx={{ mt: 1 }}>
            Cancelled{r.cancelledBy ? ` by ${r.cancelledBy.name}` : ""}
            {r.cancelledAt ? ` on ${dateTime(r.cancelledAt)}` : ""}:{" "}
            {r.cancelReason}
          </Alert>
        )}
      </Box>

      {actionError && <Alert severity="error">{actionError}</Alert>}

      {(r.computeState === "QUEUED" || r.computeState === "RUNNING") && (
        <Paper variant="outlined" sx={{ p: 2 }} role="status">
          <Typography sx={{ mb: 1 }}>
            {r.stalled
              ? "The computation stopped before it finished. Compute again to carry on."
              : `Computing: ${r.progressDone} of ${r.progressTotal} people done.`}
          </Typography>
          <LinearProgress
            variant="determinate"
            value={percent}
            aria-label="Computation progress"
          />
        </Paper>
      )}
      {r.computeState === "FAILED" && (
        <Alert severity="error">
          The computation failed. Compute again; nothing has been approved.
        </Alert>
      )}

      {(canComputeAgain || (canProcess && isDraft)) && (
        <Stack direction="row" sx={{ gap: 1.5, flexWrap: "wrap" }}>
          {canProcess && isDraft && (
            <Button
              variant="outlined"
              onClick={() => void recompute()}
              disabled={busy || computing || r.computeState === "QUEUED"}
            >
              Compute again
            </Button>
          )}
          {canProcess && isDraft && (
            <Button
              color="error"
              variant="outlined"
              onClick={() => setCancelOpen(true)}
            >
              Cancel run
            </Button>
          )}
        </Stack>
      )}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack
          component="dl"
          direction="row"
          sx={{ gap: 4, flexWrap: "wrap", m: 0 }}
        >
          {(
            [
              ["Earned", money(r.totals.earned)],
              ["Deductions", money(r.totals.deduction)],
              ["Adjustments", money(r.totals.adjustments)],
              ["Net pay", money(r.totals.net)],
              ["People paid", String(r.payableCount)],
              ["Cannot be paid", String(r.cannotPayCount)],
            ] as const
          ).map(([label, value]) => (
            <Box key={label}>
              <Typography component="dt" variant="body2" color="text.secondary">
                {label}
              </Typography>
              <Typography component="dd" variant="h6" sx={{ m: 0 }}>
                {value}
              </Typography>
            </Box>
          ))}
        </Stack>
      </Paper>

      {r.recomputeSummary && (
        <RecomputeSummaryPanel summary={r.recomputeSummary} />
      )}

      {r.computeState === "DONE" && (
        <>
          <Box>
            <Typography variant="h2" sx={{ fontSize: "1.25rem", mb: 1.5 }}>
              Lines
            </Typography>
            <Stack
              direction="row"
              sx={{ gap: 2, flexWrap: "wrap", mb: 2, alignItems: "flex-start" }}
            >
              <TextField
                select
                label="Show"
                value={stateFilter}
                onChange={(e) => {
                  setStateFilter(e.target.value as LineState | "");
                  setPage(0);
                }}
                sx={{ minWidth: 180 }}
              >
                <MenuItem value="">Everyone</MenuItem>
                <MenuItem value="PAYABLE">People who are paid</MenuItem>
                <MenuItem value="CANNOT_PAY">Cannot be paid</MenuItem>
              </TextField>
              <TextField
                select
                label="Role"
                value={kindFilter}
                onChange={(e) => {
                  setKindFilter(e.target.value as PersonKind | "");
                  setPage(0);
                }}
                sx={{ minWidth: 160 }}
              >
                <MenuItem value="">All roles</MenuItem>
                <MenuItem value="TEACHER">Teachers</MenuItem>
                <MenuItem value="MANAGER">Managers</MenuItem>
                <MenuItem value="STAFF">Staff</MenuItem>
              </TextField>
              <TextField
                label="Search by name"
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setPage(0);
                }}
                sx={{ minWidth: 220 }}
              />
            </Stack>
            {linesState.kind === "loading" && (
              <Stack direction="row" sx={{ gap: 1.5, alignItems: "center" }}>
                <CircularProgress size={22} aria-label="Loading the lines" />
                <Typography>Loading the lines…</Typography>
              </Stack>
            )}
            {linesState.kind === "error" && (
              <Alert severity="error">{linesState.reason}</Alert>
            )}
            {linesState.kind === "ready" && linesState.lines.length === 0 && (
              <Alert severity="info">No line matches.</Alert>
            )}
            {linesState.kind === "ready" && linesState.lines.length > 0 && (
              <>
                <LinesTable lines={linesState.lines} />
                <TablePagination
                  component="div"
                  count={linesState.total}
                  page={page}
                  rowsPerPage={PAGE_SIZE}
                  rowsPerPageOptions={[PAGE_SIZE]}
                  onPageChange={(_, next) => setPage(next)}
                />
              </>
            )}
          </Box>

          <Box component="section" aria-labelledby="cannot-pay-heading">
            <Typography
              id="cannot-pay-heading"
              variant="h2"
              sx={{ fontSize: "1.25rem", mb: 1.5 }}
            >
              Cannot be paid in this run
            </Typography>
            {cannotPay.length === 0 ? (
              <Alert severity="success">
                Everyone in this run can be paid.
              </Alert>
            ) : (
              <TableContainer component={Paper} variant="outlined">
                <Table
                  size="small"
                  aria-label="People who cannot be paid in this run"
                >
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Role</TableCell>
                      <TableCell>Why</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {cannotPay.map((line) => (
                      <TableRow key={line.id}>
                        <TableCell>{line.name}</TableCell>
                        <TableCell>
                          {line.label ?? PERSON_KIND_LABELS[line.personKind]}
                        </TableCell>
                        <TableCell>{line.reason}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Box>
        </>
      )}

      <CancelRunDialog
        open={cancelOpen}
        runName={runName}
        onConfirm={cancel}
        onClose={() => setCancelOpen(false)}
      />
    </Stack>
  );
}
