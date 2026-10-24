import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Link,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { ConfirmDialog } from "../common/ConfirmDialog";
import { formatDate, formatRupees } from "../teachers/formatters";
import { DecisionDialog } from "./DecisionDialog";
import { RecordPaidDialog } from "./RecordPaidDialog";
import { RemoveBillDialog } from "./RemoveBillDialog";
import { ReverseDialog } from "./ReverseDialog";
import {
  EXPENSES_ROUTE,
  MODE_LABELS,
  MY_EXPENSES_ROUTE,
  PAYROLL_STATE_TEXT,
  approveClaim,
  billPath,
  deleteClaim,
  fetchBillUrl,
  getClaim,
  getMine,
  recordPaid,
  rejectClaim,
  removeBill,
  reverseClaim,
  submitClaim,
  withdrawClaim,
  type BillView,
  type ClaimDetail,
} from "./expensesApi";

type Dialog =
  | null
  | "approve"
  | "reject"
  | "reverse"
  | "paid"
  | "delete"
  | { removeBill: BillView };

interface Props {
  /** True for the Admin and Director review screen (`/operations/expenses/:id`); false for My Expenses. */
  review?: boolean;
}

function when(iso: string): string {
  return new Date(iso).toLocaleString("en-IN");
}

/**
 * One claim with its bills, link, decision, payment and history (spec 015 US1, US3, US4, US7). The buttons are exactly the
 * `allowedActions` the server sent; the server checks them again on every action.
 */
export function ClaimDetailPage({ review = false }: Props) {
  const { id = "" } = useParams();
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const [claim, setClaim] = useState<ClaimDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [busy, setBusy] = useState(false);
  const back = review ? EXPENSES_ROUTE : MY_EXPENSES_ROUTE;

  const load = useCallback(async () => {
    const result = review ? await getClaim(authFetch, id) : await getMine(authFetch, id);
    if (result.ok) {
      setClaim(result.data);
      setError(null);
    } else {
      setError(result.reason);
    }
  }, [authFetch, id, review]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  if (error && !claim) {
    return <Alert severity="error">{error}</Alert>;
  }
  if (!claim) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress aria-label="Loading the claim" />
      </Box>
    );
  }

  const can = (a: string) => (claim.allowedActions as string[]).includes(a);

  function done(updated: ClaimDetail, message?: string) {
    setClaim(updated);
    setDialog(null);
    setNotice(message ?? null);
    setError(null);
  }

  async function act(run: () => Promise<{ ok: boolean }>, success: string) {
    setBusy(true);
    const result = (await run()) as
      | { ok: true; data: ClaimDetail }
      | { ok: false; reason: string };
    setBusy(false);
    if (result.ok) {
      done(result.data, success);
    } else {
      setError(result.reason);
    }
  }

  async function openBill(bill: BillView) {
    const url = await fetchBillUrl(authFetch, billPath(review, claim!.id, bill.id));
    if (url.ok) {
      window.open(url.data, "_blank", "noopener");
    } else {
      setError(url.reason);
    }
  }

  return (
    <Box>
      <Link component={RouterLink} to={back} underline="hover">
        Back to {review ? "Expenses" : "My Expenses"}
      </Link>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{ alignItems: { sm: "center" }, justifyContent: "space-between", my: 2 }}
      >
        <Typography variant="h5" component="h1">
          {claim.categoryLabel}, {formatRupees(Number(claim.amount))}
        </Typography>
        <Chip label={claim.statusLabel} color={claim.status === "REJECTED" ? "error" : "default"} />
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {notice && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {notice}
        </Alert>
      )}
      {claim.status === "REJECTED" && claim.decision?.reason && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Rejected by {claim.decision.byName}: {claim.decision.reason}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack component="dl" spacing={1} sx={{ m: 0 }}>
          <Field term="Claimant" value={claim.claimantName} />
          <Field term="Expense date" value={formatDate(claim.expenseDate)} />
          <Field term="Area" value={claim.areaName} />
          <Field term="Paid by" value={claim.paidBy === "COMPANY" ? "The company" : "The claimant"} />
          {claim.note && <Field term="Note" value={claim.note} />}
          {claim.reason && <Field term="Reason" value={claim.reason} />}
          <Field
            term="Linked work"
            value={
              claim.link
                ? `${claim.link.kindLabel}: ${claim.link.label ?? ""} (${formatDate(claim.link.date)})`
                : "Not linked"
            }
          />
          {claim.submittedAt && <Field term="Submitted" value={when(claim.submittedAt)} />}
          {review && claim.ageDays !== null && <Field term="Waiting" value={`${claim.ageDays} days`} />}
          {claim.decision && claim.status !== "REJECTED" && (
            <Field term="Decided" value={`${claim.decision.byName}, ${when(claim.decision.at)}`} />
          )}
        </Stack>
      </Paper>

      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
        Payment
      </Typography>
      <Paper variant="outlined" sx={{ p: 2, mb: 2 }} aria-label="Payment">
        {claim.status === "PAID" && claim.payment ? (
          <Stack component="dl" spacing={1} sx={{ m: 0 }}>
            <Field
              term="How"
              value={claim.payment.how === "PAYROLL" ? "Paid with payroll" : "Paid outside payroll"}
            />
            {claim.payment.payrollMonth && (
              <Field term="Pay month" value={formatDate(claim.payment.payrollMonth)} />
            )}
            {claim.payment.paidOn && claim.payment.how === "OUTSIDE" && (
              <Field term="Paid on" value={formatDate(claim.payment.paidOn)} />
            )}
            {claim.payment.mode && <Field term="Mode" value={MODE_LABELS[claim.payment.mode]} />}
            {claim.payment.reference && <Field term="Reference" value={claim.payment.reference} />}
          </Stack>
        ) : claim.status === "PAID_BY_COMPANY" ? (
          <Typography>Paid by the company. Nothing is owed to the claimant.</Typography>
        ) : claim.status === "APPROVED" ? (
          <Typography>
            Owed to {claim.claimantName}. {PAYROLL_STATE_TEXT[claim.payrollState]}.
            {claim.payrollState === "NONE" &&
              " This person has no pay run, so it must be recorded as paid outside payroll."}
          </Typography>
        ) : (
          <Typography color="text.secondary">Not paid yet.</Typography>
        )}
      </Paper>

      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
        Bills
      </Typography>
      {claim.bills.length === 0 ? (
        <Typography sx={{ mb: 2 }}>
          {claim.noBillReason ? `No bill: ${claim.noBillReason}` : "No bills yet."}
        </Typography>
      ) : (
        <List dense sx={{ mb: 2 }}>
          {claim.bills.map((bill) => (
            <ListItem
              key={bill.id}
              secondaryAction={
                review && can("REMOVE_BILL") && !bill.removed ? (
                  <Button size="small" onClick={() => setDialog({ removeBill: bill })}>
                    Remove {bill.name}
                  </Button>
                ) : null
              }
            >
              <ListItemText
                primary={
                  bill.removed ? (
                    `${bill.name} (bill removed)`
                  ) : (
                    <Link component="button" type="button" onClick={() => void openBill(bill)}>
                      {bill.name}
                    </Link>
                  )
                }
                secondary={
                  bill.removed
                    ? `Removed by ${bill.removedByName}: ${bill.removalReason}`
                    : `Added by ${bill.addedByName}, ${when(bill.addedAt)}`
                }
              />
            </ListItem>
          ))}
        </List>
      )}

      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", mb: 3 }}>
        {!review && can("EDIT") && (
          <Button variant="outlined" component={RouterLink} to={`${MY_EXPENSES_ROUTE}/${claim.id}/edit`}>
            Edit
          </Button>
        )}
        {!review && can("SUBMIT") && (
          <Button
            variant="contained"
            disabled={busy}
            onClick={() =>
              void act(() => submitClaim(authFetch, claim.id, claim.version, false), "Submitted for review.")
            }
          >
            Submit
          </Button>
        )}
        {!review && can("WITHDRAW") && (
          <Button
            variant="outlined"
            disabled={busy}
            onClick={() => void act(() => withdrawClaim(authFetch, claim.id, claim.version), "Withdrawn to a Draft.")}
          >
            Withdraw
          </Button>
        )}
        {!review && can("DELETE") && (
          <Button color="error" variant="outlined" onClick={() => setDialog("delete")}>
            Delete draft
          </Button>
        )}
        {can("APPROVE") && (
          <Button variant="contained" onClick={() => setDialog("approve")}>
            Approve
          </Button>
        )}
        {can("REJECT") && (
          <Button color="error" variant="outlined" onClick={() => setDialog("reject")}>
            Reject
          </Button>
        )}
        {can("REVERSE") && (
          <Button variant="outlined" onClick={() => setDialog("reverse")}>
            Reverse approval
          </Button>
        )}
        {can("RECORD_PAID") && (
          <Button variant="outlined" onClick={() => setDialog("paid")}>
            Record as paid
          </Button>
        )}
        {review && claim.status === "APPROVED" && claim.payrollState === "HELD_AWAITING_APPROVAL" && (
          <Typography color="text.secondary" sx={{ alignSelf: "center" }}>
            It is in a pay run awaiting approval, so it cannot be reversed or recorded as paid now.
          </Typography>
        )}
      </Stack>

      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
        History
      </Typography>
      <Divider />
      <List dense aria-label="History">
        {claim.history.map((h, i) => (
          <ListItem key={`${h.at}-${i}`}>
            <ListItemText
              primary={`${h.event.replace(/_/g, " ").toLowerCase()} by ${h.actorName}`}
              secondary={`${when(h.at)}${h.reason ? ` - ${h.reason}` : ""}${h.detail ? ` (${h.detail})` : ""}`}
            />
          </ListItem>
        ))}
      </List>

      {(dialog === "approve" || dialog === "reject") && (
        <DecisionDialog
          mode={dialog}
          claim={claim}
          onApprove={() => approveClaim(authFetch, claim.id, claim.version)}
          onReject={(reason) => rejectClaim(authFetch, claim.id, claim.version, reason)}
          onDone={(c) => done(c, dialog === "approve" ? "Approved." : "Rejected.")}
          onCancel={() => setDialog(null)}
        />
      )}
      {dialog === "reverse" && (
        <ReverseDialog
          onReverse={(reason) => reverseClaim(authFetch, claim.id, claim.version, reason)}
          onDone={(c) => done(c, "Approval reversed.")}
          onCancel={() => setDialog(null)}
        />
      )}
      {dialog === "paid" && (
        <RecordPaidDialog
          onRecord={(body) => recordPaid(authFetch, claim.id, { ...body, version: claim.version })}
          onDone={(c) => done(c, "Recorded as paid.")}
          onCancel={() => setDialog(null)}
        />
      )}
      {typeof dialog === "object" && dialog !== null && (
        <RemoveBillDialog
          bill={dialog.removeBill}
          onRemove={(reason) => removeBill(authFetch, claim.id, dialog.removeBill.id, reason)}
          onDone={(c) => done(c, "Bill removed.")}
          onCancel={() => setDialog(null)}
        />
      )}
      {dialog === "delete" && (
        <ConfirmDialog
          title="Delete this draft"
          message="The draft and its bills are removed. This cannot be undone."
          confirmLabel="Delete draft"
          destructive
          busy={busy}
          onConfirm={async () => {
            setBusy(true);
            const result = await deleteClaim(authFetch, claim.id);
            setBusy(false);
            if (result.ok) {
              navigate(MY_EXPENSES_ROUTE);
            } else {
              setDialog(null);
              setError(result.reason);
            }
          }}
          onCancel={() => setDialog(null)}
        />
      )}
    </Box>
  );
}

function Field({ term, value }: { term: string; value: string }) {
  return (
    <Box sx={{ display: "flex", gap: 2 }}>
      <Typography component="dt" color="text.secondary" sx={{ minWidth: 140 }}>
        {term}
      </Typography>
      <Typography component="dd" sx={{ m: 0 }}>
        {value}
      </Typography>
    </Box>
  );
}
