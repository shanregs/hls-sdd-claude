import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Button,
  Chip,
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
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { ProspectDialog } from "./ProspectDialog";
import {
  STAGE_LABEL,
  listProspects,
  type ProspectRow,
  type ShownStage,
} from "./marketingApi";

const ROUTE = "/marketing/prospects";
const PAGE_SIZE = 25;
const STAGES = Object.keys(STAGE_LABEL) as ShownStage[];

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: ProspectRow[]; total: number };

/** MARKETING -> Prospects (spec 023 US1): the Schools HLS is trying to win, within the caller's Zones. */
export function ProspectsPage() {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const actions = useGrantedActions(ROUTE);
  const [state, setState] = useState<State>({ kind: "loading" });
  const [query, setQuery] = useState("");
  const [stage, setStage] = useState("");
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await listProspects(authFetch, {
      query: query || undefined,
      stage: stage || undefined,
      page,
      size: PAGE_SIZE,
    });
    setState(
      result.ok
        ? {
            kind: "ready",
            rows: result.data.content,
            total: result.data.totalElements,
          }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, query, stage, page]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

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
            Prospects
          </Typography>
          {actions.has("CREATE") && (
            <Button variant="contained" onClick={() => setAdding(true)}>
              Add prospect
            </Button>
          )}
        </Stack>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
          <TextField
            size="small"
            label="Search by name"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setPage(0);
            }}
          />
          <TextField
            select
            size="small"
            label="Stage"
            value={stage}
            onChange={(e) => {
              setStage(e.target.value);
              setPage(0);
            }}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value="">All</MenuItem>
            {STAGES.map((s) => (
              <MenuItem key={s} value={s}>
                {STAGE_LABEL[s]}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
        {state.kind === "loading" && (
          <Typography role="status">Loading prospects...</Typography>
        )}
        {state.kind === "error" && (
          <Alert severity="error" role="alert">
            {state.reason}
          </Alert>
        )}
        {state.kind === "ready" && state.rows.length === 0 && (
          <Alert severity="info">
            No prospects match. Add the first School you are approaching.
          </Alert>
        )}
        {state.kind === "ready" && state.rows.length > 0 && (
          <>
            <TableContainer>
              <Table size="small" aria-label="Prospects">
                <TableHead>
                  <TableRow>
                    <TableCell>School</TableCell>
                    <TableCell>Zone</TableCell>
                    <TableCell>Owner</TableCell>
                    <TableCell>Stage</TableCell>
                    <TableCell>Expected Teachers</TableCell>
                    <TableCell>
                      <span style={{ position: "absolute", left: -9999 }}>
                        Actions
                      </span>
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {state.rows.map((r) => (
                    <TableRow key={r.id} hover>
                      <TableCell>
                        {r.name}
                        {r.board ? ` (${r.board})` : ""}
                      </TableCell>
                      <TableCell>{r.zoneName}</TableCell>
                      <TableCell>{r.owner.name}</TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={STAGE_LABEL[r.effectiveStage]}
                        />
                        {r.followUpOverdue && (
                          <Chip
                            size="small"
                            color="warning"
                            label="Follow-up overdue"
                            sx={{ ml: 0.5 }}
                          />
                        )}
                        {r.mouOverdue && (
                          <Chip
                            size="small"
                            color="error"
                            label="MoU not recorded"
                            sx={{ ml: 0.5 }}
                          />
                        )}
                      </TableCell>
                      <TableCell>{r.expectedTeachers ?? "-"}</TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          onClick={() =>
                            navigate(`/marketing/prospects/${r.id}`)
                          }
                          aria-label={`Open ${r.name}`}
                        >
                          Open
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            {state.total > PAGE_SIZE && (
              <Pagination
                count={Math.ceil(state.total / PAGE_SIZE)}
                page={page + 1}
                onChange={(_e, value) => setPage(value - 1)}
              />
            )}
          </>
        )}
      </Stack>
      {adding && (
        <ProspectDialog
          onClose={() => setAdding(false)}
          onSaved={(id) => {
            setAdding(false);
            navigate(`/marketing/prospects/${id}`);
          }}
        />
      )}
    </Paper>
  );
}
