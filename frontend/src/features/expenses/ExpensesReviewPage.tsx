import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
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
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate, formatRupees } from "../teachers/formatters";
import {
  CATEGORY_LABELS,
  EXPENSES_ROUTE,
  STATUS_LABELS,
  downloadExport,
  getPending,
  listClaims,
  type Category,
  type ClaimRow,
  type ClaimStatus,
  type PendingSummary,
} from "./expensesApi";

type Rows =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: ClaimRow[] };

function thisMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

/**
 * OPERATIONS -> Expenses (spec 015 US3, US6): the claims waiting for a decision, oldest first, with their age and the
 * area's remaining budget, the pending-approval ageing, and the monthly CSV for those who may export.
 */
export function ExpensesReviewPage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(EXPENSES_ROUTE);
  const [status, setStatus] = useState<ClaimStatus | "ALL">("SUBMITTED");
  const [category, setCategory] = useState("");
  const [state, setState] = useState<Rows>({ kind: "loading" });
  const [pending, setPending] = useState<PendingSummary | null>(null);
  const [exportMonth, setExportMonth] = useState(thisMonth());
  const [exportError, setExportError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await listClaims(authFetch, { status, category: category || undefined });
    setState(
      result.ok
        ? { kind: "ready", rows: result.data.content }
        : { kind: "error", reason: result.reason },
    );
    const summary = await getPending(authFetch);
    if (summary.ok) setPending(summary.data);
  }, [authFetch, status, category]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  async function exportCsv() {
    setExportError(null);
    const result = await downloadExport(authFetch, exportMonth);
    if (!result.ok) {
      setExportError(result.reason);
      return;
    }
    const link = document.createElement("a");
    link.href = result.data;
    link.download = `expenses-${exportMonth}.csv`;
    link.click();
  }

  return (
    <Box>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{ alignItems: { sm: "center" }, justifyContent: "space-between", mb: 2 }}
      >
        <Typography variant="h5" component="h1">
          Expenses
        </Typography>
        <Stack direction="row" spacing={1}>
          <Button component={RouterLink} to={`${EXPENSES_ROUTE}/budgets`} variant="outlined">
            Budgets
          </Button>
          <Button component={RouterLink} to={`${EXPENSES_ROUTE}/costs`} variant="outlined">
            Cost measures
          </Button>
        </Stack>
      </Stack>

      {pending && (
        <Paper variant="outlined" sx={{ p: 2, mb: 2 }} aria-label="Pending approvals">
          <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
            Waiting for a decision: {pending.count} ({formatRupees(Number(pending.total))})
          </Typography>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            {pending.buckets.map((b) => (
              <Box key={b.label}>
                <Typography variant="body2" color="text.secondary">
                  {b.label}
                </Typography>
                <Typography>
                  {b.count} {b.count === 1 ? "claim" : "claims"}, {formatRupees(Number(b.total))}
                </Typography>
              </Box>
            ))}
          </Stack>
        </Paper>
      )}

      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as ClaimStatus | "ALL")}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="ALL">All</MenuItem>
          {(Object.keys(STATUS_LABELS) as ClaimStatus[])
            .filter((s) => s !== "DRAFT")
            .map((s) => (
              <MenuItem key={s} value={s}>
                {STATUS_LABELS[s]}
              </MenuItem>
            ))}
        </TextField>
        <TextField
          select
          label="Category"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="">All</MenuItem>
          {(Object.keys(CATEGORY_LABELS) as Category[]).map((c) => (
            <MenuItem key={c} value={c}>
              {CATEGORY_LABELS[c]}
            </MenuItem>
          ))}
        </TextField>
        {granted.has("EXPORT") && (
          <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
            <TextField
              label="Export month"
              type="month"
              value={exportMonth}
              onChange={(e) => setExportMonth(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <Button variant="outlined" onClick={() => void exportCsv()}>
              Export CSV
            </Button>
          </Stack>
        )}
      </Stack>
      {exportError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {exportError}
        </Alert>
      )}

      {state.kind === "loading" && (
        <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
          <CircularProgress aria-label="Loading the claims" />
        </Box>
      )}
      {state.kind === "error" && <Alert severity="error">{state.reason}</Alert>}
      {state.kind === "ready" && state.rows.length === 0 && <Typography>No claims are waiting.</Typography>}
      {state.kind === "ready" && state.rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label="Claims">
            <TableHead>
              <TableRow>
                <TableCell>Claimant</TableCell>
                <TableCell>Date</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Area</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell>Bills</TableCell>
                <TableCell>Linked work</TableCell>
                <TableCell>Waiting</TableCell>
                <TableCell align="right">Budget left</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {state.rows.map((row) => (
                <TableRow key={row.id} hover>
                  <TableCell>
                    <Link component={RouterLink} to={`${EXPENSES_ROUTE}/${row.id}`}>
                      {row.claimantName}
                    </Link>
                  </TableCell>
                  <TableCell>{formatDate(row.expenseDate)}</TableCell>
                  <TableCell>{row.categoryLabel}</TableCell>
                  <TableCell>{row.areaName}</TableCell>
                  <TableCell align="right">{formatRupees(Number(row.amount))}</TableCell>
                  <TableCell>{row.billCount > 0 ? row.billCount : "No bill"}</TableCell>
                  <TableCell>{row.linkLabel ?? "Not linked"}</TableCell>
                  <TableCell>{row.ageDays === null ? "" : `${row.ageDays} days`}</TableCell>
                  <TableCell align="right">
                    {row.remainingBudget === null ? "No budget set" : formatRupees(Number(row.remainingBudget))}
                  </TableCell>
                  <TableCell>
                    <Chip size="small" label={row.statusLabel} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
}
