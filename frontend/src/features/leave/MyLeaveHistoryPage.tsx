import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  IconButton,
  MenuItem,
  Pagination,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import CancelOutlined from "@mui/icons-material/CancelOutlined";
import { useAuth } from "../../auth/useAuth";
import { ConfirmDialog } from "../common/ConfirmDialog";
import { formatDate } from "../attendance/monthUtils";
import {
  cancelMyLeave,
  getMyLeave,
  type LeaveRequestView,
  type LeaveStatus,
} from "./leaveApi";
import { LeaveStatusChip } from "./LeaveStatusChip";

const PAGE_SIZE = 25;

function datesOf(r: LeaveRequestView): string {
  return r.firstDate === r.lastDate
    ? formatDate(r.firstDate)
    : `${formatDate(r.firstDate)} to ${formatDate(r.lastDate)}`;
}

function decisionOf(r: LeaveRequestView): string {
  if (r.status === "REJECTED") return r.decisionNote ?? "";
  if (r.status === "CANCELLED") {
    return r.cancelledBy === "SUPERVISOR"
      ? `Revoked${r.decisionNote ? `: ${r.decisionNote}` : ""}`
      : "Cancelled by you";
  }
  return r.decisionNote ?? "";
}

/** LEAVE -> My Leave History (spec 009 US2): own requests with their outcome; cancel while allowed. */
export function MyLeaveHistoryPage() {
  const { authFetch } = useAuth();
  const [status, setStatus] = useState<LeaveStatus | "">("");
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<LeaveRequestView[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [toCancel, setToCancel] = useState<LeaveRequestView | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await getMyLeave(authFetch, {
        status,
        page,
        size: PAGE_SIZE,
      });
      if (cancelled) return;
      if (result.ok) {
        setError(null);
        setRows(result.data.content);
        setTotal(result.data.totalElements);
      } else {
        setError(result.reason);
        setRows([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, status, page, reload]);

  const confirmCancel = useCallback(async () => {
    if (!toCancel) return;
    setBusy(true);
    const result = await cancelMyLeave(authFetch, toCancel.id);
    setBusy(false);
    setToCancel(null);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    setReload((n) => n + 1);
  }, [authFetch, toCancel]);

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        My Leave History
      </Typography>
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as LeaveStatus | "");
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All</MenuItem>
          <MenuItem value="PENDING">Pending</MenuItem>
          <MenuItem value="APPROVED">Approved</MenuItem>
          <MenuItem value="REJECTED">Rejected</MenuItem>
          <MenuItem value="CANCELLED">Cancelled</MenuItem>
        </TextField>
      </Stack>
      {error && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {rows === null && !error && <Typography>Loading…</Typography>}
      {rows !== null && rows.length === 0 && !error && (
        <Typography>You have no leave requests yet.</Typography>
      )}
      {rows !== null && rows.length > 0 && (
        <Paper variant="outlined">
          <TableContainer>
            <Table size="small" aria-label="My leave requests">
              <TableHead>
                <TableRow>
                  <TableCell>#</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Dates</TableCell>
                  <TableCell align="right">Working days</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Decided by</TableCell>
                  <TableCell>Note</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((r, i) => (
                  <TableRow key={r.id} hover>
                    <TableCell>{page * PAGE_SIZE + i + 1}</TableCell>
                    <TableCell>{r.leaveType}</TableCell>
                    <TableCell>{datesOf(r)}</TableCell>
                    <TableCell align="right">{r.workingDays}</TableCell>
                    <TableCell>
                      <LeaveStatusChip status={r.status} />
                    </TableCell>
                    <TableCell>
                      {r.decidedByName
                        ? `${r.decidedByName}${r.decidedAt ? `, ${formatDate(r.decidedAt.slice(0, 10))}` : ""}`
                        : "—"}
                    </TableCell>
                    <TableCell>{decisionOf(r) || r.reason}</TableCell>
                    <TableCell>
                      {r.allowedActions.includes("CANCEL") && (
                        <Tooltip title="Cancel">
                          <IconButton
                            size="small"
                            color="error"
                            aria-label={`Cancel leave ${datesOf(r)}`}
                            onClick={() => setToCancel(r)}
                          >
                            <CancelOutlined fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}
      {total > PAGE_SIZE && (
        <Pagination
          sx={{ mt: 2 }}
          count={Math.ceil(total / PAGE_SIZE)}
          page={page + 1}
          onChange={(_, p) => setPage(p - 1)}
        />
      )}
      {toCancel && (
        <ConfirmDialog
          title="Cancel this leave request?"
          message={
            toCancel.status === "APPROVED"
              ? `Your approved leave ${datesOf(toCancel)} will be cancelled and its attendance marks removed.`
              : `Your request for ${datesOf(toCancel)} will be cancelled.`
          }
          confirmLabel="Cancel request"
          destructive
          busy={busy}
          onConfirm={() => void confirmCancel()}
          onCancel={() => setToCancel(null)}
        />
      )}
    </Box>
  );
}
