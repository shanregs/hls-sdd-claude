import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  Tab,
  Tabs,
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
import { useAccessModel } from "../../access-model/useAccessModel";
import { formatDate, formatRupees } from "../teachers/formatters";
import {
  ATTENDANCE_LABELS,
  PAYOUTS_ROUTE,
  STATUS_LABELS,
  downloadCsv,
  listDays,
  listMismatches,
  lockAllMonths,
  markDay,
  raiseCorrection,
  reopenMonth,
  runMigration,
  type DayPage,
  type DayRow,
  type DayStatus,
  type Mismatch,
  type MigrationResult,
} from "./payoutsApi";

type Load =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; page: DayPage };

/** Chip colour by what a person must do next: payment problems are loud, finished states are quiet. */
function chipColor(status: DayStatus): "default" | "success" | "warning" | "error" | "info" {
  switch (status) {
    case "PAID":
    case "CORRECTED":
      return "success";
    case "CANNOT_PAY":
    case "CORRECTION_NEEDED":
    case "CHANGED_RUN_AWAITING":
      return "error";
    case "BACKPAY_WAITING":
    case "CORRECTION_RAISED":
    case "ALLOCATED":
      return "warning";
    case "CANCELLED":
    case "CANCELLED_BEFORE_PAYMENT":
    case "DID_NOT_ATTEND":
      return "default";
    default:
      return "info";
  }
}

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

/**
 * PAYROLL & FINANCE -> Substitute Payouts (spec 017b): every day a substitute was allocated, with who marked it, where
 * it stands on the way to payment and what it is worth. Zone Managers mark Present or Absent; Admin and Director lock
 * and reopen a month. A substitute has no account, so this is the only view of their days.
 */
export function SubstitutePayoutsPage() {
  const { authFetch } = useAuth();
  const { accessModel } = useAccessModel();
  const item = accessModel?.navigation
    .flatMap((s) => s.items)
    .find((i) => i.route === PAYOUTS_ROUTE);
  const canApprove = Boolean(item?.actions.includes("APPROVE"));
  const canExport = Boolean(item?.actions.includes("EXPORT"));
  const isAdmin = (accessModel?.roles ?? []).includes("ADMIN");
  const [tab, setTab] = useState<"days" | "attention">("days");
  const [attention, setAttention] = useState<
    { kind: "loading" } | { kind: "error"; reason: string } | { kind: "ready"; rows: Mismatch[] }
  >({ kind: "loading" });
  const [correcting, setCorrecting] = useState<Mismatch | null>(null);
  const [correctionAmount, setCorrectionAmount] = useState("");
  const [correctionReason, setCorrectionReason] = useState("");
  const [correctionError, setCorrectionError] = useState<string | null>(null);
  const [migration, setMigration] = useState<MigrationResult | null>(null);

  const [month, setMonth] = useState(currentMonth());
  const [status, setStatus] = useState<DayStatus | "">("");
  const [load, setLoad] = useState<Load>({ kind: "loading" });
  const [message, setMessage] = useState<{ severity: "success" | "error" | "info"; text: string } | null>(null);
  const [reopening, setReopening] = useState<DayRow | null>(null);
  const [reason, setReason] = useState("");

  const reload = useCallback(async () => {
    setLoad({ kind: "loading" });
    const result = await listDays(authFetch, {
      month: month || undefined,
      status: status || undefined,
      size: 100,
    });
    setLoad(
      result.ok
        ? { kind: "ready", page: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, month, status]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void reload();
  }, [reload]);

  const loadAttention = useCallback(async () => {
    setAttention({ kind: "loading" });
    const result = await listMismatches(authFetch, month);
    setAttention(
      result.ok
        ? { kind: "ready", rows: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, month]);

  useEffect(() => {
    if (tab !== "attention") return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- fetch when the tab opens or the month changes
    void loadAttention();
  }, [tab, loadAttention]);

  async function exportCsv() {
    setMessage(null);
    const result = await downloadCsv(authFetch, { month: month || undefined, status: status || undefined });
    if (!result.ok) setMessage({ severity: "error", text: result.reason });
  }

  async function migrate() {
    setMessage(null);
    const result = await runMigration(authFetch);
    if (!result.ok) {
      setMessage({ severity: "error", text: result.reason });
      return;
    }
    setMigration(result.data);
    await reload();
  }

  async function saveCorrection() {
    if (!correcting) return;
    setCorrectionError(null);
    const result = await raiseCorrection(authFetch, correcting.day.id, Number(correctionAmount), correctionReason);
    if (!result.ok) {
      setCorrectionError(result.reason);
      return;
    }
    setCorrecting(null);
    setCorrectionAmount("");
    setCorrectionReason("");
    await loadAttention();
  }

  async function mark(row: DayRow, attendance: "PRESENT" | "ABSENT") {
    setMessage(null);
    const result = await markDay(authFetch, row, attendance);
    if (!result.ok) {
      setMessage({ severity: "error", text: result.reason });
      return;
    }
    await reload();
  }

  async function lockAll() {
    setMessage(null);
    const result = await lockAllMonths(authFetch, month);
    if (!result.ok) {
      setMessage({ severity: "error", text: result.reason });
      return;
    }
    const { locked, alreadyLocked, blocked } = result.data;
    setMessage({
      severity: blocked.length > 0 ? "info" : "success",
      text:
        `${locked} locked, ${alreadyLocked} already locked` +
        (blocked.length > 0 ? `, ${blocked.length} not ready (${blocked[0].reason})` : "") +
        ".",
    });
    await reload();
  }

  async function reopen() {
    if (!reopening) return;
    const result = await reopenMonth(
      authFetch,
      reopening.substituteTeacherId,
      reopening.workMonth.slice(0, 7),
      reason,
    );
    if (!result.ok) {
      setMessage({ severity: "error", text: result.reason });
      return;
    }
    setReopening(null);
    setReason("");
    await reload();
  }

  return (
    <Box>
      <Stack
        direction="row"
        spacing={1}
        sx={{ mb: 2, justifyContent: "space-between", alignItems: "center", flexWrap: "wrap" }}
      >
        <Typography variant="h5" component="h1">
          Substitute Payouts
        </Typography>
        <Stack direction="row" spacing={1}>
          {canExport && (
            <Button variant="outlined" onClick={() => void exportCsv()}>
              Export CSV
            </Button>
          )}
          {canApprove && month && (
            <Button variant="outlined" onClick={() => void lockAll()}>
              Lock all complete substitutes for {month}
            </Button>
          )}
        </Stack>
      </Stack>

      <Tabs
        value={tab}
        onChange={(_e, value: "days" | "attention") => setTab(value)}
        aria-label="Substitute payout views"
        sx={{ mb: 2 }}
      >
        <Tab value="days" label="All days" />
        <Tab value="attention" label="Needs attention" />
      </Tabs>

      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          size="small"
          type="month"
          label="Month"
          value={month}
          onChange={(e) => setMonth(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as DayStatus | "")}
          sx={{ minWidth: 260 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          {(Object.keys(STATUS_LABELS) as DayStatus[]).map((s) => (
            <MenuItem key={s} value={s}>
              {STATUS_LABELS[s]}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {message && (
        <Alert severity={message.severity} role={message.severity === "error" ? "alert" : "status"} sx={{ mb: 2 }}>
          {message.text}
        </Alert>
      )}

      {tab === "days" && isAdmin && (
        <Box sx={{ mb: 2 }}>
          <Button size="small" variant="text" onClick={() => void migrate()}>
            Move existing substitute credits into day records (go-live)
          </Button>
          {migration && (
            <Alert severity={migration.settleFirst.length > 0 ? "warning" : "success"} sx={{ mt: 1 }}>
              {migration.migrated} credits moved and marked Present for review, {migration.futureAllocations} future
              allocations, {migration.alreadyRecorded} already recorded.
              {migration.settleFirst.length > 0 && (
                <>
                  {" "}
                  Settle first (not Substitute Teachers):{" "}
                  {migration.settleFirst.map((p) => `${p.name} (${p.credits})`).join(", ")}.
                </>
              )}
            </Alert>
          )}
        </Box>
      )}

      {tab === "attention" && (
        <>
          {attention.kind === "loading" && <CircularProgress aria-label="Loading days that need attention" />}
          {attention.kind === "error" && (
            <Alert severity="error" role="alert">
              {attention.reason}
            </Alert>
          )}
          {attention.kind === "ready" && attention.rows.length === 0 && (
            <Typography color="text.secondary">Nothing needs attention for {month}.</Typography>
          )}
          {attention.kind === "ready" && attention.rows.length > 0 && (
            <TableContainer>
              <Table size="small" aria-label="Days that need attention">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Substitute</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Why</TableCell>
                    <TableCell>Next step</TableCell>
                    <TableCell>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {attention.rows.map((m) => (
                    <TableRow key={m.day.id}>
                      <TableCell>{formatDate(m.day.date)}</TableCell>
                      <TableCell>{m.day.substituteName}</TableCell>
                      <TableCell>
                        <Chip size="small" color={chipColor(m.day.status)} label={m.day.statusText} />
                      </TableCell>
                      <TableCell>{m.reason}</TableCell>
                      <TableCell>{m.nextStep}</TableCell>
                      <TableCell>
                        {isAdmin && m.day.status === "CORRECTION_NEEDED" && (
                          <Button
                            size="small"
                            aria-label={`Raise correction for ${m.day.substituteName} on ${formatDate(m.day.date)}`}
                            onClick={() => {
                              setCorrecting(m);
                              setCorrectionAmount(m.suggestedCorrection === null ? "" : String(m.suggestedCorrection));
                              setCorrectionReason("");
                              setCorrectionError(null);
                            }}
                          >
                            Raise correction
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </>
      )}

      {tab === "days" && load.kind === "loading" && <CircularProgress aria-label="Loading substitute days" />}
      {tab === "days" && load.kind === "error" && (
        <Alert severity="error" role="alert">
          {load.reason}
        </Alert>
      )}
      {tab === "days" && load.kind === "ready" && (
        <>
          <Typography sx={{ mb: 2 }} aria-live="polite">
            Allocated <strong>{load.page.totals.allocated}</strong> · Attended{" "}
            <strong>{load.page.totals.attended}</strong> · Paid <strong>{load.page.totals.paid}</strong> · Amount paid{" "}
            <strong>{formatRupees(load.page.totals.amountPaid)}</strong>
          </Typography>
          {load.page.content.length === 0 ? (
            <Typography color="text.secondary">No substitute days for these filters.</Typography>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Substitute days">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Substitute</TableCell>
                    <TableCell>School</TableCell>
                    <TableCell align="right">Day</TableCell>
                    <TableCell>Attendance</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Rate</TableCell>
                    <TableCell align="right">Amount</TableCell>
                    <TableCell>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {load.page.content.map((row) => (
                    <TableRow key={row.id} hover>
                      <TableCell>{formatDate(row.date)}</TableCell>
                      <TableCell>
                        {row.substituteName}
                        {row.needsReview && (
                          <Chip size="small" color="warning" label="Review" sx={{ ml: 0.5 }} />
                        )}
                      </TableCell>
                      <TableCell>{row.schoolName}</TableCell>
                      <TableCell align="right">{row.dayValue === 1 ? "Full" : "Half"}</TableCell>
                      <TableCell>{ATTENDANCE_LABELS[row.attendance]}</TableCell>
                      <TableCell>
                        <Chip size="small" color={chipColor(row.status)} label={row.statusText} />
                        {row.mismatchReason && (
                          <Typography variant="caption" color="text.secondary" component="div">
                            {row.mismatchReason}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align="right">{row.rate === null ? "" : formatRupees(row.rate)}</TableCell>
                      <TableCell align="right">{row.amount === null ? "" : formatRupees(row.amount)}</TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1}>
                          {row.actions.includes("MARK") && (
                            <>
                              <Button
                                size="small"
                                disabled={row.attendance === "PRESENT"}
                                aria-label={`Mark ${row.substituteName} present on ${formatDate(row.date)}`}
                                onClick={() => void mark(row, "PRESENT")}
                              >
                                Present
                              </Button>
                              <Button
                                size="small"
                                disabled={row.attendance === "ABSENT"}
                                aria-label={`Mark ${row.substituteName} absent on ${formatDate(row.date)}`}
                                onClick={() => void mark(row, "ABSENT")}
                              >
                                Absent
                              </Button>
                            </>
                          )}
                          {canApprove && row.locked && (
                            <Button
                              size="small"
                              aria-label={`Reopen ${row.substituteName} for ${row.workMonth.slice(0, 7)}`}
                              onClick={() => setReopening(row)}
                            >
                              Reopen month
                            </Button>
                          )}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </>
      )}

      <Dialog open={correcting !== null} onClose={() => setCorrecting(null)} fullWidth maxWidth="xs">
        <DialogTitle>
          Raise a correction for {correcting?.day.substituteName}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {correctionError && (
              <Alert severity="error" role="alert">
                {correctionError}
              </Alert>
            )}
            <Typography variant="body2" color="text.secondary">
              {correcting?.reason} A positive amount pays more; a negative amount recovers. It is paid with the next
              pay run.
            </Typography>
            <TextField
              label="Amount (₹)"
              type="number"
              required
              value={correctionAmount}
              onChange={(e) => setCorrectionAmount(e.target.value)}
            />
            <TextField
              label="Reason"
              required
              multiline
              minRows={2}
              value={correctionReason}
              onChange={(e) => setCorrectionReason(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCorrecting(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={Number(correctionAmount) === 0 || correctionReason.trim().length === 0}
            onClick={() => void saveCorrection()}
          >
            Raise correction
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={reopening !== null} onClose={() => setReopening(null)} fullWidth maxWidth="xs">
        <DialogTitle>Reopen {reopening?.workMonth.slice(0, 7)} for {reopening?.substituteName}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            required
            fullWidth
            multiline
            minRows={2}
            margin="dense"
            label="Reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReopening(null)}>Cancel</Button>
          <Button variant="contained" disabled={reason.trim().length === 0} onClick={() => void reopen()}>
            Reopen
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
