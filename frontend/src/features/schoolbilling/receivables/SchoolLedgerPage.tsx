import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Link,
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
import { useGrantedActions } from "../../common/useGrantedActions";
import { CollectionDialog, ReasonDialog } from "./Dialogs";
import {
  BILLING_ROUTE,
  MODE_LABELS,
  STATUS_LABELS,
  addAdjustment,
  allocateCollection,
  cancelDue,
  getDue,
  getLedger,
  reverseAdjustment,
  reverseCollection,
  type DueDetail,
  type DueRow,
  type SchoolLedger,
} from "./billingApi";
import { dmy, monthLabel, rupees } from "./money";

type Action =
  | { kind: "collect" }
  | { kind: "adjust"; due: DueRow }
  | { kind: "cancel"; due: DueRow }
  | { kind: "reverseCollection"; id: string }
  | { kind: "reverseAdjustment"; id: string };

/** One School's monthly dues, adjustments and collections with advance credit and balance (spec 022 US2 to US5). */
export function SchoolLedgerPage() {
  const { schoolId = "" } = useParams();
  const { authFetch } = useAuth();
  const granted = useGrantedActions(BILLING_ROUTE);
  const [ledger, setLedger] = useState<SchoolLedger | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<DueDetail | null>(null);
  const [action, setAction] = useState<Action | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    const result = await getLedger(authFetch, schoolId);
    if (result.ok) {
      setLedger(result.data);
      setError(null);
    } else {
      setError(result.reason);
    }
  }, [authFetch, schoolId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  async function openDue(id: string) {
    const result = await getDue(authFetch, id);
    if (result.ok) setOpen(result.data);
  }

  async function allocateAdvance(id: string) {
    const result = await allocateCollection(authFetch, id);
    setNotice(result.ok ? "The advance was allocated." : result.reason);
    void load();
  }

  async function run(request: Promise<{ ok: boolean; reason?: string }>): Promise<string | null> {
    const result = await request;
    if (!result.ok) return result.reason ?? "Something went wrong.";
    setAction(null);
    setOpen(null);
    void load();
    return null;
  }

  if (error) return <Alert severity="error">{error}</Alert>;
  if (!ledger) return <CircularProgress aria-label="Loading ledger" />;

  return (
    <Box>
      <Link component={RouterLink} to={BILLING_ROUTE}>
        All Schools
      </Link>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{ alignItems: { sm: "center" }, justifyContent: "space-between", my: 2 }}
      >
        <Typography variant="h5" component="h1">
          {ledger.schoolName}
        </Typography>
        {granted.has("CREATE") && (
          <Button variant="contained" onClick={() => setAction({ kind: "collect" })}>
            Record a collection
          </Button>
        )}
      </Stack>
      {notice && <Alert severity="info" onClose={() => setNotice(null)} sx={{ mb: 2 }}>{notice}</Alert>}

      <Stack direction={{ xs: "column", sm: "row" }} spacing={3} sx={{ mb: 3 }}>
        <Typography>Owed: <strong>{rupees(ledger.owed)}</strong></Typography>
        <Typography>Collected: <strong>{rupees(ledger.collected)}</strong></Typography>
        <Typography>Outstanding: <strong>{rupees(ledger.outstanding)}</strong></Typography>
        <Typography>Overdue: <strong>{rupees(ledger.overdue)}</strong></Typography>
        <Typography>Advance credit: <strong>{rupees(ledger.advanceCredit)}</strong></Typography>
      </Stack>

      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
        Monthly dues
      </Typography>
      {ledger.dues.length === 0 ? (
        <Typography color="text.secondary">No monthly dues yet.</Typography>
      ) : (
        <TableContainer sx={{ mb: 3 }}>
          <Table size="small" aria-label="Monthly dues">
            <TableHead>
              <TableRow>
                <TableCell>Reference</TableCell>
                <TableCell>Month</TableCell>
                <TableCell>Due date</TableCell>
                <TableCell align="right">Total</TableCell>
                <TableCell align="right">Balance</TableCell>
                <TableCell>Status</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {ledger.dues.map((d) => (
                <TableRow key={d.id} hover>
                  <TableCell>{d.reference}</TableCell>
                  <TableCell>{monthLabel(d.month)}</TableCell>
                  <TableCell>{dmy(d.dueOn)}</TableCell>
                  <TableCell align="right">{rupees(d.total)}</TableCell>
                  <TableCell align="right">{rupees(d.balance)}</TableCell>
                  <TableCell>
                    <Chip size="small" label={STATUS_LABELS[d.status]} />
                    {d.overdue && (
                      <Chip size="small" color="error" label={`${d.daysOverdue} days overdue`} sx={{ ml: 1 }} />
                    )}
                  </TableCell>
                  <TableCell>
                    <Button size="small" onClick={() => void openDue(d.id)}>
                      Open
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
        Collections
      </Typography>
      {ledger.collections.length === 0 ? (
        <Typography color="text.secondary">No collections yet.</Typography>
      ) : (
        <TableContainer>
          <Table size="small" aria-label="Collections">
            <TableHead>
              <TableRow>
                <TableCell>Received</TableCell>
                <TableCell>Mode</TableCell>
                <TableCell>Reference</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell align="right">Not yet allocated</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {ledger.collections.map((c) => (
                <TableRow key={c.id} hover>
                  <TableCell>{dmy(c.receivedOn)}</TableCell>
                  <TableCell>{MODE_LABELS[c.mode] ?? c.mode}</TableCell>
                  <TableCell>
                    {c.reversesId ? `Reversal: ${c.reason ?? ""}` : (c.reference ?? "")}
                    {c.reversed && <Chip size="small" label="Reversed" sx={{ ml: 1 }} />}
                  </TableCell>
                  <TableCell align="right">{rupees(c.amount)}</TableCell>
                  <TableCell align="right">{rupees(c.unallocated)}</TableCell>
                  <TableCell>
                    {granted.has("CREATE") && c.unallocated > 0 && (
                      <Button size="small" onClick={() => void allocateAdvance(c.id)}>
                        Allocate
                      </Button>
                    )}
                    {granted.has("EDIT") && !c.reversed && !c.reversesId && (
                      <Button size="small" color="warning" onClick={() => setAction({ kind: "reverseCollection", id: c.id })}>
                        Reverse
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {open && (
        <Box sx={{ mt: 3 }} role="region" aria-label="Monthly due detail">
          <Typography variant="h6" component="h2">
            {open.due.reference}, {monthLabel(open.due.month)}
          </Typography>
          <Typography color="text.secondary" sx={{ mb: 1 }}>
            Due {dmy(open.due.dueOn)}. Nothing is sent to the School.
            {open.due.cancelReason ? ` Cancelled: ${open.due.cancelReason}` : ""}
          </Typography>
          <TableContainer>
            <Table size="small" aria-label="Lines of the monthly due">
              <TableHead>
                <TableRow>
                  <TableCell>Teacher</TableCell>
                  <TableCell>Working</TableCell>
                  <TableCell align="right">Amount</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {open.lines.map((l) => (
                  <TableRow key={l.id}>
                    <TableCell>{l.teacherName}</TableCell>
                    <TableCell>{l.working}</TableCell>
                    <TableCell align="right">{rupees(l.amount)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {open.adjustmentEntries.map((a) => (
            <Typography key={a.id} variant="body2" sx={{ mt: 1 }}>
              {a.reversesId ? "Reversal" : "Adjustment"} {rupees(a.amount)}: {a.reason}
              {granted.has("EDIT") && !a.reversesId && !a.reversed && (
                <Button size="small" onClick={() => setAction({ kind: "reverseAdjustment", id: a.id })}>
                  Reverse
                </Button>
              )}
            </Typography>
          ))}
          <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
            {granted.has("EDIT") && open.due.status !== "CANCELLED" && open.due.balance > 0 && (
              <Button variant="outlined" onClick={() => setAction({ kind: "adjust", due: open.due })}>
                Add an adjustment
              </Button>
            )}
            {granted.has("APPROVE") && open.due.status !== "CANCELLED" && (
              <Button variant="outlined" color="warning" onClick={() => setAction({ kind: "cancel", due: open.due })}>
                Cancel this monthly due
              </Button>
            )}
            <Button onClick={() => setOpen(null)}>Close</Button>
          </Stack>
        </Box>
      )}

      {action?.kind === "collect" && (
        <CollectionDialog
          schoolId={schoolId}
          onClose={() => setAction(null)}
          onSaved={() => {
            setAction(null);
            void load();
          }}
        />
      )}
      {action?.kind === "adjust" && (
        <ReasonDialog
          title="Add an adjustment"
          confirm="Add adjustment"
          withAmount
          onClose={() => setAction(null)}
          onSubmit={(amount, reason) => run(addAdjustment(authFetch, action.due.id, amount, reason))}
        />
      )}
      {action?.kind === "cancel" && (
        <ReasonDialog
          title="Cancel this monthly due"
          confirm="Cancel monthly due"
          onClose={() => setAction(null)}
          onSubmit={(_, reason) => run(cancelDue(authFetch, action.due.id, reason))}
        />
      )}
      {action?.kind === "reverseCollection" && (
        <ReasonDialog
          title="Reverse this collection"
          confirm="Reverse collection"
          onClose={() => setAction(null)}
          onSubmit={(_, reason) => run(reverseCollection(authFetch, action.id, reason))}
        />
      )}
      {action?.kind === "reverseAdjustment" && (
        <ReasonDialog
          title="Reverse this adjustment"
          confirm="Reverse adjustment"
          onClose={() => setAction(null)}
          onSubmit={(_, reason) => run(reverseAdjustment(authFetch, action.id, reason))}
        />
      )}
    </Box>
  );
}
