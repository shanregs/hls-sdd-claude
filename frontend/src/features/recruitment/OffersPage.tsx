import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Button,
  Chip,
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
import { OfferAcceptDialog } from "./OfferAcceptDialog";
import { OfferDialog } from "./OfferDialog";
import { ReasonDialog } from "./ReasonDialog";
import {
  declineOffer,
  fetchOfferLetter,
  issueOffer,
  listOffers,
  type Offer,
  type OfferStatus,
} from "./recruitmentApi";

const ROUTE = "/recruitment/offers";
const STATUSES: OfferStatus[] = [
  "DRAFT",
  "ISSUED",
  "ACCEPTED",
  "DECLINED",
  "EXPIRED",
  "SUPERSEDED",
];

const LABEL: Record<OfferStatus, string> = {
  DRAFT: "Draft",
  ISSUED: "Offered",
  ACCEPTED: "Accepted",
  DECLINED: "Declined",
  EXPIRED: "Expired",
  SUPERSEDED: "Superseded",
};

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; offers: Offer[] };

type Dialog =
  | { kind: "create" }
  | { kind: "edit"; offer: Offer }
  | { kind: "supersede"; offer: Offer }
  | { kind: "accept"; offer: Offer }
  | { kind: "decline"; offer: Offer };

/** Opens the offer letter in a new window from fetched HTML, so the access token is never put in a URL. */
async function openLetter(letter: string) {
  const blob = new Blob([letter], { type: "text/html" });
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener");
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** RECRUITMENT -> Offers (spec 016 US2 and US3): send, replace, accept, decline and print job offers. */
export function OffersPage() {
  const { authFetch } = useAuth();
  const actions = useGrantedActions(ROUTE);
  const [state, setState] = useState<State>({ kind: "loading" });
  const [status, setStatus] = useState<OfferStatus | "">("");
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [message, setMessage] = useState<{
    severity: "success" | "error";
    text: string;
  } | null>(null);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await listOffers(authFetch, { status: status || undefined });
    setState(
      result.ok
        ? { kind: "ready", offers: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, status]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  const done = (text?: string) => {
    setDialog(null);
    if (text) setMessage({ severity: "success", text });
    void load();
  };

  const issue = async (offer: Offer) => {
    const result = await issueOffer(authFetch, offer.id);
    if (!result.ok) setMessage({ severity: "error", text: result.reason });
    else done(`Offer to ${offer.candidateName} issued.`);
  };

  const letter = async (offer: Offer) => {
    const result = await fetchOfferLetter(authFetch, offer.id);
    if (!result.ok) setMessage({ severity: "error", text: result.reason });
    else await openLetter(result.data);
  };

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Stack
          direction="row"
          sx={{
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: 1,
          }}
        >
          <Typography variant="h5" component="h1">
            Offers
          </Typography>
          {actions.has("CREATE") && (
            <Button
              variant="contained"
              onClick={() => setDialog({ kind: "create" })}
            >
              New offer
            </Button>
          )}
        </Stack>
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as OfferStatus | "")}
          sx={{ maxWidth: 220 }}
        >
          <MenuItem value="">All</MenuItem>
          {STATUSES.map((s) => (
            <MenuItem key={s} value={s}>
              {LABEL[s]}
            </MenuItem>
          ))}
        </TextField>
        {message && (
          <Alert
            severity={message.severity}
            role={message.severity === "error" ? "alert" : "status"}
            onClose={() => setMessage(null)}
          >
            {message.text}
          </Alert>
        )}
        {state.kind === "loading" && (
          <Typography role="status">Loading offers...</Typography>
        )}
        {state.kind === "error" && (
          <Alert severity="error" role="alert">
            {state.reason}
          </Alert>
        )}
        {state.kind === "ready" && state.offers.length === 0 && (
          <Alert severity="info">
            No offers yet. Select a candidate at a drive, then send an offer.
          </Alert>
        )}
        {state.kind === "ready" && state.offers.length > 0 && (
          <TableContainer>
            <Table size="small" aria-label="Offers">
              <TableHead>
                <TableRow>
                  <TableCell>Candidate</TableCell>
                  <TableCell>College</TableCell>
                  <TableCell>Role</TableCell>
                  <TableCell>Monthly salary</TableCell>
                  <TableCell>Reply by</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>
                    <span style={{ position: "absolute", left: -9999 }}>
                      Actions
                    </span>
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {state.offers.map((o) => (
                  <TableRow key={o.id}>
                    <TableCell>{o.candidateName}</TableCell>
                    <TableCell>{o.college}</TableCell>
                    <TableCell>{o.role}</TableCell>
                    <TableCell>
                      {formatRupees(Number(o.monthlySalary))}
                    </TableCell>
                    <TableCell>{formatDate(o.responseDeadline)}</TableCell>
                    <TableCell>
                      <Chip size="small" label={LABEL[o.status]} />
                      {o.declineReason && (
                        <Typography variant="caption" sx={{ display: "block" }}>
                          {o.declineReason}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="right">
                      <Stack
                        direction="row"
                        sx={{ justifyContent: "flex-end", flexWrap: "wrap" }}
                        spacing={0.5}
                      >
                        <Button
                          size="small"
                          onClick={() => void letter(o)}
                          aria-label={`Letter for ${o.candidateName}`}
                        >
                          Letter
                        </Button>
                        {actions.has("EDIT") && o.status === "DRAFT" && (
                          <>
                            <Button
                              size="small"
                              onClick={() =>
                                setDialog({ kind: "edit", offer: o })
                              }
                              aria-label={`Change draft for ${o.candidateName}`}
                            >
                              Change
                            </Button>
                            <Button
                              size="small"
                              onClick={() => void issue(o)}
                              aria-label={`Issue offer to ${o.candidateName}`}
                            >
                              Issue
                            </Button>
                          </>
                        )}
                        {o.status === "ISSUED" && actions.has("CREATE") && (
                          <Button
                            size="small"
                            onClick={() =>
                              setDialog({ kind: "supersede", offer: o })
                            }
                            aria-label={`Replace offer of ${o.candidateName}`}
                          >
                            Replace
                          </Button>
                        )}
                        {o.status === "ISSUED" && actions.has("EDIT") && (
                          <>
                            <Button
                              size="small"
                              onClick={() =>
                                setDialog({ kind: "accept", offer: o })
                              }
                              aria-label={`Accept offer of ${o.candidateName}`}
                            >
                              Accept
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              onClick={() =>
                                setDialog({ kind: "decline", offer: o })
                              }
                              aria-label={`Decline offer of ${o.candidateName}`}
                            >
                              Decline
                            </Button>
                          </>
                        )}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Stack>
      {dialog?.kind === "create" && (
        <OfferDialog
          mode="create"
          onClose={() => setDialog(null)}
          onSaved={() => done("Draft offer saved.")}
        />
      )}
      {dialog?.kind === "edit" && (
        <OfferDialog
          mode="edit"
          offer={dialog.offer}
          onClose={() => setDialog(null)}
          onSaved={() => done("Draft changed.")}
        />
      )}
      {dialog?.kind === "supersede" && (
        <OfferDialog
          mode="supersede"
          offer={dialog.offer}
          onClose={() => setDialog(null)}
          onSaved={() => done("The offer was replaced.")}
        />
      )}
      {dialog?.kind === "accept" && (
        <OfferAcceptDialog
          offer={dialog.offer}
          onClose={() => setDialog(null)}
          onAccepted={() =>
            done(
              `${dialog.offer.candidateName} joined as a Teacher in training.`,
            )
          }
        />
      )}
      {dialog?.kind === "decline" && (
        <ReasonDialog
          title={`Decline the offer of ${dialog.offer.candidateName}?`}
          label="Reason"
          confirmLabel="Decline offer"
          destructive
          onConfirm={async (reason) => {
            const result = await declineOffer(
              authFetch,
              dialog.offer.id,
              reason,
            );
            if (!result.ok) return result.reason;
            done("The offer was declined.");
            return null;
          }}
          onCancel={() => setDialog(null)}
        />
      )}
    </Paper>
  );
}
