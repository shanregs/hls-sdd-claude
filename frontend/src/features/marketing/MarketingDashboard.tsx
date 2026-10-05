import { useCallback, useEffect, useState } from "react";
import {
  Alert,
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
  STAGE_LABEL,
  getDashboard,
  type MarketingDashboard as Data,
  type ShownStage,
} from "./marketingApi";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; data: Data };

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <Paper variant="outlined" sx={{ p: 1.5, minWidth: 150 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="h6" component="p">
        {value}
      </Typography>
    </Paper>
  );
}

/** MARKETING -> Dashboard (spec 023 US5): visits, prospects by stage, win rate, Schools won, and demand against supply. */
export function MarketingDashboard() {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [period, setPeriod] = useState("");

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await getDashboard(authFetch, period || undefined);
    setState(
      result.ok
        ? { kind: "ready", data: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, period]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and period-driven fetch
    void load();
  }, [load]);

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Typography variant="h5" component="h1">
          Marketing dashboard
        </Typography>
        <TextField
          type="month"
          size="small"
          label="Month"
          value={period}
          onChange={(e) => setPeriod(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
          sx={{ maxWidth: 200 }}
        />
        {state.kind === "loading" && (
          <Typography role="status">Loading the dashboard...</Typography>
        )}
        {state.kind === "error" && (
          <Alert severity="error" role="alert">
            {state.reason}
          </Alert>
        )}
        {state.kind === "ready" && (
          <>
            {Object.values(state.data.prospectsByStage).every((n) => n === 0) &&
              state.data.visits.completed === 0 && (
                <Alert severity="info">
                  No prospects or visits yet. Add a prospect to start the
                  pipeline.
                </Alert>
              )}
            <Stack direction="row" sx={{ flexWrap: "wrap", gap: 1.5 }}>
              <Figure
                label={`Visits completed (${state.data.period})`}
                value={String(state.data.visits.completed)}
              />
              <Figure
                label="Visits planned"
                value={String(state.data.visits.planned)}
              />
              <Figure
                label="Visits missed"
                value={String(state.data.visits.missed)}
              />
              <Figure
                label="Win rate"
                value={
                  state.data.winRate === null
                    ? "-"
                    : `${Math.round(Number(state.data.winRate) * 100)}%`
                }
              />
              <Figure label="Schools won" value={String(state.data.won)} />
              <Figure label="Prospects lost" value={String(state.data.lost)} />
            </Stack>
            <Typography variant="h6" component="h2">
              Demand and supply
            </Typography>
            <Stack direction="row" sx={{ flexWrap: "wrap", gap: 1.5 }}>
              <Figure
                label="Demand: vacant positions at won Schools"
                value={String(state.data.demand)}
              />
              <Figure
                label="Supply: recruits ready to deploy"
                value={
                  state.data.supply === null
                    ? "Not available"
                    : String(state.data.supply)
                }
              />
              <Figure
                label="Shortfall"
                value={
                  state.data.shortfall === null
                    ? "Not available"
                    : String(state.data.shortfall)
                }
              />
            </Stack>
            <TableContainer>
              <Table size="small" aria-label="Prospects by stage">
                <TableHead>
                  <TableRow>
                    <TableCell>Stage</TableCell>
                    <TableCell align="right">Prospects</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {Object.entries(state.data.prospectsByStage).map(
                    ([stage, count]) => (
                      <TableRow key={stage}>
                        <TableCell>
                          {STAGE_LABEL[stage as ShownStage] ?? stage}
                        </TableCell>
                        <TableCell align="right">{count}</TableCell>
                      </TableRow>
                    ),
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
              <TableContainer>
                <Table size="small" aria-label="Schools won per Zone">
                  <TableHead>
                    <TableRow>
                      <TableCell>Zone</TableCell>
                      <TableCell align="right">Schools won</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {state.data.wonPerZone.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={2}>None yet</TableCell>
                      </TableRow>
                    )}
                    {state.data.wonPerZone.map((z) => (
                      <TableRow key={z.name}>
                        <TableCell>{z.name}</TableCell>
                        <TableCell align="right">{z.won}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <TableContainer>
                <Table size="small" aria-label="Schools won per owner">
                  <TableHead>
                    <TableRow>
                      <TableCell>Owner</TableCell>
                      <TableCell align="right">Schools won</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {state.data.wonPerOwner.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={2}>None yet</TableCell>
                      </TableRow>
                    )}
                    {state.data.wonPerOwner.map((o) => (
                      <TableRow key={o.name}>
                        <TableCell>{o.name}</TableCell>
                        <TableCell align="right">{o.won}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Stack>
          </>
        )}
      </Stack>
    </Paper>
  );
}
