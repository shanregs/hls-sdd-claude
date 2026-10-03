import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { getJson, sendJson } from "../common/masterDataApi";
import { formatDate, formatRupees, todayIso } from "./formatters";
import type { TeacherSummary } from "./teachersApi";

interface SalaryEntry {
  id: string;
  amount: number;
  effectiveOn: string;
}

interface SalaryHistory {
  current: SalaryEntry | null;
  history: SalaryEntry[];
}

interface SalaryDialogProps {
  teacher: TeacherSummary;
  onClose: () => void;
}

/**
 * A Teacher's salary history (spec 005 US9): current amount, every dated entry, an "as of" lookup,
 * and an append-only add form. Entries are never edited; a correction is a new entry.
 */
export function SalaryDialog({ teacher, onClose }: SalaryDialogProps) {
  const { authFetch } = useAuth();
  const base = `/api/v1/teachers/${teacher.id}/salary`;
  const [data, setData] = useState<SalaryHistory | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [amount, setAmount] = useState("");
  const [effectiveOn, setEffectiveOn] = useState(todayIso());
  const [asOf, setAsOf] = useState("");
  const [asOfResult, setAsOfResult] = useState<string | null>(null);

  const load = useCallback(async () => {
    const result = await getJson<SalaryHistory>(
      authFetch,
      base,
      "Could not load the salary history.",
    );
    if (result.ok) {
      setData(result.data);
      setError(null);
    } else {
      setError(result.reason);
    }
  }, [authFetch, base]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch on open
    void load();
  }, [load]);

  const add = async () => {
    const value = Number(amount);
    if (!amount.trim() || Number.isNaN(value) || value < 0) {
      setError("Enter a salary amount of zero or more.");
      return;
    }
    const result = await sendJson(authFetch, "POST", base, {
      amount: value,
      effectiveOn,
    });
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    setAmount("");
    await load();
  };

  const lookup = async () => {
    if (!asOf) return;
    const result = await getJson<{ amount: number | null }>(
      authFetch,
      `${base}?asOf=${asOf}`,
      "Could not look up the salary.",
    );
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    setAsOfResult(
      result.data.amount === null
        ? "No salary recorded for that date."
        : `${formatRupees(result.data.amount)} on ${formatDate(asOf)}`,
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Salary history - {teacher.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <Typography>
            Current:{" "}
            {data?.current
              ? formatRupees(data.current.amount)
              : "No salary recorded"}
          </Typography>
          <List dense>
            {(data?.history ?? []).map((entry) => (
              <ListItem key={entry.id}>
                <ListItemText
                  primary={formatRupees(entry.amount)}
                  secondary={`From ${formatDate(entry.effectiveOn)}`}
                />
              </ListItem>
            ))}
          </List>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              label="Salary amount"
              type="number"
              size="small"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
            <TextField
              label="Effective date"
              type="date"
              size="small"
              value={effectiveOn}
              onChange={(e) => setEffectiveOn(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <Button variant="contained" onClick={add}>
              Add entry
            </Button>
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              label="Salary as of"
              type="date"
              size="small"
              value={asOf}
              onChange={(e) => setAsOf(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <Button variant="outlined" onClick={lookup}>
              Look up
            </Button>
          </Stack>
          {asOfResult && <Typography role="status">{asOfResult}</Typography>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
