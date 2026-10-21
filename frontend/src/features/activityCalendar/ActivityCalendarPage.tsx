import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useAccessModel } from "../../access-model/useAccessModel";
import {
  getCalendar,
  type CalendarItem,
  type CalendarResponse,
} from "./activityCalendarApi";
import { ItemDetail } from "./ItemDetail";
import { MonthGrid } from "./MonthGrid";
import { SummaryBar } from "./SummaryBar";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; data: CalendarResponse };

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function shift(month: string, by: number): string {
  const [year, mon] = month.split("-").map(Number);
  const d = new Date(year, mon - 1 + by, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

function monthTitle(month: string): string {
  const [year, mon] = month.split("-").map(Number);
  return new Date(year, mon - 1, 1).toLocaleString("en-IN", {
    month: "long",
    year: "numeric",
  });
}

/**
 * OPERATIONS -> Activity Calendar (Admin, Director) or My Activity Calendar (Manager), spec 035 US1: one month of drives,
 * visits, calls, meetings and tasks with status and counts. Read-only: nothing here completes, moves or cancels.
 */
export function ActivityCalendarPage() {
  const { authFetch } = useAuth();
  const { accessModel } = useAccessModel();
  const management = Boolean(
    accessModel?.roles.some((r) => r === "ADMIN" || r === "DIRECTOR"),
  );
  const [month, setMonth] = useState(currentMonth);
  const [state, setState] = useState<State>({ kind: "loading" });
  const [open, setOpen] = useState<CalendarItem | null>(null);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await getCalendar(authFetch, { month });
    setState(
      result.ok
        ? { kind: "ready", data: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, month]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- month-driven fetch
    void load();
  }, [load]);

  return (
    <Box>
      <Typography variant="h5" component="h1" sx={{ mb: 2 }}>
        {management ? "Activity Calendar" : "My Activity Calendar"}
      </Typography>
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: "center", mb: 2, flexWrap: "wrap" }}
        useFlexGap
      >
        <Button onClick={() => setMonth(shift(month, -1))}>Previous month</Button>
        <Typography component="h2" variant="h6" aria-live="polite">
          {monthTitle(month)}
        </Typography>
        <Button onClick={() => setMonth(shift(month, 1))}>Next month</Button>
        <Button onClick={() => setMonth(currentMonth())}>Today</Button>
      </Stack>

      {state.kind === "loading" && <CircularProgress aria-label="Loading" />}
      {state.kind === "error" && <Alert severity="error">{state.reason}</Alert>}
      {state.kind === "ready" && (
        <>
          {state.data.sources
            .filter((s) => s.note)
            .map((s) => (
              <Alert
                key={s.group}
                severity={s.available ? "warning" : "info"}
                sx={{ mb: 1 }}
              >
                {s.note}
              </Alert>
            ))}
          <SummaryBar summary={state.data.summary} partial={state.data.partial} />
          {state.data.items.length === 0 && (
            <Typography color="text.secondary" sx={{ mb: 2 }}>
              Nothing planned this month.
            </Typography>
          )}
          <MonthGrid
            month={state.data.month}
            items={state.data.items}
            management={management}
            onOpen={setOpen}
          />
        </>
      )}
      {open && (
        <ItemDetail
          item={open}
          management={management}
          onClose={() => setOpen(null)}
        />
      )}
    </Box>
  );
}
