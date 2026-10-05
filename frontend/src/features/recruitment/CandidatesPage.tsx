import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Button,
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
import { listCandidates, type Candidate } from "./recruitmentApi";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: Candidate[] };

/** RECRUITMENT -> Candidates (spec 016 US1): every candidate across drives, with an outcome filter and search. */
export function CandidatesPage() {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [outcome, setOutcome] = useState("");
  const [query, setQuery] = useState("");

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await listCandidates(authFetch, {
      outcome: outcome || undefined,
      query: query || undefined,
    });
    setState(
      result.ok
        ? { kind: "ready", rows: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, outcome, query]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Typography variant="h5" component="h1">
          Candidates
        </Typography>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
          <TextField
            size="small"
            label="Search name or phone"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <TextField
            select
            size="small"
            label="Outcome"
            value={outcome}
            onChange={(e) => setOutcome(e.target.value)}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value="">All</MenuItem>
            <MenuItem value="SELECTED">Selected</MenuItem>
            <MenuItem value="WAITLISTED">Waitlisted</MenuItem>
            <MenuItem value="REJECTED">Rejected</MenuItem>
          </TextField>
        </Stack>
        {state.kind === "loading" && (
          <Typography role="status">Loading candidates...</Typography>
        )}
        {state.kind === "error" && (
          <Alert severity="error" role="alert">
            {state.reason}
          </Alert>
        )}
        {state.kind === "ready" && state.rows.length === 0 && (
          <Alert severity="info">No candidates match.</Alert>
        )}
        {state.kind === "ready" && state.rows.length > 0 && (
          <TableContainer>
            <Table size="small" aria-label="Candidates">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Phone</TableCell>
                  <TableCell>Outcome</TableCell>
                  <TableCell>
                    <span style={{ position: "absolute", left: -9999 }}>
                      Actions
                    </span>
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {state.rows.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell>{c.name}</TableCell>
                    <TableCell>{c.phone}</TableCell>
                    <TableCell>
                      {c.teacherId
                        ? "Joined"
                        : c.outcome
                          ? c.outcome.charAt(0) +
                            c.outcome.slice(1).toLowerCase()
                          : "Not decided"}
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        onClick={() =>
                          navigate(`/recruitment/drives/${c.driveId}`)
                        }
                        aria-label={`Open the drive of ${c.name}`}
                      >
                        Open drive
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Stack>
    </Paper>
  );
}
