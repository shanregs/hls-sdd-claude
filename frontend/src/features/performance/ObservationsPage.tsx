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
import { formatDate } from "../teachers/formatters";
import { ResultChip } from "./Chips";
import {
  OBSERVATION_STATUS_LABELS,
  PERFORMANCE_ROUTE,
  RESULT_LABELS,
  listObservations,
  startObservation,
  type ObservationRow,
} from "./performanceApi";
import { PerformanceNav } from "./PerformanceNav";
import { TeacherPicker } from "./TeacherPicker";

type Rows =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: ObservationRow[]; total: number };

/**
 * OPERATIONS -> Teacher Performance, spec 026 US1 and US6: the observations the caller may see, filtered by result, status,
 * dates and the visits where the Principal declined; a Manager starts a new observation from here.
 */
export function ObservationsPage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(PERFORMANCE_ROUTE);
  const [result, setResult] = useState("");
  const [status, setStatus] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [declined, setDeclined] = useState(false);
  const [rows, setRows] = useState<Rows>({ kind: "loading" });
  const [starting, setStarting] = useState(false);

  const load = useCallback(async () => {
    setRows({ kind: "loading" });
    const list = await listObservations(authFetch, {
      result: result || undefined,
      status: status || undefined,
      from: from || undefined,
      to: to || undefined,
      principalDeclined: declined || undefined,
    });
    setRows(
      list.ok
        ? {
            kind: "ready",
            rows: list.data.content,
            total: list.data.totalElements,
          }
        : { kind: "error", reason: list.reason },
    );
  }, [authFetch, result, status, from, to, declined]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

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
          <Button variant="contained" onClick={() => setStarting(true)}>
            Start observation
          </Button>
        )}
      </Stack>
      <PerformanceNav />

      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={2}
        sx={{ mb: 2, alignItems: { sm: "center" }, flexWrap: "wrap" }}
      >
        <TextField
          select
          size="small"
          label="Result"
          value={result}
          onChange={(e) => setResult(e.target.value)}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">All results</MenuItem>
          {Object.entries(RESULT_LABELS).map(([value, label]) => (
            <MenuItem key={value} value={value}>
              {label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {["DRAFT", "SUBMITTED", "NOT_HELD"].map((s) => (
            <MenuItem key={s} value={s}>
              {OBSERVATION_STATUS_LABELS[s]}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          size="small"
          type="date"
          label="From"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          size="small"
          type="date"
          label="To"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={declined}
              onChange={(e) => setDeclined(e.target.checked)}
            />
          }
          label="Principal declined only"
        />
      </Stack>

      {rows.kind === "loading" && (
        <CircularProgress aria-label="Loading observations" />
      )}
      {rows.kind === "error" && <Alert severity="error">{rows.reason}</Alert>}
      {rows.kind === "ready" && rows.rows.length === 0 && (
        <Typography color="text.secondary">No observations here.</Typography>
      )}
      {rows.kind === "ready" && rows.rows.length > 0 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {rows.total} {rows.total === 1 ? "observation" : "observations"}
          </Typography>
          <TableContainer>
            <Table size="small" aria-label="Observations">
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Teacher</TableCell>
                  <TableCell>School</TableCell>
                  <TableCell>Observer</TableCell>
                  <TableCell>Result</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell>
                      <Link
                        component={RouterLink}
                        to={`${PERFORMANCE_ROUTE}/observations/${row.id}`}
                      >
                        {formatDate(row.observedOn)}
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
                    <TableCell>{row.observerName}</TableCell>
                    <TableCell>
                      <ResultChip result={row.overallResult} />
                      {row.averageRating !== null && (
                        <Typography
                          variant="caption"
                          component="span"
                          sx={{ ml: 1 }}
                        >
                          average {row.averageRating}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      {OBSERVATION_STATUS_LABELS[row.status] ?? row.status}
                      {row.principalDeclined && (
                        <Chip
                          size="small"
                          color="warning"
                          label="Principal declined"
                          sx={{ ml: 1 }}
                        />
                      )}
                      {row.status === "SUBMITTED" &&
                        !row.permissionRecorded && (
                          <Typography
                            variant="caption"
                            component="div"
                            color="text.secondary"
                          >
                            Permission not recorded
                          </Typography>
                        )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      {starting && <StartDialog onClose={() => setStarting(false)} />}
    </Box>
  );
}

function StartDialog({ onClose }: { onClose: () => void }) {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const [teacher, setTeacher] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function start() {
    if (!teacher) {
      setProblem("Choose the Teacher.");
      return;
    }
    setProblem(null);
    setBusy(true);
    const result = await startObservation(authFetch, teacher);
    setBusy(false);
    if (result.ok) {
      navigate(`${PERFORMANCE_ROUTE}/observations/${result.data.id}`);
    } else {
      setProblem(result.reason);
    }
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="start-title"
    >
      <DialogTitle id="start-title">Start an observation</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          <TeacherPicker value={teacher} onChange={setTeacher} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          disabled={busy}
          onClick={() => void start()}
        >
          Start
        </Button>
      </DialogActions>
    </Dialog>
  );
}
