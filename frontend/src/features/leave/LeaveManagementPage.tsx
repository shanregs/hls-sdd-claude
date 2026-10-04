import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Chip,
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
import TaskAltOutlined from "@mui/icons-material/TaskAltOutlined";
import HighlightOff from "@mui/icons-material/HighlightOff";
import UndoOutlined from "@mui/icons-material/UndoOutlined";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "../attendance/monthUtils";
import {
  decideLeave,
  getLeaveList,
  type LeaveRequestView,
  type LeaveStatus,
} from "./leaveApi";
import { LeaveDecisionDialog, type Decision } from "./LeaveDecisionDialog";
import { LeaveStatusChip } from "./LeaveStatusChip";

const PAGE_SIZE = 25;

function datesOf(r: LeaveRequestView): string {
  const base =
    r.firstDate === r.lastDate
      ? formatDate(r.firstDate)
      : `${formatDate(r.firstDate)} to ${formatDate(r.lastDate)}`;
  const halves = [
    r.halfDayStart && "half day first",
    r.halfDayEnd && "half day last",
  ]
    .filter(Boolean)
    .join(", ");
  return halves ? `${base} (${halves})` : base;
}

/** OPERATIONS -> Leave Management (spec 009 US3, US4): decide leave requests in scope. */
export function LeaveManagementPage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions("/operations/leave");
  const canDecide = granted.has("APPROVE");
  const [status, setStatus] = useState<LeaveStatus | "">("PENDING");
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<LeaveRequestView[] | null>(null);
  const [total, setTotal] = useState(0);
  const [pending, setPending] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [active, setActive] = useState<{
    request: LeaveRequestView;
    decision: Decision;
  } | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await getLeaveList(authFetch, {
        status,
        page,
        size: PAGE_SIZE,
      });
      if (cancelled) return;
      if (result.ok) {
        setError(null);
        setRows(result.data.content);
        setTotal(result.data.totalElements);
        setPending(result.data.pendingCount);
      } else {
        setError(result.reason);
        setRows([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, status, page, reload]);

  const submitDecision = async (text: string): Promise<string | null> => {
    if (!active) return null;
    const result = await decideLeave(
      authFetch,
      active.request.id,
      active.decision,
      text,
      active.request.version,
    );
    if (!result.ok) return result.reason;
    setActive(null);
    setReload((n) => n + 1);
    return null;
  };

  return (
    <Box>
      <Stack direction="row" spacing={2} sx={{ alignItems: "center", mb: 2 }}>
        <Typography variant="h5" component="h1">
          Leave Management
        </Typography>
        <Chip
          label={`${pending} pending`}
          color={pending > 0 ? "warning" : "default"}
          size="small"
          aria-live="polite"
        />
      </Stack>
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
        <Typography>No leave requests to show.</Typography>
      )}
      {rows !== null && rows.length > 0 && (
        <Paper variant="outlined">
          <TableContainer>
            <Table size="small" aria-label="Leave requests">
              <TableHead>
                <TableRow>
                  <TableCell>#</TableCell>
                  <TableCell>Teacher</TableCell>
                  <TableCell>School</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Dates</TableCell>
                  <TableCell align="right">Working days</TableCell>
                  <TableCell>Reason</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((r, i) => (
                  <TableRow key={r.id} hover>
                    <TableCell>{page * PAGE_SIZE + i + 1}</TableCell>
                    <TableCell>{r.teacherName}</TableCell>
                    <TableCell>{r.schoolName}</TableCell>
                    <TableCell>{r.leaveType}</TableCell>
                    <TableCell>{datesOf(r)}</TableCell>
                    <TableCell align="right">{r.workingDays}</TableCell>
                    <TableCell>{r.reason}</TableCell>
                    <TableCell>
                      <LeaveStatusChip status={r.status} />
                    </TableCell>
                    <TableCell>
                      {canDecide && r.allowedActions.includes("APPROVE") && (
                        <Tooltip title="Approve">
                          <IconButton
                            size="small"
                            color="success"
                            aria-label={`Approve leave of ${r.teacherName}`}
                            onClick={() =>
                              setActive({ request: r, decision: "approve" })
                            }
                          >
                            <TaskAltOutlined fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                      {canDecide && r.allowedActions.includes("REJECT") && (
                        <Tooltip title="Reject">
                          <IconButton
                            size="small"
                            color="error"
                            aria-label={`Reject leave of ${r.teacherName}`}
                            onClick={() =>
                              setActive({ request: r, decision: "reject" })
                            }
                          >
                            <HighlightOff fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                      {canDecide && r.allowedActions.includes("REVOKE") && (
                        <Tooltip title="Revoke">
                          <IconButton
                            size="small"
                            color="error"
                            aria-label={`Revoke leave of ${r.teacherName}`}
                            onClick={() =>
                              setActive({ request: r, decision: "revoke" })
                            }
                          >
                            <UndoOutlined fontSize="small" />
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
      {active && (
        <LeaveDecisionDialog
          request={active.request}
          decision={active.decision}
          onSubmit={submitDecision}
          onClose={() => setActive(null)}
        />
      )}
    </Box>
  );
}
