import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Autocomplete,
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
import { useAuth } from "../../../auth/useAuth";
import { useGrantedActions } from "../../common/useGrantedActions";
import {
  listPeople,
  PERSON_KIND_LABELS as PEOPLE_KIND_LABELS,
  type PersonRow,
} from "../salaryStructuresApi";
import { ADJUSTMENT_KIND_LABELS } from "./lineWorking";
import { ReasonDialog } from "./ReasonDialog";
import {
  listAdjustments,
  recordAdjustment,
  withdrawAdjustment,
  type Adjustment,
  type AdjustmentStatus,
} from "./payrollRunApi";
import {
  amountProblem,
  dateTime,
  MAX_REASON,
  money,
  reasonProblem,
} from "./format";

const ROUTE = "/operations/payroll";

/** The kinds that exist but are created by later specs: shown so nobody wonders where they are, and not selectable. */
const RESERVED: { kind: string; spec: string }[] = [
  { kind: "INCENTIVE", spec: "spec 030" },
  { kind: "REIMBURSEMENT", spec: "spec 015" },
  { kind: "SUBSTITUTE_CREDIT", spec: "spec 017b" },
];

const STATUS_LABELS: Record<AdjustmentStatus, string> = {
  WAITING: "Waiting for a run",
  APPLIED: "Applied",
  WITHDRAWN: "Withdrawn",
};

type ListState =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; items: Adjustment[] };

/**
 * Corrections that wait for the next run (spec 013b US4): record one against a person with a signed amount and a
 * reason, see where each stands, and take a waiting one back. The next draft run takes it as its own line item; once a
 * run is approved it is applied and never changes.
 */
export function AdjustmentsPage() {
  const { authFetch } = useAuth();
  const canProcess = useGrantedActions(ROUTE).has("PROCESS");

  const [list, setList] = useState<ListState>({ kind: "loading" });
  const [statusFilter, setStatusFilter] = useState<AdjustmentStatus | "">("");

  const [people, setPeople] = useState<PersonRow[]>([]);
  const [search, setSearch] = useState("");
  const [person, setPerson] = useState<PersonRow | null>(null);
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [withdrawing, setWithdrawing] = useState<Adjustment | null>(null);

  const load = useCallback(async () => {
    const result = await listAdjustments(authFetch, {
      status: statusFilter,
    });
    setList(
      result.ok
        ? { kind: "ready", items: result.data.content }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, statusFilter]);

  useEffect(() => {
    void (async () => {
      await load();
    })();
  }, [load]);

  // the person picker asks the server as the Admin types
  useEffect(() => {
    if (!canProcess) return;
    let cancelled = false;
    const timer = setTimeout(async () => {
      const result = await listPeople(authFetch, {
        kind: "",
        q: search,
        withoutSalary: false,
        page: 0,
        size: 20,
      });
      if (!cancelled && result.ok) setPeople(result.data.content);
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [authFetch, canProcess, search]);

  const problems = {
    person: person ? null : "Choose the person.",
    amount: amountProblem(amount),
    reason: reasonProblem(reason),
  };
  const show = (key: keyof typeof problems) =>
    submitted ? problems[key] : null;

  const submit = async () => {
    setSubmitted(true);
    setSaved(false);
    setServerError(null);
    if (problems.person || problems.amount || problems.reason || !person)
      return;
    setSaving(true);
    const result = await recordAdjustment(authFetch, {
      personKind: person.kind,
      personId: person.personId,
      kind: "CORRECTION",
      amount: amount.trim(),
      reason: reason.trim(),
    });
    setSaving(false);
    if (!result.ok) {
      setServerError(result.reason);
      return;
    }
    setSaved(true);
    setSubmitted(false);
    setAmount("");
    setReason("");
    setPerson(null);
    await load();
  };

  const withdraw = async (why: string): Promise<string | null> => {
    if (!withdrawing) return null;
    const result = await withdrawAdjustment(authFetch, withdrawing.id, why);
    if (!result.ok) return result.reason;
    setWithdrawing(null);
    await load();
    return null;
  };

  return (
    <Stack spacing={3} sx={{ p: { xs: 2, md: 3 } }}>
      <Box>
        <Link component={RouterLink} to={ROUTE}>
          All pay runs
        </Link>
        <Typography variant="h1" sx={{ fontSize: "1.75rem", mt: 1 }}>
          Adjustments
        </Typography>
        <Typography color="text.secondary">
          A correction waits here until the next pay run takes it as its own
          line item. Once that run is approved it is applied and cannot change.
        </Typography>
      </Box>

      {canProcess && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="h2" sx={{ fontSize: "1.1rem", mb: 1.5 }}>
            Record an adjustment
          </Typography>
          {serverError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {serverError}
            </Alert>
          )}
          {saved && (
            <Alert severity="success" sx={{ mb: 2 }}>
              The adjustment was recorded. The next run takes it.
            </Alert>
          )}
          <Stack spacing={2} sx={{ maxWidth: 560 }}>
            <Autocomplete
              options={people}
              value={person}
              onChange={(_, next) => setPerson(next)}
              onInputChange={(_, text) => setSearch(text)}
              getOptionLabel={(p) => p.name}
              getOptionKey={(p) => `${p.kind}-${p.personId}`}
              isOptionEqualToValue={(a, b) =>
                a.kind === b.kind && a.personId === b.personId
              }
              filterOptions={(options) => options}
              renderOption={(props, p) => (
                <li {...props} key={`${p.kind}-${p.personId}`}>
                  {p.name} ({PEOPLE_KIND_LABELS[p.kind]})
                </li>
              )}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Person"
                  required
                  error={show("person") !== null}
                  helperText={show("person")}
                />
              )}
            />
            <TextField
              select
              label="Kind"
              value="CORRECTION"
              helperText="Only corrections can be recorded by hand."
            >
              <MenuItem value="CORRECTION">
                {ADJUSTMENT_KIND_LABELS.CORRECTION}
              </MenuItem>
              {RESERVED.map((r) => (
                <MenuItem key={r.kind} value={r.kind} disabled>
                  {ADJUSTMENT_KIND_LABELS[r.kind]} (created by {r.spec})
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Amount (rupees, use - for a deduction)"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              required
              error={show("amount") !== null}
              helperText={show("amount")}
              slotProps={{ htmlInput: { inputMode: "decimal" } }}
            />
            <TextField
              label="Reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              required
              multiline
              minRows={2}
              error={show("reason") !== null}
              helperText={show("reason") ?? `${reason.length}/${MAX_REASON}`}
              slotProps={{ htmlInput: { maxLength: MAX_REASON + 50 } }}
            />
            <Box>
              <Button
                variant="contained"
                onClick={() => void submit()}
                disabled={saving}
              >
                Record adjustment
              </Button>
            </Box>
          </Stack>
        </Paper>
      )}

      <Box>
        <Stack
          direction="row"
          sx={{ gap: 2, flexWrap: "wrap", alignItems: "center", mb: 2 }}
        >
          <Typography variant="h2" sx={{ fontSize: "1.25rem" }}>
            Recorded adjustments
          </Typography>
          <TextField
            select
            label="Status"
            value={statusFilter}
            onChange={(e) =>
              setStatusFilter(e.target.value as AdjustmentStatus | "")
            }
            sx={{ minWidth: 200 }}
          >
            <MenuItem value="">All</MenuItem>
            {(Object.keys(STATUS_LABELS) as AdjustmentStatus[]).map((s) => (
              <MenuItem key={s} value={s}>
                {STATUS_LABELS[s]}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
        {list.kind === "loading" && (
          <Stack direction="row" sx={{ gap: 1.5, alignItems: "center" }}>
            <CircularProgress size={22} aria-label="Loading the adjustments" />
            <Typography>Loading the adjustments…</Typography>
          </Stack>
        )}
        {list.kind === "error" && <Alert severity="error">{list.reason}</Alert>}
        {list.kind === "ready" && list.items.length === 0 && (
          <Alert severity="info">No adjustment has been recorded yet.</Alert>
        )}
        {list.kind === "ready" && list.items.length > 0 && (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small" aria-label="Adjustments">
              <TableHead>
                <TableRow>
                  <TableCell>Person</TableCell>
                  <TableCell>Kind</TableCell>
                  <TableCell align="right">Amount</TableCell>
                  <TableCell>Reason</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Recorded</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {list.items.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell>{a.personName}</TableCell>
                    <TableCell>
                      {ADJUSTMENT_KIND_LABELS[a.kind] ?? a.kind}
                    </TableCell>
                    <TableCell align="right">{money(a.amount)}</TableCell>
                    <TableCell>
                      {a.reason}
                      {a.withdrawReason && (
                        <Typography variant="caption" sx={{ display: "block" }}>
                          Withdrawn: {a.withdrawReason}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={STATUS_LABELS[a.status]}
                        color={a.status === "APPLIED" ? "success" : "default"}
                        variant={a.status === "WAITING" ? "outlined" : "filled"}
                      />
                      {a.status === "WAITING" && a.attachedRunId && (
                        <Typography variant="caption" sx={{ display: "block" }}>
                          In a draft run
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      {dateTime(a.createdAt)}
                      {a.createdBy ? ` by ${a.createdBy.name}` : ""}
                    </TableCell>
                    <TableCell>
                      {canProcess && a.status === "WAITING" && (
                        <Button
                          size="small"
                          color="error"
                          aria-label={`Withdraw the adjustment for ${a.personName}`}
                          onClick={() => setWithdrawing(a)}
                        >
                          Withdraw
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Box>

      <ReasonDialog
        open={withdrawing !== null}
        title="Withdraw this adjustment?"
        description={
          withdrawing
            ? `${withdrawing.personName}: ${money(withdrawing.amount)} (${withdrawing.reason}). It stays in the history and is never applied.`
            : ""
        }
        reasonLabel="Reason for withdrawing"
        confirmLabel="Withdraw"
        dismissLabel="Keep it"
        confirmColor="error"
        onConfirm={withdraw}
        onClose={() => setWithdrawing(null)}
      />
    </Stack>
  );
}
