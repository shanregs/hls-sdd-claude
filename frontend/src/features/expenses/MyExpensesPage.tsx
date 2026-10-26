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
import { formatDate, formatRupees } from "../teachers/formatters";
import {
  MY_EXPENSES_ROUTE,
  STATUS_LABELS,
  listMine,
  type ClaimRow,
  type ClaimStatus,
} from "./expensesApi";

type Rows =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: ClaimRow[]; owed: string };

/** OPERATIONS -> My Expenses (spec 015 US7): own claims, newest first, what is still owed, and filters. */
export function MyExpensesPage() {
  const { authFetch } = useAuth();
  const [status, setStatus] = useState("");
  const [month, setMonth] = useState("");
  const [state, setState] = useState<Rows>({ kind: "loading" });

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await listMine(authFetch, {
      status: status || undefined,
      month: month || undefined,
    });
    setState(
      result.ok
        ? { kind: "ready", rows: result.data.content, owed: result.data.owed }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, status, month]);

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
          My Expenses
        </Typography>
        <Button
          variant="contained"
          component={RouterLink}
          to={`${MY_EXPENSES_ROUTE}/new`}
        >
          New claim
        </Button>
      </Stack>

      {state.kind === "ready" && (
        <Alert
          severity={Number(state.owed) > 0 ? "info" : "success"}
          sx={{ mb: 2 }}
        >
          {Number(state.owed) > 0
            ? `You are still owed ${formatRupees(Number(state.owed))} for approved claims.`
            : "Nothing is owed to you right now."}
        </Alert>
      )}

      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="">All</MenuItem>
          {(Object.keys(STATUS_LABELS) as ClaimStatus[]).map((s) => (
            <MenuItem key={s} value={s}>
              {STATUS_LABELS[s]}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          label="Month"
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
      </Stack>

      {state.kind === "loading" && (
        <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
          <CircularProgress aria-label="Loading your claims" />
        </Box>
      )}
      {state.kind === "error" && <Alert severity="error">{state.reason}</Alert>}
      {state.kind === "ready" && state.rows.length === 0 && (
        <Typography>
          You have no claims{status || month ? " for these filters" : " yet"}.
        </Typography>
      )}
      {state.kind === "ready" && state.rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label="My claims">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Area</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Bills</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {state.rows.map((row) => (
                <TableRow key={row.id} hover>
                  <TableCell>
                    <Link
                      component={RouterLink}
                      to={`${MY_EXPENSES_ROUTE}/${row.id}`}
                    >
                      {formatDate(row.expenseDate)}
                    </Link>
                  </TableCell>
                  <TableCell>{row.categoryLabel}</TableCell>
                  <TableCell>{row.areaName}</TableCell>
                  <TableCell align="right">
                    {formatRupees(Number(row.amount))}
                  </TableCell>
                  <TableCell>
                    <Chip size="small" label={row.statusLabel} />
                  </TableCell>
                  <TableCell>
                    {row.billCount > 0
                      ? row.billCount
                      : row.noBillReason
                        ? "No bill"
                        : "None"}
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
