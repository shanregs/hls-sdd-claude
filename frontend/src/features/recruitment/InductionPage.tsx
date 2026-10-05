import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Button,
  Chip,
  List,
  ListItem,
  ListItemText,
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
  AttendanceDialog,
  BatchDialog,
  EnrolDialog,
  SignOffDialog,
} from "./InductionDialogs";
import { ReasonDialog } from "./ReasonDialog";
import {
  batchRoster,
  cancelBatch,
  followUp,
  listBatches,
  readyToDeploy,
  toBeEnrolled,
  type Batch,
  type RecruitRef,
  type RosterRow,
} from "./recruitmentApi";

const ROUTE = "/recruitment/induction";

type Dialog =
  | { kind: "batch" }
  | { kind: "enrol" }
  | { kind: "attend"; row: RosterRow }
  | { kind: "signoff"; row: RosterRow }
  | { kind: "release"; row: RosterRow };

function resultLabel(row: RosterRow) {
  if (!row.result) return "In progress";
  if (row.result === "COMPLETED") return "Completed";
  return row.followUp === "NEXT_BATCH"
    ? "Not completed, moved to next batch"
    : row.followUp === "RELEASED"
      ? "Not completed, released"
      : "Not completed";
}

/** RECRUITMENT -> Induction (spec 016 US4): batches, roster with attendance, sign-off, and who is ready to deploy. */
export function InductionPage() {
  const { authFetch } = useAuth();
  const actions = useGrantedActions(ROUTE);
  const [batches, setBatches] = useState<Batch[] | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [roster, setRoster] = useState<RosterRow[]>([]);
  const [ready, setReady] = useState<RecruitRef[]>([]);
  const [waiting, setWaiting] = useState<RecruitRef[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog | null>(null);

  const load = useCallback(async () => {
    const [b, r, w] = await Promise.all([
      listBatches(authFetch),
      readyToDeploy(authFetch),
      toBeEnrolled(authFetch),
    ]);
    if (!b.ok) {
      setError(b.reason);
      return;
    }
    setError(null);
    setBatches(b.data);
    if (r.ok) setReady(r.data);
    if (w.ok) setWaiting(w.data);
    setSelected(
      (current) =>
        current ?? b.data.find((x) => x.status !== "CANCELLED")?.id ?? null,
    );
  }, [authFetch]);

  const loadRoster = useCallback(async () => {
    if (!selected) {
      setRoster([]);
      return;
    }
    const result = await batchRoster(authFetch, selected);
    if (result.ok) setRoster(result.data);
    else setError(result.reason);
  }, [authFetch, selected]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- roster follows the selected batch
    void loadRoster();
  }, [loadRoster]);

  const refresh = () => {
    setDialog(null);
    void load();
    void loadRoster();
  };

  const batch = batches?.find((b) => b.id === selected) ?? null;

  const nextBatch = async (row: RosterRow) => {
    const result = await followUp(authFetch, row.enrolmentId, "NEXT_BATCH");
    if (!result.ok) setError(result.reason);
    else refresh();
  };

  const cancel = async () => {
    if (!batch) return;
    const result = await cancelBatch(authFetch, batch.id);
    if (!result.ok) setError(result.reason);
    else {
      setSelected(null);
      refresh();
    }
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
            Induction
          </Typography>
          {actions.has("CREATE") && (
            <Button
              variant="contained"
              onClick={() => setDialog({ kind: "batch" })}
            >
              Create batch
            </Button>
          )}
        </Stack>
        {error && (
          <Alert severity="error" role="alert">
            {error}
          </Alert>
        )}
        {batches === null && !error && (
          <Typography role="status">Loading batches...</Typography>
        )}
        {batches?.length === 0 && (
          <Alert severity="info">
            No induction batches yet. Create the first batch.
          </Alert>
        )}
        {batches && batches.length > 0 && (
          <TableContainer>
            <Table size="small" aria-label="Induction batches">
              <TableHead>
                <TableRow>
                  <TableCell>Batch</TableCell>
                  <TableCell>Dates</TableCell>
                  <TableCell>Trainer</TableCell>
                  <TableCell>Seats</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>
                    <span style={{ position: "absolute", left: -9999 }}>
                      Actions
                    </span>
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {batches.map((b) => (
                  <TableRow key={b.id} selected={b.id === selected}>
                    <TableCell>{b.name}</TableCell>
                    <TableCell>
                      {formatDate(b.startsOn)} to {formatDate(b.endsOn)}
                    </TableCell>
                    <TableCell>{b.trainer ?? "-"}</TableCell>
                    <TableCell>
                      {b.enrolled} of {b.seatLimit}
                    </TableCell>
                    <TableCell>
                      {b.status.charAt(0) + b.status.slice(1).toLowerCase()}
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        onClick={() => setSelected(b.id)}
                        aria-label={`Show roster of ${b.name}`}
                      >
                        Roster
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        {batch && (
          <>
            <Stack
              direction="row"
              spacing={1}
              sx={{ alignItems: "center", flexWrap: "wrap" }}
            >
              <Typography variant="h6" component="h2">
                Roster of {batch.name}
              </Typography>
              {actions.has("CREATE") &&
                batch.status !== "CANCELLED" &&
                batch.status !== "COMPLETED" && (
                  <Button
                    size="small"
                    onClick={() => setDialog({ kind: "enrol" })}
                  >
                    Enrol a recruit
                  </Button>
                )}
              {actions.has("EDIT") && batch.status === "PLANNED" && (
                <Button
                  size="small"
                  color="error"
                  onClick={() => void cancel()}
                >
                  Cancel batch
                </Button>
              )}
            </Stack>
            {roster.length === 0 ? (
              <Typography color="text.secondary">
                No recruits enrolled yet.
              </Typography>
            ) : (
              <TableContainer>
                <Table size="small" aria-label="Roster">
                  <TableHead>
                    <TableRow>
                      <TableCell>Recruit</TableCell>
                      <TableCell>Days recorded</TableCell>
                      <TableCell>Sign-off</TableCell>
                      <TableCell>
                        <span style={{ position: "absolute", left: -9999 }}>
                          Actions
                        </span>
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {roster.map((row) => {
                      const present = row.days.reduce(
                        (sum, d) => sum + d.dayValue,
                        0,
                      );
                      const absent = row.days.filter(
                        (d) => d.status === "ABSENT",
                      ).length;
                      return (
                        <TableRow key={row.enrolmentId}>
                          <TableCell>{row.name}</TableCell>
                          <TableCell>
                            {present} present, {absent} absent
                          </TableCell>
                          <TableCell>
                            {resultLabel(row)}
                            {row.remarks ? ` - ${row.remarks}` : ""}
                          </TableCell>
                          <TableCell align="right">
                            <Stack
                              direction="row"
                              spacing={0.5}
                              sx={{
                                justifyContent: "flex-end",
                                flexWrap: "wrap",
                              }}
                            >
                              {actions.has("EDIT") && !row.result && (
                                <>
                                  <Button
                                    size="small"
                                    onClick={() =>
                                      setDialog({ kind: "attend", row })
                                    }
                                    aria-label={`Attendance of ${row.name}`}
                                  >
                                    Attendance
                                  </Button>
                                  <Button
                                    size="small"
                                    onClick={() =>
                                      setDialog({ kind: "signoff", row })
                                    }
                                    aria-label={`Sign off ${row.name}`}
                                  >
                                    Sign off
                                  </Button>
                                </>
                              )}
                              {actions.has("EDIT") &&
                                row.result === "NOT_COMPLETED" &&
                                !row.followUp && (
                                  <>
                                    <Button
                                      size="small"
                                      onClick={() => void nextBatch(row)}
                                      aria-label={`Move ${row.name} to the next batch`}
                                    >
                                      Next batch
                                    </Button>
                                    <Button
                                      size="small"
                                      color="error"
                                      onClick={() =>
                                        setDialog({ kind: "release", row })
                                      }
                                      aria-label={`Release ${row.name}`}
                                    >
                                      Release
                                    </Button>
                                  </>
                                )}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </>
        )}

        <Typography variant="h6" component="h2">
          Ready to deploy ({ready.length})
        </Typography>
        {ready.length === 0 ? (
          <Typography color="text.secondary">
            Nobody is waiting for a School yet.
          </Typography>
        ) : (
          <List dense aria-label="Ready to deploy">
            {ready.map((r) => (
              <ListItem key={r.teacherId} disableGutters>
                <ListItemText primary={r.name} />
                <Chip size="small" label="Ready" />
              </ListItem>
            ))}
          </List>
        )}
        <Typography variant="h6" component="h2">
          Waiting for a batch ({waiting.length})
        </Typography>
        {waiting.length === 0 ? (
          <Typography color="text.secondary">
            Every recruit in training is in a batch.
          </Typography>
        ) : (
          <List dense aria-label="Waiting for a batch">
            {waiting.map((r) => (
              <ListItem key={r.teacherId} disableGutters>
                <ListItemText primary={r.name} />
              </ListItem>
            ))}
          </List>
        )}
      </Stack>

      {dialog?.kind === "batch" && (
        <BatchDialog onClose={() => setDialog(null)} onSaved={refresh} />
      )}
      {dialog?.kind === "enrol" && batch && (
        <EnrolDialog
          batchId={batch.id}
          waiting={waiting}
          onClose={() => setDialog(null)}
          onSaved={refresh}
        />
      )}
      {dialog?.kind === "attend" && batch && (
        <AttendanceDialog
          row={dialog.row}
          minDate={batch.startsOn}
          maxDate={batch.endsOn}
          onClose={() => setDialog(null)}
          onSaved={refresh}
        />
      )}
      {dialog?.kind === "signoff" && (
        <SignOffDialog
          row={dialog.row}
          onClose={() => setDialog(null)}
          onSaved={refresh}
        />
      )}
      {dialog?.kind === "release" && (
        <ReasonDialog
          title={`Release ${dialog.row.name}?`}
          message="The Teacher exits with this reason."
          label="Reason"
          confirmLabel="Release"
          destructive
          onConfirm={async (reason) => {
            const result = await followUp(
              authFetch,
              dialog.row.enrolmentId,
              "RELEASE",
              reason,
            );
            if (!result.ok) return result.reason;
            refresh();
            return null;
          }}
          onCancel={() => setDialog(null)}
        />
      )}
    </Paper>
  );
}
