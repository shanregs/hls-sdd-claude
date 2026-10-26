import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useAccessModel } from "../../access-model/useAccessModel";
import {
  currentMonth,
  formatDate,
  formatTime,
  monthBounds,
  monthTitle,
  shiftMonth,
} from "./format";
import { PatternsPanel } from "./PatternsPanel";
import { SessionDetail } from "./SessionDetail";
import { SessionDialog } from "./SessionDialog";
import {
  STATUS_LABELS,
  TYPE_LABELS,
  getSessions,
  type SessionStatus,
  type SessionView,
} from "./trainingApi";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; sessions: SessionView[] };

const CHIP_COLOR: Record<
  SessionStatus,
  "default" | "primary" | "success" | "warning" | "error"
> = {
  DRAFT: "default",
  PUBLISHED: "primary",
  HELD: "success",
  CANCELLED: "error",
  SKIPPED: "warning",
};

/**
 * OPERATIONS -> Training Calendar (Admin, Director) and My Training (Manager), spec 024: a month of sessions as a grid or
 * a list, filters, holiday notices and status chips. Buttons on a session come from the server's actions list.
 */
export function TrainingCalendarPage() {
  const { authFetch } = useAuth();
  const { accessModel } = useAccessModel();
  const management = Boolean(
    accessModel?.roles.some((r) => r === "ADMIN" || r === "DIRECTOR"),
  );
  const [month, setMonth] = useState(currentMonth());
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [view, setView] = useState<"grid" | "list">("list");
  const [state, setState] = useState<State>({ kind: "loading" });
  const [openId, setOpenId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [tab, setTab] = useState<"sessions" | "patterns">("sessions");

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const { from, to } = monthBounds(month);
    const result = await getSessions(authFetch, {
      from,
      to,
      type: type || undefined,
      status: status || undefined,
    });
    setState(
      result.ok
        ? { kind: "ready", sessions: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, month, type, status]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- month and filter driven fetch
    void load();
  }, [load]);

  return (
    <Box>
      <Typography variant="h5" component="h1" sx={{ mb: 2 }}>
        {management ? "Training Calendar" : "My Training"}
      </Typography>
      {management && (
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
          <Tab value="sessions" label="Sessions" />
          <Tab value="patterns" label="Patterns" />
        </Tabs>
      )}
      {tab === "patterns" && management && (
        <PatternsPanel onChanged={() => void load()} />
      )}
      {(tab === "sessions" || !management) && (
        <>
          <Stack
            direction="row"
            spacing={1}
            useFlexGap
            sx={{ alignItems: "center", mb: 2, flexWrap: "wrap" }}
          >
            <Button onClick={() => setMonth(shiftMonth(month, -1))}>
              Previous month
            </Button>
            <Typography component="h2" variant="h6" aria-live="polite">
              {monthTitle(month)}
            </Typography>
            <Button onClick={() => setMonth(shiftMonth(month, 1))}>
              Next month
            </Button>
            <Button onClick={() => setMonth(currentMonth())}>Today</Button>
            {management && (
              <Button variant="contained" onClick={() => setCreating(true)}>
                New session
              </Button>
            )}
          </Stack>
          <Stack
            direction="row"
            spacing={1}
            useFlexGap
            sx={{ mb: 2, flexWrap: "wrap" }}
            role="group"
            aria-label="Filters"
          >
            <TextField
              select
              size="small"
              label="Type"
              value={type}
              onChange={(e) => setType(e.target.value)}
              slotProps={{ select: { native: true } }}
              sx={{ minWidth: 170 }}
            >
              <option value="">All types</option>
              <option value="WEEKEND">Weekend refresher</option>
              <option value="MONTHLY">Monthly training</option>
            </TextField>
            <TextField
              select
              size="small"
              label="Status"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              slotProps={{ select: { native: true } }}
              sx={{ minWidth: 150 }}
            >
              <option value="">All statuses</option>
              {(Object.keys(STATUS_LABELS) as SessionStatus[]).map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABELS[s]}
                </option>
              ))}
            </TextField>
            <Button onClick={() => setView(view === "grid" ? "list" : "grid")}>
              {view === "grid" ? "Show list" : "Show month grid"}
            </Button>
          </Stack>

          {state.kind === "loading" && (
            <CircularProgress aria-label="Loading" />
          )}
          {state.kind === "error" && (
            <Alert
              severity="error"
              action={<Button onClick={() => void load()}>Try again</Button>}
            >
              {state.reason}
            </Alert>
          )}
          {state.kind === "ready" && state.sessions.length === 0 && (
            <Typography>No training sessions this month.</Typography>
          )}
          {state.kind === "ready" &&
            state.sessions.length > 0 &&
            (view === "list" ? (
              <Stack
                component="ul"
                spacing={1}
                sx={{ listStyle: "none", p: 0, m: 0 }}
              >
                {state.sessions.map((s) => (
                  <li key={s.id}>
                    <Button
                      fullWidth
                      onClick={() => setOpenId(s.id)}
                      sx={{
                        justifyContent: "flex-start",
                        textTransform: "none",
                        textAlign: "left",
                      }}
                      aria-label={`${s.topic}, ${formatDate(s.date)}`}
                    >
                      <Stack
                        direction="row"
                        spacing={1}
                        useFlexGap
                        sx={{ alignItems: "center", flexWrap: "wrap" }}
                      >
                        <span>{formatDate(s.date)}</span>
                        <span>{formatTime(s.startsAt)}</span>
                        <strong>{s.topic}</strong>
                        <span>{TYPE_LABELS[s.type]}</span>
                        <Chip
                          size="small"
                          color={CHIP_COLOR[s.status]}
                          label={STATUS_LABELS[s.status]}
                        />
                        {s.holidayNotice && (
                          <Chip
                            size="small"
                            variant="outlined"
                            label="Holiday"
                          />
                        )}
                      </Stack>
                    </Button>
                  </li>
                ))}
              </Stack>
            ) : (
              <MonthGrid
                month={month}
                sessions={state.sessions}
                onOpen={setOpenId}
              />
            ))}
        </>
      )}

      {openId && (
        <SessionDetail
          sessionId={openId}
          onChanged={() => void load()}
          onClose={() => setOpenId(null)}
        />
      )}
      {creating && (
        <SessionDialog
          onCancel={() => setCreating(false)}
          onSaved={(s) => {
            setCreating(false);
            void load();
            setOpenId(s.id);
          }}
        />
      )}
    </Box>
  );
}

function MonthGrid({
  month,
  sessions,
  onOpen,
}: {
  month: string;
  sessions: SessionView[];
  onOpen: (id: string) => void;
}) {
  const [year, mon] = month.split("-").map(Number);
  const days = new Date(year, mon, 0).getDate();
  const offset = (new Date(year, mon - 1, 1).getDay() + 6) % 7;
  const cells: (number | null)[] = [
    ...Array<null>(offset).fill(null),
    ...Array.from({ length: days }, (_, i) => i + 1),
  ];
  const on = (day: number) =>
    sessions.filter(
      (s) => s.date === `${month}-${String(day).padStart(2, "0")}`,
    );
  return (
    <Box
      role="grid"
      aria-label={`Sessions in ${monthTitle(month)}`}
      sx={{
        display: "grid",
        gridTemplateColumns: "repeat(7, minmax(0, 1fr))",
        gap: 0.5,
      }}
    >
      {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((d) => (
        <Typography
          key={d}
          variant="caption"
          role="columnheader"
          sx={{ fontWeight: 600 }}
        >
          {d}
        </Typography>
      ))}
      {cells.map((day, i) => (
        <Box
          key={i}
          role="gridcell"
          sx={{ minHeight: 64, border: 1, borderColor: "divider", p: 0.5 }}
        >
          {day !== null && (
            <>
              <Typography variant="caption">{day}</Typography>
              {on(day).map((s) => (
                <Chip
                  key={s.id}
                  size="small"
                  color={CHIP_COLOR[s.status]}
                  label={s.topic}
                  onClick={() => onOpen(s.id)}
                  sx={{ display: "flex", maxWidth: "100%", mt: 0.25 }}
                />
              ))}
            </>
          )}
        </Box>
      ))}
    </Box>
  );
}
