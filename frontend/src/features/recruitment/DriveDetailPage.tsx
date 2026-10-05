import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Button,
  Chip,
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
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "../teachers/formatters";
import {
  AddCandidateDialog,
  AssessmentDialog,
  HistoryDialog,
  ImportDialog,
  OutcomeDialog,
} from "./CandidateDialogs";
import { DriveDialog } from "./DriveDialog";
import { ReasonDialog } from "./ReasonDialog";
import {
  getDrive,
  listCandidates,
  setDriveStatus,
  type Candidate,
  type Drive,
} from "./recruitmentApi";

const ROUTE = "/recruitment/drives";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; drive: Drive; candidates: Candidate[] };

type Dialog =
  | { kind: "add" }
  | { kind: "import" }
  | { kind: "edit" }
  | { kind: "cancel" }
  | { kind: "outcome"; candidate: Candidate }
  | { kind: "assess"; candidate: Candidate }
  | { kind: "history"; candidate: Candidate };

function scoreText(c: Candidate) {
  if (!c.assessment) return "Not assessed";
  const s = c.assessment.scores;
  return `Speaking ${s.SPEAKING ?? "-"}, English ${s.ENGLISH ?? "-"}, Communication ${s.COMMUNICATION ?? "-"}`;
}

/**
 * A drive with its candidates (spec 016 US1). Write controls show only to people who may change this drive: Admin
 * and Director always, a Zone Manager only for a drive they scheduled or attend (the server enforces the same).
 */
export function DriveDetailPage() {
  const { id = "" } = useParams();
  const { authFetch, user } = useAuth();
  const navigate = useNavigate();
  const actions = useGrantedActions(ROUTE);
  const [state, setState] = useState<State>({ kind: "loading" });
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [drive, candidates] = await Promise.all([
      getDrive(authFetch, id),
      listCandidates(authFetch, { drive: id }),
    ]);
    if (!drive.ok) setState({ kind: "error", reason: drive.reason });
    else if (!candidates.ok)
      setState({ kind: "error", reason: candidates.reason });
    else
      setState({
        kind: "ready",
        drive: drive.data,
        candidates: candidates.data,
      });
  }, [authFetch, id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  if (state.kind === "loading")
    return <Typography role="status">Loading drive...</Typography>;
  if (state.kind === "error")
    return (
      <Alert severity="error" role="alert">
        {state.reason}
      </Alert>
    );

  const { drive, candidates } = state;
  const orgWide = !!user?.roles.some((r) => r === "ADMIN" || r === "DIRECTOR");
  const mine =
    orgWide ||
    drive.scheduledBy === user?.id ||
    drive.interviewers.some((p) => p.userId === user?.id);
  const canEdit = mine && actions.has("EDIT");
  const canCreate = mine && actions.has("CREATE");
  const open = drive.status !== "CANCELLED";

  const closeAndReload = () => {
    setDialog(null);
    void load();
  };

  const hold = async () => {
    const result = await setDriveStatus(authFetch, drive.id, "HELD");
    if (!result.ok) setActionError(result.reason);
    else void load();
  };

  const cancel = async (reason: string): Promise<string | null> => {
    const result = await setDriveStatus(
      authFetch,
      drive.id,
      "CANCELLED",
      reason,
    );
    if (!result.ok) return result.reason;
    setDialog(null);
    void load();
    return null;
  };

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Button
          onClick={() => navigate("/recruitment/drives")}
          sx={{ alignSelf: "flex-start" }}
        >
          Back to drives
        </Button>
        <Typography variant="h5" component="h1">
          {drive.college.name}, {drive.college.city}
        </Typography>
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: "center", flexWrap: "wrap" }}
        >
          <Chip
            label={
              drive.status === "HELD"
                ? "Held"
                : drive.status === "CANCELLED"
                  ? "Cancelled"
                  : "Planned"
            }
          />
          <Typography>{drive.dates.map(formatDate).join(", ")}</Typography>
          {drive.venue && <Typography>at {drive.venue}</Typography>}
          {drive.season && <Typography>Season {drive.season}</Typography>}
        </Stack>
        <Typography variant="body2">
          Interviewers:{" "}
          {drive.interviewers.length
            ? drive.interviewers.map((p) => p.name).join(", ")
            : "none recorded"}
        </Typography>
        {drive.cancelReason && (
          <Alert severity="warning">Cancelled: {drive.cancelReason}</Alert>
        )}
        {actionError && (
          <Alert severity="error" role="alert">
            {actionError}
          </Alert>
        )}
        {!mine && actions.has("VIEW") && (
          <Alert severity="info">
            This is another person's drive; you can read it but not change it.
          </Alert>
        )}
        <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
          {canEdit && open && (
            <Button onClick={() => setDialog({ kind: "edit" })}>
              Change drive
            </Button>
          )}
          {canEdit && drive.status === "PLANNED" && (
            <Button onClick={() => void hold()}>Mark held</Button>
          )}
          {canEdit && open && (
            <Button color="error" onClick={() => setDialog({ kind: "cancel" })}>
              Cancel drive
            </Button>
          )}
          {canCreate && open && (
            <Button onClick={() => setDialog({ kind: "add" })}>
              Add candidate
            </Button>
          )}
          {canCreate && open && (
            <Button onClick={() => setDialog({ kind: "import" })}>
              Import CSV
            </Button>
          )}
        </Stack>
        <Typography variant="body2">
          {drive.candidates} candidates: {drive.outcomes.SELECTED ?? 0}{" "}
          selected, {drive.outcomes.WAITLISTED ?? 0} waitlisted,{" "}
          {drive.outcomes.REJECTED ?? 0} rejected, {drive.outcomes.PENDING ?? 0}{" "}
          not decided.
        </Typography>
        {candidates.length === 0 ? (
          <Alert severity="info">
            No candidates yet. Add one or import a CSV file.
          </Alert>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Candidates">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Phone</TableCell>
                  <TableCell>Degree</TableCell>
                  <TableCell>Assessment</TableCell>
                  <TableCell>Outcome</TableCell>
                  <TableCell>
                    <span style={{ position: "absolute", left: -9999 }}>
                      Actions
                    </span>
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {candidates.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell>
                      {c.name}
                      {c.teacherId && (
                        <Chip size="small" label="Joined" sx={{ ml: 1 }} />
                      )}
                    </TableCell>
                    <TableCell>{c.phone}</TableCell>
                    <TableCell>
                      {[c.degree, c.year].filter(Boolean).join(", ") || "-"}
                    </TableCell>
                    <TableCell>{scoreText(c)}</TableCell>
                    <TableCell>
                      {c.outcome
                        ? c.outcome.charAt(0) + c.outcome.slice(1).toLowerCase()
                        : "Not decided"}
                    </TableCell>
                    <TableCell align="right">
                      <Stack
                        direction="row"
                        spacing={0.5}
                        sx={{ justifyContent: "flex-end" }}
                      >
                        {canEdit && !c.teacherId && (
                          <Button
                            size="small"
                            onClick={() =>
                              setDialog({ kind: "outcome", candidate: c })
                            }
                            aria-label={`Set outcome of ${c.name}`}
                          >
                            Outcome
                          </Button>
                        )}
                        {canEdit && (
                          <Button
                            size="small"
                            onClick={() =>
                              setDialog({ kind: "assess", candidate: c })
                            }
                            aria-label={`Assess ${c.name}`}
                          >
                            Assess
                          </Button>
                        )}
                        <Button
                          size="small"
                          onClick={() =>
                            setDialog({ kind: "history", candidate: c })
                          }
                          aria-label={`History of ${c.name}`}
                        >
                          History
                        </Button>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Stack>

      {dialog?.kind === "add" && (
        <AddCandidateDialog
          driveId={drive.id}
          onClose={() => setDialog(null)}
          onSaved={closeAndReload}
        />
      )}
      {dialog?.kind === "import" && (
        <ImportDialog
          driveId={drive.id}
          onClose={closeAndReload}
          onDone={() => void load()}
        />
      )}
      {dialog?.kind === "edit" && (
        <DriveDialog
          drive={drive}
          onClose={() => setDialog(null)}
          onSaved={closeAndReload}
        />
      )}
      {dialog?.kind === "outcome" && (
        <OutcomeDialog
          candidate={dialog.candidate}
          onClose={() => setDialog(null)}
          onSaved={closeAndReload}
        />
      )}
      {dialog?.kind === "assess" && (
        <AssessmentDialog
          candidate={dialog.candidate}
          onClose={() => setDialog(null)}
          onSaved={closeAndReload}
        />
      )}
      {dialog?.kind === "history" && (
        <HistoryDialog
          candidate={dialog.candidate}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "cancel" && (
        <ReasonDialog
          title="Cancel this drive?"
          message="The candidates stay on record."
          label="Reason for cancelling"
          confirmLabel="Cancel drive"
          destructive
          onConfirm={cancel}
          onCancel={() => setDialog(null)}
        />
      )}
    </Paper>
  );
}
