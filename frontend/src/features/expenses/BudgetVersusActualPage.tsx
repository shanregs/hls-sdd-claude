import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
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
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatRupees } from "../teachers/formatters";
import {
  EXPENSES_ROUTE,
  getBudgetDetail,
  getBudgets,
  setBudget,
  type AreaFigures,
  type BudgetDetail,
  type BudgetOverview,
} from "./expensesApi";
import { StatusChip } from "../../theme/StatusChip";

function thisMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

/**
 * OPERATIONS -> Expenses -> Budgets (spec 015 US5): budget, actual, pending, remaining and percentage used per area for a
 * month. An area with no budget says so; "over" is shown in words, not by colour alone.
 */
export function BudgetVersusActualPage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(EXPENSES_ROUTE);
  const [month, setMonth] = useState(thisMonth());
  const [overview, setOverview] = useState<BudgetOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<BudgetDetail | null>(null);
  const [editing, setEditing] = useState<AreaFigures | null>(null);
  const [amount, setAmount] = useState("");
  const [saveError, setSaveError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setOverview(null);
    const result = await getBudgets(authFetch, month);
    if (result.ok) {
      setOverview(result.data);
      setError(null);
    } else {
      setError(result.reason);
    }
  }, [authFetch, month]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and month-driven fetch
    void load();
  }, [load]);

  async function showDetail(area: AreaFigures) {
    const result = await getBudgetDetail(
      authFetch,
      month,
      area.total ? "all" : area.zoneId,
    );
    if (result.ok) setDetail(result.data);
    else setError(result.reason);
  }

  async function save() {
    if (!editing) return;
    const result = await setBudget(authFetch, {
      zoneId: editing.zoneId,
      month,
      amount: amount.trim(),
      version: editing.budgetVersion,
    });
    if (result.ok) {
      setEditing(null);
      void load();
    } else {
      setSaveError(result.reason);
    }
  }

  const canSet = granted.has("CREATE") || granted.has("EDIT");

  function row(a: AreaFigures) {
    return (
      <TableRow key={a.areaName + String(a.total)} hover selected={a.total}>
        <TableCell component="th" scope="row">
          {a.areaName}
        </TableCell>
        <TableCell align="right">
          {a.budget === null ? "No budget set" : formatRupees(Number(a.budget))}
        </TableCell>
        <TableCell align="right">{formatRupees(Number(a.actual))}</TableCell>
        <TableCell align="right">{formatRupees(Number(a.pending))}</TableCell>
        <TableCell align="right">
          {a.remaining === null ? "" : formatRupees(Number(a.remaining))}
        </TableCell>
        <TableCell align="right">
          {a.percentUsed === null ? "" : `${a.percentUsed}%`}
        </TableCell>
        <TableCell>
          {a.over ? (
            <StatusChip kind="rejected" size="small" label="Over budget" />
          ) : (
            ""
          )}
        </TableCell>
        <TableCell>
          <Stack direction="row" spacing={1}>
            <Button size="small" onClick={() => void showDetail(a)}>
              Claims for {a.areaName}
            </Button>
            {canSet && !a.total && (
              <Button
                size="small"
                onClick={() => {
                  setEditing(a);
                  setAmount(a.budget ?? "");
                  setSaveError(null);
                }}
              >
                Set budget for {a.areaName}
              </Button>
            )}
          </Stack>
        </TableCell>
      </TableRow>
    );
  }

  return (
    <Box>
      <Link component={RouterLink} to={EXPENSES_ROUTE} underline="hover">
        Back to Expenses
      </Link>
      <Typography variant="h5" component="h1" sx={{ my: 2 }}>
        Budget versus actual
      </Typography>
      <TextField
        label="Month"
        type="month"
        value={month}
        onChange={(e) => setMonth(e.target.value)}
        sx={{ mb: 2 }}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {!overview && !error && (
        <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
          <CircularProgress aria-label="Loading the budgets" />
        </Box>
      )}
      {overview && (
        <TableContainer>
          <Table size="small" aria-label="Budget versus actual">
            <TableHead>
              <TableRow>
                <TableCell>Area</TableCell>
                <TableCell align="right">Budget</TableCell>
                <TableCell align="right">Actual</TableCell>
                <TableCell align="right">Pending</TableCell>
                <TableCell align="right">Remaining</TableCell>
                <TableCell align="right">Used</TableCell>
                <TableCell>Marker</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {overview.areas.map(row)}
              {row(overview.total)}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {detail && (
        <Paper
          variant="outlined"
          sx={{ p: 2, mt: 3 }}
          aria-label="Claims behind actual"
        >
          <Typography variant="h6" component="h2">
            {detail.areaName}: claims behind actual
          </Typography>
          <Typography variant="subtitle2" sx={{ mt: 1 }}>
            By category
          </Typography>
          <ul>
            {detail.byCategory.map((c) => (
              <li key={c.category}>
                {c.categoryLabel}: {formatRupees(Number(c.total))} ({c.count})
              </li>
            ))}
          </ul>
          <Typography variant="subtitle2">By claimant</Typography>
          <ul>
            {detail.byClaimant.map((c) => (
              <li key={c.claimantId}>
                {c.claimantName}: {formatRupees(Number(c.total))} ({c.count})
              </li>
            ))}
          </ul>
          <Button onClick={() => setDetail(null)}>Close</Button>
        </Paper>
      )}

      {editing && (
        <Dialog
          open
          onClose={() => setEditing(null)}
          aria-labelledby="budget-title"
          fullWidth
          maxWidth="xs"
        >
          <DialogTitle id="budget-title">
            Budget for {editing.areaName}
          </DialogTitle>
          <DialogContent>
            {saveError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {saveError}
              </Alert>
            )}
            <TextField
              label="Monthly budget (rupees)"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              fullWidth
              autoFocus
              sx={{ mt: 1 }}
              slotProps={{ htmlInput: { inputMode: "decimal" } }}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setEditing(null)}>Cancel</Button>
            <Button variant="contained" onClick={() => void save()}>
              Save budget
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
}
