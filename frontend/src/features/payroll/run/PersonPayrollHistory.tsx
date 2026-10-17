import { useEffect, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import {
  Alert,
  CircularProgress,
  Link,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useAuth } from "../../../auth/useAuth";
import { formatDate } from "../../teachers/formatters";
import { money, monthLabel } from "./format";
import {
  getPersonHistory,
  KIND_LABELS,
  PAYMENT_MODE_LABELS,
  PERSON_KIND_LABELS,
  STATUS_LABELS,
  type HistoryEntry,
  type PersonKind,
} from "./payrollRunApi";

const ROUTE = "/operations/payroll";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: HistoryEntry[] };

function paymentText(row: HistoryEntry): string {
  if (row.state === "CANNOT_PAY") return "Not paid in this run";
  if (row.status !== "APPROVED") return "Not approved yet";
  const p = row.payment;
  if (!p || !p.paid) return "Not paid yet";
  const mode = p.mode ? PAYMENT_MODE_LABELS[p.mode] : "";
  return `Paid on ${formatDate(p.paidOn)} by ${mode}${p.reference ? `, reference ${p.reference}` : ""}`;
}

/** Every run a person was in, with the month, net pay, status and payment (spec 013b US7). Read only. */
export function PersonPayrollHistory() {
  const { kind = "", personId = "" } = useParams();
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  const personKind = kind.toUpperCase() as PersonKind;

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const result = await getPersonHistory(authFetch, personKind, personId);
      if (cancelled) return;
      setState(
        result.ok
          ? { kind: "ready", rows: result.data }
          : { kind: "error", reason: result.reason },
      );
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, personKind, personId]);

  return (
    <Stack spacing={2} sx={{ p: { xs: 2, md: 3 } }}>
      <Link component={RouterLink} to={ROUTE}>
        All pay runs
      </Link>
      <Typography variant="h1" sx={{ fontSize: "1.75rem" }}>
        Payroll history
      </Typography>
      <Typography color="text.secondary">
        {PERSON_KIND_LABELS[personKind] ?? kind}
      </Typography>
      {state.kind === "loading" && (
        <Stack direction="row" sx={{ gap: 1.5, alignItems: "center" }}>
          <CircularProgress size={22} aria-label="Loading the history" />
          <Typography>Loading the history…</Typography>
        </Stack>
      )}
      {state.kind === "error" && <Alert severity="error">{state.reason}</Alert>}
      {state.kind === "ready" && state.rows.length === 0 && (
        <Alert severity="info">
          This person has not been in a pay run yet.
        </Alert>
      )}
      {state.kind === "ready" && state.rows.length > 0 && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label="Payroll history">
            <TableHead>
              <TableRow>
                <TableCell>Month</TableCell>
                <TableCell>Run</TableCell>
                <TableCell align="right">Net pay</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Payment</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {state.rows.map((row) => (
                <TableRow key={row.lineId}>
                  <TableCell>
                    <Link component={RouterLink} to={`${ROUTE}/${row.runId}`}>
                      {monthLabel(row.month)}
                    </Link>
                  </TableCell>
                  <TableCell>
                    {KIND_LABELS[row.runKind]}
                    {row.runKind === "SUPPLEMENTARY" ? ` ${row.sequence}` : ""}
                  </TableCell>
                  <TableCell align="right">
                    {row.net === null ? "Not paid" : money(row.net)}
                  </TableCell>
                  <TableCell>{STATUS_LABELS[row.status]}</TableCell>
                  <TableCell>{paymentText(row)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Stack>
  );
}
