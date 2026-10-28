import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
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
  KIND_LABELS,
  STATUS_LABELS,
  getCalendar,
  getFilters,
  type CalendarItem,
  type CalendarKind,
  type CalendarResponse,
  type CalendarStatus,
  type FiltersResponse,
} from "./activityCalendarApi";
import { ItemDetail } from "./ItemDetail";
import { ListView } from "./ListView";
import { MonthGrid } from "./MonthGrid";
import { NeedsAttention } from "./NeedsAttention";
import { SummaryBar } from "./SummaryBar";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; data: CalendarResponse };

const STORAGE_KEY = "activityCalendar.view.v1";

interface Remembered {
  month?: string;
  managerId?: string;
  zoneId?: string;
  kind?: string;
  status?: string;
  view?: "grid" | "list";
}

/** The month and filters survive a refresh; storage can be blocked, so every access is guarded. */
function remembered(): Remembered {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Remembered) : {};
  } catch {
    return {};
  }
}

function remember(value: Remembered) {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  } catch {
    // not remembered; the page still works
  }
}

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
 * OPERATIONS -> Activity Calendar (Admin, Director) or My Activity Calendar (Manager), spec 035: one month of drives,
 * visits, calls, meetings and tasks with status and counts, filters, a list view and a Needs attention tab. Read-only:
 * nothing here completes, moves or cancels.
 */
export function ActivityCalendarPage() {
  const { authFetch } = useAuth();
  const { accessModel } = useAccessModel();
  const management = Boolean(
    accessModel?.roles.some((r) => r === "ADMIN" || r === "DIRECTOR"),
  );
  const [saved] = useState(remembered);
  const [month, setMonth] = useState(saved.month ?? currentMonth());
  const [managerId, setManagerId] = useState(saved.managerId ?? "");
  const [zoneId, setZoneId] = useState(saved.zoneId ?? "");
  const [kind, setKind] = useState(saved.kind ?? "");
  const [status, setStatus] = useState(saved.status ?? "");
  const [view, setView] = useState<"grid" | "list">(saved.view ?? "grid");
  const [tab, setTab] = useState<"calendar" | "attention">("calendar");
  const [choices, setChoices] = useState<FiltersResponse | null>(null);
  const [state, setState] = useState<State>({ kind: "loading" });
  const [open, setOpen] = useState<CalendarItem | null>(null);

  useEffect(() => {
    remember({ month, managerId, zoneId, kind, status, view });
  }, [month, managerId, zoneId, kind, status, view]);

  useEffect(() => {
    let cancelled = false;
    void getFilters(authFetch).then((result) => {
      if (!cancelled && result.ok) setChoices(result.data);
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const result = await getCalendar(authFetch, {
      month,
      managerId: managerId || undefined,
      zoneId: zoneId || undefined,
      kind: kind || undefined,
      status: status || undefined,
    });
    setState(
      result.ok
        ? { kind: "ready", data: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, month, managerId, zoneId, kind, status]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- month and filter driven fetch
    void load();
  }, [load]);

  const filtered = Boolean(managerId || zoneId || kind || status);
  const trainingAvailable =
    choices?.kinds?.find((k) => k.kind === "TRAINING")?.available ?? false;

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
        <Button onClick={() => setMonth(shift(month, -1))}>
          Previous month
        </Button>
        <Typography component="h2" variant="h6" aria-live="polite">
          {monthTitle(month)}
        </Typography>
        <Button onClick={() => setMonth(shift(month, 1))}>Next month</Button>
        <Button onClick={() => setMonth(currentMonth())}>Today</Button>
      </Stack>

      <Stack
        direction="row"
        spacing={1}
        useFlexGap
        sx={{ mb: 2, flexWrap: "wrap", alignItems: "center" }}
        role="group"
        aria-label="Filters"
      >
        {management && (
          <TextField
            select
            size="small"
            label="Manager"
            value={managerId}
            onChange={(e) => setManagerId(e.target.value)}
            sx={{ minWidth: 160 }}
            slotProps={{
              select: { native: true },
              inputLabel: { shrink: true },
            }}
          >
            <option value="">All Managers</option>
            {(choices?.managers ?? []).map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </TextField>
        )}
        <TextField
          select
          size="small"
          label="Zone"
          value={zoneId}
          onChange={(e) => setZoneId(e.target.value)}
          sx={{ minWidth: 140 }}
          slotProps={{
            select: { native: true },
            inputLabel: { shrink: true },
          }}
        >
          <option value="">All Zones</option>
          {(choices?.zones ?? []).map((z) => (
            <option key={z.id} value={z.id}>
              {z.name}
            </option>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Kind"
          value={kind}
          onChange={(e) => setKind(e.target.value)}
          sx={{ minWidth: 160 }}
          slotProps={{
            select: { native: true },
            inputLabel: { shrink: true },
          }}
        >
          <option value="">All kinds</option>
          {(Object.keys(KIND_LABELS) as CalendarKind[]).map((k) => (
            <option
              key={k}
              value={k}
              disabled={k === "TRAINING" && !trainingAvailable}
            >
              {k === "TRAINING" && !trainingAvailable
                ? "Training: not available yet"
                : KIND_LABELS[k]}
            </option>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          sx={{ minWidth: 140 }}
          slotProps={{
            select: { native: true },
            inputLabel: { shrink: true },
          }}
        >
          <option value="">All statuses</option>
          {(Object.keys(STATUS_LABELS) as CalendarStatus[]).map((st) => (
            <option key={st} value={st}>
              {STATUS_LABELS[st]}
            </option>
          ))}
        </TextField>
        {filtered && (
          <Button
            onClick={() => {
              setManagerId("");
              setZoneId("");
              setKind("");
              setStatus("");
            }}
          >
            Clear filters
          </Button>
        )}
      </Stack>

      <Tabs
        value={tab}
        onChange={(_, value: "calendar" | "attention") => setTab(value)}
        sx={{ mb: 2 }}
      >
        <Tab value="calendar" label="Calendar" />
        <Tab value="attention" label="Needs attention" />
      </Tabs>

      {tab === "attention" && (
        <NeedsAttention
          month={month}
          managerId={managerId}
          zoneId={zoneId}
          kind={kind}
          onOpen={setOpen}
        />
      )}
      {tab === "calendar" && state.kind === "loading" && (
        <CircularProgress aria-label="Loading" />
      )}
      {tab === "calendar" && state.kind === "error" && (
        <Alert severity="error">{state.reason}</Alert>
      )}
      {tab === "calendar" && state.kind === "ready" && (
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
          <SummaryBar
            summary={state.data.summary}
            partial={state.data.partial}
          />
          <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
            <Button
              variant={view === "grid" ? "contained" : "outlined"}
              aria-pressed={view === "grid"}
              onClick={() => setView("grid")}
            >
              Month view
            </Button>
            <Button
              variant={view === "list" ? "contained" : "outlined"}
              aria-pressed={view === "list"}
              onClick={() => setView("list")}
            >
              List view
            </Button>
          </Stack>
          {state.data.items.length === 0 && (
            <Typography color="text.secondary" sx={{ mb: 2 }}>
              Nothing planned this month.
            </Typography>
          )}
          {view === "grid" ? (
            <MonthGrid
              month={state.data.month}
              items={state.data.items}
              management={management}
              onOpen={setOpen}
            />
          ) : (
            <ListView
              items={state.data.items}
              management={management}
              onOpen={setOpen}
            />
          )}
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
