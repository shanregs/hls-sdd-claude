import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { ReasonDialog } from "../recruitment/ReasonDialog";
import {
  ACTIVE_STAGES,
  STAGE_LABEL,
  getPipeline,
  moveStage,
  reviewProspect,
  type Board,
  type ProspectRow,
  type ShownStage,
  type StoredStage,
} from "./marketingApi";

const ROUTE = "/marketing/pipeline";
const COLUMN_ORDER: ShownStage[] = [
  "PROSPECT",
  "CONTACTED",
  "VISIT",
  "FOLLOW_UP",
  "INTERESTED",
  "NEGOTIATION",
  "FINAL_STAGE",
  "WON",
  "MOU",
  "ACTIVE",
  "ON_HOLD",
  "LOST",
];

type Dialog =
  { kind: "lost"; row: ProspectRow } | { kind: "reject"; row: ProspectRow };

/**
 * MARKETING -> Pipeline (spec 023 US2): the board of prospects by stage. MoU and Active come from the contract in spec
 * 012 and cannot be dragged to; a won prospect with no MoU for too long carries a flag. Review buttons show only to roles
 * that hold the approve action.
 */
export function PipelineBoard() {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const actions = useGrantedActions(ROUTE);
  const [board, setBoard] = useState<Board | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog | null>(null);

  const load = useCallback(async () => {
    const result = await getPipeline(authFetch, {});
    if (result.ok) {
      setError(null);
      setBoard(result.data);
    } else {
      setError(result.reason);
    }
  }, [authFetch]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  const move = async (row: ProspectRow, stage: StoredStage) => {
    const result = await moveStage(
      authFetch,
      row.id,
      stage,
      undefined,
      row.version,
    );
    if (!result.ok) setMessage(result.reason);
    else {
      setMessage(null);
      void load();
    }
  };

  const review = async (
    row: ProspectRow,
    decision: "APPROVE" | "REJECT",
    reason?: string,
  ) => {
    const result = await reviewProspect(authFetch, row.id, decision, reason);
    if (!result.ok) return result.reason;
    setMessage(null);
    void load();
    return null;
  };

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Typography variant="h5" component="h1">
          Pipeline
        </Typography>
        {error && (
          <Alert severity="error" role="alert">
            {error}
          </Alert>
        )}
        {message && (
          <Alert severity="error" role="alert" onClose={() => setMessage(null)}>
            {message}
          </Alert>
        )}
        {!board && !error && (
          <Typography role="status">Loading the pipeline...</Typography>
        )}
        {board && Object.values(board.counts).every((n) => n === 0) && (
          <Alert severity="info">
            No prospects yet. Add one under Prospects.
          </Alert>
        )}
        {board && (
          <Box sx={{ display: "flex", gap: 1.5, overflowX: "auto", pb: 1 }}>
            {COLUMN_ORDER.map((stage) => {
              const cards = board.columns[stage] ?? [];
              return (
                <Box
                  key={stage}
                  component="section"
                  aria-label={`${STAGE_LABEL[stage]}, ${cards.length}`}
                  sx={{ minWidth: 220, flex: "0 0 220px" }}
                >
                  <Typography variant="subtitle2" component="h2" sx={{ mb: 1 }}>
                    {STAGE_LABEL[stage]} ({board.counts[stage] ?? 0})
                  </Typography>
                  <Stack spacing={1}>
                    {cards.map((row) => (
                      <Card key={row.id} variant="outlined">
                        <CardContent
                          sx={{ p: 1.5, "&:last-child": { pb: 1.5 } }}
                        >
                          <Stack spacing={0.5}>
                            <Button
                              size="small"
                              sx={{
                                justifyContent: "flex-start",
                                textAlign: "left",
                              }}
                              onClick={() =>
                                navigate(`/marketing/prospects/${row.id}`)
                              }
                            >
                              {row.name}
                            </Button>
                            <Typography variant="caption">
                              {row.zoneName}; {row.owner.name}
                            </Typography>
                            <Stack
                              direction="row"
                              spacing={0.5}
                              sx={{ flexWrap: "wrap", gap: 0.5 }}
                            >
                              {row.followUpOverdue && (
                                <Chip
                                  size="small"
                                  color="warning"
                                  label="Follow-up overdue"
                                />
                              )}
                              {row.mouOverdue && (
                                <Chip
                                  size="small"
                                  color="error"
                                  label="MoU not recorded"
                                />
                              )}
                              {row.won &&
                                row.effectiveStage === "WON" &&
                                !row.mouOverdue && (
                                  <Chip
                                    size="small"
                                    label="School and MoU to be recorded"
                                  />
                                )}
                            </Stack>
                            {actions.has("EDIT") &&
                              !row.won &&
                              ACTIVE_STAGES.includes(row.stage) && (
                                <TextField
                                  select
                                  size="small"
                                  label={`Move ${row.name}`}
                                  value=""
                                  onChange={(e) =>
                                    void move(
                                      row,
                                      e.target.value as StoredStage,
                                    )
                                  }
                                >
                                  {ACTIVE_STAGES.filter(
                                    (s) => s !== row.stage,
                                  ).map((s) => (
                                    <MenuItem key={s} value={s}>
                                      {STAGE_LABEL[s]}
                                    </MenuItem>
                                  ))}
                                </TextField>
                              )}
                            {actions.has("EDIT") &&
                              !row.won &&
                              (row.stage === "ON_HOLD" ||
                                ACTIVE_STAGES.includes(row.stage)) && (
                                <Button
                                  size="small"
                                  color="error"
                                  onClick={() =>
                                    setDialog({ kind: "lost", row })
                                  }
                                  aria-label={`Mark ${row.name} lost`}
                                >
                                  Mark lost
                                </Button>
                              )}
                            {actions.has("APPROVE") &&
                              row.stage === "FINAL_STAGE" &&
                              !row.won && (
                                <Stack direction="row" spacing={0.5}>
                                  <Button
                                    size="small"
                                    variant="contained"
                                    onClick={() => void review(row, "APPROVE")}
                                    aria-label={`Approve ${row.name}`}
                                  >
                                    Approve
                                  </Button>
                                  <Button
                                    size="small"
                                    color="error"
                                    onClick={() =>
                                      setDialog({ kind: "reject", row })
                                    }
                                    aria-label={`Reject ${row.name}`}
                                  >
                                    Reject
                                  </Button>
                                </Stack>
                              )}
                          </Stack>
                        </CardContent>
                      </Card>
                    ))}
                  </Stack>
                </Box>
              );
            })}
          </Box>
        )}
      </Stack>
      {dialog?.kind === "lost" && (
        <ReasonDialog
          title={`Mark ${dialog.row.name} lost?`}
          label="Reason"
          confirmLabel="Mark lost"
          destructive
          onConfirm={async (reason) => {
            const result = await moveStage(
              authFetch,
              dialog.row.id,
              "LOST",
              reason,
            );
            if (!result.ok) return result.reason;
            setDialog(null);
            void load();
            return null;
          }}
          onCancel={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "reject" && (
        <ReasonDialog
          title={`Reject ${dialog.row.name}?`}
          message="The prospect goes back to Negotiation with your reason."
          label="Reason"
          confirmLabel="Reject"
          destructive
          onConfirm={async (reason) => {
            const failure = await review(dialog.row, "REJECT", reason);
            if (!failure) setDialog(null);
            return failure;
          }}
          onCancel={() => setDialog(null)}
        />
      )}
    </Paper>
  );
}
