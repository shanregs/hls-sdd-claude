import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Link,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "../teachers/formatters";
import { ResultChip } from "./Chips";
import {
  PERFORMANCE_ROUTE,
  cancelPlan,
  listPlans,
  type PlanListView,
  type PlanView,
} from "./performanceApi";
import { PerformanceNav } from "./PerformanceNav";
import { PlanDialog } from "./PlanDialog";
import { ReasonDialog } from "./ReasonDialog";

type Rows =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: PlanView[] };

/**
 * OPERATIONS -> Teacher Performance -> Planned, spec 026 US2: each Teacher's open Observation task with its date, the reason,
 * the days overdue and the Teacher's last result. Observations are planned when needed; there is no fixed interval.
 */
export function PlannedPage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(PERFORMANCE_ROUTE);
  const [view, setView] = useState<PlanListView>("planned");
  const [rows, setRows] = useState<Rows>({ kind: "loading" });
  const [planning, setPlanning] = useState(false);
  const [editing, setEditing] = useState<PlanView | null>(null);
  const [cancelling, setCancelling] = useState<PlanView | null>(null);

  const load = useCallback(async () => {
    setRows({ kind: "loading" });
    const list = await listPlans(authFetch, view);
    setRows(
      list.ok
        ? { kind: "ready", rows: list.data }
        : { kind: "error", reason: list.reason },
    );
  }, [authFetch, view]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and tab-driven fetch
    void load();
  }, [load]);

  return (
    <Box>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{
          alignItems: { sm: "center" },
          justifyContent: "space-between",
          mb: 2,
        }}
      >
        <Typography variant="h5" component="h1">
          Teacher Performance
        </Typography>
        {granted.has("CREATE") && (
          <Button variant="contained" onClick={() => setPlanning(true)}>
            Plan an observation
          </Button>
        )}
      </Stack>
      <PerformanceNav />

      <Tabs
        value={view}
        onChange={(_, v: PlanListView) => setView(v)}
        aria-label="Planned observation lists"
        sx={{ mb: 2 }}
      >
        <Tab value="planned" label="Planned" />
        <Tab value="overdue" label="Overdue" />
      </Tabs>

      {rows.kind === "loading" && (
        <CircularProgress aria-label="Loading planned observations" />
      )}
      {rows.kind === "error" && <Alert severity="error">{rows.reason}</Alert>}
      {rows.kind === "ready" && rows.rows.length === 0 && (
        <Typography color="text.secondary">
          No planned observations here.
        </Typography>
      )}
      {rows.kind === "ready" && rows.rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label="Planned observations">
            <TableHead>
              <TableRow>
                <TableCell>Planned for</TableCell>
                <TableCell>Teacher</TableCell>
                <TableCell>School</TableCell>
                <TableCell>Manager</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell>Last result</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.rows.map((plan) => (
                <TableRow key={plan.id}>
                  <TableCell>
                    {formatDate(plan.plannedOn)}
                    {plan.overdue && (
                      <Chip
                        size="small"
                        color="error"
                        label={`${plan.daysOverdue} ${plan.daysOverdue === 1 ? "day" : "days"} overdue`}
                        sx={{ ml: 1 }}
                      />
                    )}
                  </TableCell>
                  <TableCell>
                    <Link
                      component={RouterLink}
                      to={`${PERFORMANCE_ROUTE}/teachers/${plan.teacherId}`}
                    >
                      {plan.teacherName}
                    </Link>
                  </TableCell>
                  <TableCell>{plan.schoolName}</TableCell>
                  <TableCell>{plan.managerName}</TableCell>
                  <TableCell>
                    {plan.reasonLabel}
                    {plan.reasonText ? `: ${plan.reasonText}` : ""}
                  </TableCell>
                  <TableCell>
                    <ResultChip result={plan.lastResult} />
                  </TableCell>
                  <TableCell>
                    {plan.actions.edit && granted.has("EDIT") && (
                      <Button size="small" onClick={() => setEditing(plan)}>
                        Change
                      </Button>
                    )}
                    {plan.actions.cancel && granted.has("EDIT") && (
                      <Button
                        size="small"
                        color="error"
                        onClick={() => setCancelling(plan)}
                      >
                        Cancel plan
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {(planning || editing) && (
        <PlanDialog
          plan={editing ?? undefined}
          onClose={() => {
            setPlanning(false);
            setEditing(null);
          }}
          onSaved={() => {
            setPlanning(false);
            setEditing(null);
            void load();
          }}
        />
      )}
      {cancelling && (
        <ReasonDialog
          title="Cancel planned observation"
          label="Why is it cancelled?"
          min={3}
          button="Cancel plan"
          destructive
          onClose={() => setCancelling(null)}
          onSubmit={async (reason) => {
            const result = await cancelPlan(authFetch, cancelling.id, reason);
            if (!result.ok) return result.reason;
            setCancelling(null);
            void load();
            return null;
          }}
        />
      )}
    </Box>
  );
}
