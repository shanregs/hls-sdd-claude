import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Checkbox,
  FormControlLabel,
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
import {
  getDashboard,
  type Dashboard,
  type DashboardCounts,
} from "./recruitmentApi";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; data: Dashboard };

const COLUMNS: { key: keyof DashboardCounts; label: string }[] = [
  { key: "drivesScheduled", label: "Drives" },
  { key: "drivesHeld", label: "Held" },
  { key: "interviewed", label: "Interviewed" },
  { key: "assessed", label: "Assessed" },
  { key: "selected", label: "Selected" },
  { key: "offered", label: "Offered" },
  { key: "accepted", label: "Joined" },
  { key: "inducted", label: "Inducted" },
  { key: "readyToDeploy", label: "Ready" },
  { key: "placed", label: "Placed" },
  { key: "active", label: "Active" },
];

function ratio(value: string | null) {
  return value === null ? "-" : `${Math.round(Number(value) * 100)}%`;
}

/** RECRUITMENT -> Dashboard (spec 016 US5): the funnel per college and season, with the joining ratio. */
export function RecruitmentDashboard() {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [season, setSeason] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [mine, setMine] = useState(false);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await getDashboard(authFetch, {
      season: season || undefined,
      from: from || undefined,
      to: to || undefined,
      mine,
    });
    setState(
      result.ok
        ? { kind: "ready", data: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, season, from, to, mine]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Typography variant="h5" component="h1">
          Recruitment dashboard
        </Typography>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={2}
          sx={{ alignItems: { sm: "center" } }}
        >
          <TextField
            size="small"
            label="Season"
            value={season}
            onChange={(e) => setSeason(e.target.value)}
          />
          <TextField
            size="small"
            type="date"
            label="From"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            size="small"
            type="date"
            label="To"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <FormControlLabel
            control={
              <Checkbox
                checked={mine}
                onChange={(e) => setMine(e.target.checked)}
              />
            }
            label="My drives"
          />
        </Stack>
        {state.kind === "loading" && (
          <Typography role="status">Loading the dashboard...</Typography>
        )}
        {state.kind === "error" && (
          <Alert severity="error" role="alert">
            {state.reason}
          </Alert>
        )}
        {state.kind === "ready" && state.data.colleges.length === 0 && (
          <Alert severity="info">
            No drives match. Schedule a campus drive under Campus Drives, and
            the funnel fills as candidates are interviewed, offered and
            inducted.
          </Alert>
        )}
        {state.kind === "ready" && state.data.colleges.length > 0 && (
          <>
            <Typography>
              Joining ratio {ratio(state.data.totals.joiningRatio)} (joined
              divided by selected). Ready to deploy now:{" "}
              {state.data.readyToDeployTotal}.
            </Typography>
            <TableContainer>
              <Table size="small" aria-label="Recruitment funnel by college">
                <TableHead>
                  <TableRow>
                    <TableCell>College</TableCell>
                    {COLUMNS.map((c) => (
                      <TableCell key={c.key} align="right">
                        {c.label}
                      </TableCell>
                    ))}
                    <TableCell align="right">Joining ratio</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {state.data.colleges.map((row) => (
                    <TableRow key={row.collegeId}>
                      <TableCell>
                        {row.name}, {row.city}
                      </TableCell>
                      {COLUMNS.map((c) => (
                        <TableCell key={c.key} align="right">
                          {row.counts[c.key]}
                        </TableCell>
                      ))}
                      <TableCell align="right">
                        {ratio(row.counts.joiningRatio)}
                      </TableCell>
                    </TableRow>
                  ))}
                  <TableRow>
                    <TableCell>
                      <strong>All colleges</strong>
                    </TableCell>
                    {COLUMNS.map((c) => (
                      <TableCell key={c.key} align="right">
                        <strong>{state.data.totals[c.key]}</strong>
                      </TableCell>
                    ))}
                    <TableCell align="right">
                      <strong>{ratio(state.data.totals.joiningRatio)}</strong>
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
          </>
        )}
      </Stack>
    </Paper>
  );
}
