import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
  Switch,
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
import { formatDate, formatRupees, todayIso } from "../teachers/formatters";
import {
  dayRateHistory,
  listDayRates,
  recordDayRate,
  type DayRateListEntry,
  type DayRateRow,
} from "./dayRatesApi";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: DayRateListEntry[] };

/**
 * The rupees-per-full-day rate paid to a Substitute Teacher, by designation and effective date (spec 017b). A half day is
 * paid half. Rows are never edited: a new rate from a date takes over from that date, and the history keeps every one.
 */
export function DayRatesTab() {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [includeRetired, setIncludeRetired] = useState(false);
  const [setting, setSetting] = useState<DayRateListEntry | null>(null);
  const [viewing, setViewing] = useState<DayRateListEntry | null>(null);
  const [history, setHistory] = useState<DayRateRow[] | null>(null);
  const [amount, setAmount] = useState("");
  const [effectiveOn, setEffectiveOn] = useState(todayIso());
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await listDayRates(authFetch, includeRetired);
    setState(
      result.ok
        ? { kind: "ready", rows: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, includeRetired]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  async function openHistory(row: DayRateListEntry) {
    setViewing(row);
    setHistory(null);
    const result = await dayRateHistory(authFetch, row.designationId);
    setHistory(result.ok ? result.data : []);
  }

  async function save() {
    if (!setting) return;
    setError(null);
    const result = await recordDayRate(authFetch, setting.designationId, {
      amount: Number(amount),
      effectiveOn,
      note,
    });
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    setSetting(null);
    setAmount("");
    setNote("");
    await load();
  }

  const invalid = !(Number(amount) > 0) || effectiveOn === "";

  return (
    <Box>
      <Typography sx={{ mb: 2 }} color="text.secondary">
        What a Substitute Teacher is paid for a full day, by designation. A half day is paid half. A substitute has no
        monthly salary.
      </Typography>
      <FormControlLabel
        control={
          <Switch
            checked={includeRetired}
            onChange={(e) => setIncludeRetired(e.target.checked)}
          />
        }
        label="Include retired designations"
        sx={{ mb: 1 }}
      />
      {state.kind === "loading" && (
        <CircularProgress aria-label="Loading day rates" />
      )}
      {state.kind === "error" && (
        <Alert severity="error" role="alert">
          {state.reason}
        </Alert>
      )}
      {state.kind === "ready" && state.rows.length === 0 && (
        <Typography color="text.secondary">No Teacher designations yet.</Typography>
      )}
      {state.kind === "ready" && state.rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label="Substitute day rates">
            <TableHead>
              <TableRow>
                <TableCell>Designation</TableCell>
                <TableCell align="right">Rate per full day</TableCell>
                <TableCell>Since</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {state.rows.map((row) => (
                <TableRow key={row.designationId}>
                  <TableCell>
                    {row.name}
                    {row.retired ? " (retired)" : ""}
                  </TableCell>
                  <TableCell align="right">
                    {row.currentAmount === null
                      ? "No rate yet"
                      : formatRupees(Number(row.currentAmount))}
                  </TableCell>
                  <TableCell>{formatDate(row.currentEffectiveOn)}</TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={1}>
                      <Button
                        size="small"
                        aria-label={`Set day rate for ${row.name}`}
                        onClick={() => {
                          setSetting(row);
                          setError(null);
                        }}
                      >
                        Set rate
                      </Button>
                      <Button
                        size="small"
                        aria-label={`History of day rates for ${row.name}`}
                        onClick={() => void openHistory(row)}
                      >
                        History
                      </Button>
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={setting !== null} onClose={() => setSetting(null)} fullWidth maxWidth="xs">
        <DialogTitle>Day rate for {setting?.name}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {error && (
              <Alert severity="error" role="alert">
                {error}
              </Alert>
            )}
            <TextField
              label="Rate per full day (₹)"
              type="number"
              required
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              slotProps={{ htmlInput: { min: 0.01, step: "0.01" } }}
            />
            <TextField
              label="Effective from"
              type="date"
              required
              value={effectiveOn}
              onChange={(e) => setEffectiveOn(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSetting(null)}>Cancel</Button>
          <Button variant="contained" disabled={invalid} onClick={() => void save()}>
            Save rate
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={viewing !== null} onClose={() => setViewing(null)} fullWidth maxWidth="sm">
        <DialogTitle>Day rate history: {viewing?.name}</DialogTitle>
        <DialogContent>
          {history === null && <CircularProgress aria-label="Loading history" />}
          {history !== null && history.length === 0 && (
            <Typography color="text.secondary">No rate has been recorded.</Typography>
          )}
          {history !== null && history.length > 0 && (
            <Table size="small" aria-label="Rate history">
              <TableHead>
                <TableRow>
                  <TableCell>From</TableCell>
                  <TableCell align="right">Rate per full day</TableCell>
                  <TableCell>Note</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {history.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell>{formatDate(r.effectiveOn)}</TableCell>
                    <TableCell align="right">{formatRupees(Number(r.amount))}</TableCell>
                    <TableCell>{r.note}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setViewing(null)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
