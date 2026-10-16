import { useCallback, useEffect, useMemo, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Link,
  MenuItem,
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
import { useAuth } from "../../../auth/useAuth";
import { useGrantedActions } from "../../common/useGrantedActions";
import {
  KIND_LABELS,
  listRuns,
  startRun,
  STATUS_LABELS,
  type Run,
} from "./payrollRunApi";
import { endedMonths, money, monthLabel } from "./format";

const ROUTE = "/operations/payroll";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; runs: Run[] };

/**
 * The pay runs, newest month first, and the form that starts one (spec 013b US1). Starting is for those who may Process;
 * a refusal (a run already exists, the month has not ended) is shown as the server words it.
 */
export function PayrollRunsPage() {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const canProcess = useGrantedActions(ROUTE).has("PROCESS");
  const months = useMemo(() => endedMonths(12), []);

  const [state, setState] = useState<State>({ kind: "loading" });
  const [month, setMonth] = useState(months[0] ?? "");
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const result = await listRuns(authFetch, { size: 100 });
    setState(
      result.ok
        ? { kind: "ready", runs: result.data.content }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch]);

  useEffect(() => {
    void (async () => {
      await load();
    })();
  }, [load]);

  const start = async () => {
    setStartError(null);
    setStarting(true);
    const result = await startRun(authFetch, month);
    setStarting(false);
    if (!result.ok) {
      setStartError(result.reason);
      return;
    }
    navigate(`${ROUTE}/${result.data.id}`);
  };

  return (
    <Stack spacing={3} sx={{ p: { xs: 2, md: 3 } }}>
      <Box>
        <Typography variant="h1" sx={{ fontSize: "1.75rem" }}>
          Payroll
        </Typography>
        <Typography color="text.secondary">
          Each month&apos;s pay run: what every person earned, what was
          deducted, and the net pay. Approving a run makes it final.
        </Typography>
      </Box>

      <Box>
        <Link component={RouterLink} to={`${ROUTE}/adjustments`}>
          Adjustments waiting for a run
        </Link>
      </Box>

      {canProcess && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="h2" sx={{ fontSize: "1.1rem", mb: 1.5 }}>
            Start a pay run
          </Typography>
          {startError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {startError}
            </Alert>
          )}
          <Stack
            direction="row"
            sx={{ gap: 2, flexWrap: "wrap", alignItems: "flex-start" }}
          >
            <TextField
              select
              label="Month"
              value={month}
              onChange={(e) => setMonth(e.target.value)}
              sx={{ minWidth: 200 }}
            >
              {months.map((m) => (
                <MenuItem key={m} value={m}>
                  {monthLabel(m)}
                </MenuItem>
              ))}
            </TextField>
            <Button
              variant="contained"
              onClick={() => void start()}
              disabled={starting || !month}
              sx={{ minHeight: 56 }}
            >
              Start run
            </Button>
          </Stack>
        </Paper>
      )}

      {state.kind === "loading" && (
        <Stack direction="row" sx={{ gap: 1.5, alignItems: "center" }}>
          <CircularProgress size={22} aria-label="Loading the pay runs" />
          <Typography>Loading the pay runs…</Typography>
        </Stack>
      )}
      {state.kind === "error" && <Alert severity="error">{state.reason}</Alert>}
      {state.kind === "ready" && state.runs.length === 0 && (
        <Alert severity="info">
          No pay run has been started yet.
          {canProcess ? " Choose a month above and start one." : ""}
        </Alert>
      )}
      {state.kind === "ready" && state.runs.length > 0 && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label="Pay runs">
            <TableHead>
              <TableRow>
                <TableCell>Month</TableCell>
                <TableCell>Run</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Paid people</TableCell>
                <TableCell align="right">Cannot be paid</TableCell>
                <TableCell align="right">Net pay</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {state.runs.map((run) => (
                <TableRow key={run.id} hover>
                  <TableCell>
                    <Link component={RouterLink} to={`${ROUTE}/${run.id}`}>
                      {monthLabel(run.month)}
                    </Link>
                  </TableCell>
                  <TableCell>
                    {KIND_LABELS[run.kind]}
                    {run.sequence > 1 ? ` (${run.sequence})` : ""}
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={STATUS_LABELS[run.status]}
                      color={
                        run.status === "APPROVED"
                          ? "success"
                          : run.status === "CANCELLED"
                            ? "default"
                            : "primary"
                      }
                      variant={run.status === "DRAFT" ? "outlined" : "filled"}
                    />
                  </TableCell>
                  <TableCell align="right">{run.payableCount}</TableCell>
                  <TableCell align="right">{run.cannotPayCount}</TableCell>
                  <TableCell align="right">{money(run.totals.net)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Stack>
  );
}
