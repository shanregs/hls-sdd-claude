import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Link,
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
  Tooltip,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { shiftMonth } from "../attendance/monthUtils";
import { useGrantedActions } from "../common/useGrantedActions";
import { todayIso } from "../teachers/formatters";
import { listZones, type ZoneSummary } from "../zones/zonesApi";
import { LockMonthDialog } from "./LockMonthDialog";
import { ManagerDayDialog } from "./ManagerDayDialog";
import { getGrid, type Grid, type GridRow } from "./managerAttendanceApi";
import { figure, statusStyle } from "./statusDisplay";

const ROUTE = "/operations/manager-attendance";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; grid: Grid };

function monthLabel(month: string): string {
  const [y, m] = month.split("-").map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString("en-IN", {
    month: "long",
    year: "numeric",
  });
}

/** Every Manager's month for an Admin or Director (spec 032 US5), with a Zone filter, a name search and a day dialog. */
export function ManagerAttendanceGridPage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(ROUTE);
  const canEdit = granted.has("EDIT");
  const canProcess = granted.has("PROCESS");

  const [month, setMonth] = useState(todayIso().slice(0, 7));
  const [zoneId, setZoneId] = useState("");
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [zones, setZones] = useState<ZoneSummary[]>([]);
  const [state, setState] = useState<State>({ kind: "loading" });
  const [open, setOpen] = useState<{ row: GridRow; date: string } | null>(null);
  const [locking, setLocking] = useState(false);

  useEffect(() => {
    void (async () => {
      const result = await listZones(authFetch, "", 0, 200);
      if (result.ok) setZones(result.data.content);
    })();
  }, [authFetch]);

  // The search is sent a moment after the last keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setSearch(query.trim()), 300);
    return () => clearTimeout(timer);
  }, [query]);

  const load = useCallback(async () => {
    const result = await getGrid(authFetch, {
      month,
      zoneId: zoneId || undefined,
      q: search || undefined,
    });
    setState(
      result.ok
        ? { kind: "ready", grid: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, month, zoneId, search]);

  useEffect(() => {
    void (async () => {
      await load();
    })();
  }, [load]);

  const days =
    state.kind === "ready"
      ? Array.from({ length: state.grid.daysInMonth }, (_, i) => i + 1)
      : [];
  const dateOf = (day: number) => `${month}-${String(day).padStart(2, "0")}`;

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        Manager attendance
      </Typography>

      <Stack
        direction="row"
        sx={{ mb: 2, gap: 2, alignItems: "center", flexWrap: "wrap" }}
      >
        <Button
          onClick={() => setMonth((m) => shiftMonth(m, -1))}
          aria-label="Previous month"
        >
          Previous
        </Button>
        <Typography component="h2" variant="h6" aria-live="polite">
          {monthLabel(month)}
        </Typography>
        <Button
          onClick={() => setMonth((m) => shiftMonth(m, 1))}
          aria-label="Next month"
        >
          Next
        </Button>
        {canProcess && month < todayIso().slice(0, 7) && (
          <Button variant="outlined" onClick={() => setLocking(true)}>
            Lock month for payroll
          </Button>
        )}
        <TextField
          select
          size="small"
          label="Zone"
          value={zoneId}
          onChange={(e) => setZoneId(e.target.value)}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">All zones</MenuItem>
          {zones.map((z) => (
            <MenuItem key={z.id} value={z.id}>
              {z.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          size="small"
          label="Search by name or employee ID"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </Stack>

      {state.kind === "loading" && (
        <Box sx={{ p: 3, textAlign: "center" }}>
          <CircularProgress aria-label="Loading the grid" />
        </Box>
      )}
      {state.kind === "error" && (
        <Alert severity="error" role="alert">
          {state.reason}
        </Alert>
      )}

      {state.kind === "ready" && (
        <>
          <Legend />
          <TableContainer component={Paper} variant="outlined">
            <Table size="small" aria-label="Manager attendance by day">
              <TableHead>
                <TableRow>
                  <TableCell>Manager</TableCell>
                  {days.map((d) => (
                    <TableCell key={d} align="center" sx={{ px: 0.5 }}>
                      {d}
                    </TableCell>
                  ))}
                  <TableCell align="right">Worked</TableCell>
                  <TableCell align="right">Unpaid</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {state.grid.managers.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={days.length + 3}>
                      No Managers match.
                    </TableCell>
                  </TableRow>
                )}
                {state.grid.managers.map((row) => (
                  <TableRow key={row.managerId} hover>
                    <TableCell component="th" scope="row">
                      <Link
                        component="button"
                        type="button"
                        onClick={() => setOpen({ row, date: "" })}
                      >
                        {row.name}
                      </Link>
                      {row.locked && (
                        <Typography variant="caption" sx={{ ml: 1 }}>
                          Locked
                        </Typography>
                      )}
                    </TableCell>
                    {days.map((d) => {
                      const style = statusStyle(row.statuses[d - 1] ?? "");
                      return (
                        <TableCell key={d} align="center" sx={{ px: 0.25 }}>
                          <Tooltip title={`${dateOf(d)}: ${style.label}`}>
                            <Button
                              size="small"
                              color={
                                style.color === "default"
                                  ? "inherit"
                                  : style.color
                              }
                              variant={
                                style.color === "default" ? "text" : "outlined"
                              }
                              sx={{ minWidth: 28, px: 0.5 }}
                              aria-label={`${row.name}, ${dateOf(d)}: ${style.label}`}
                              onClick={() => setOpen({ row, date: dateOf(d) })}
                            >
                              {style.code}
                            </Button>
                          </Tooltip>
                        </TableCell>
                      );
                    })}
                    <TableCell align="right">
                      {row.figures ? figure(row.figures.daysWorked) : "-"}
                    </TableCell>
                    <TableCell align="right">
                      {row.figures ? figure(row.figures.unpaid) : "-"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      {locking && (
        <LockMonthDialog
          month={month}
          onClose={() => setLocking(false)}
          onChanged={() => void load()}
        />
      )}

      {open && (
        <ManagerDayDialog
          managerId={open.row.managerId}
          managerName={open.row.name}
          month={month}
          initialDate={open.date}
          canEdit={canEdit}
          canProcess={canProcess}
          onClose={() => setOpen(null)}
          onChanged={() => void load()}
        />
      )}
    </Box>
  );
}

function Legend() {
  const items = [
    "WORKED",
    "FIELD",
    "LEAVE",
    "ABSENT",
    "MISSED",
    "NO_CHECK_OUT",
    "PENDING",
    "HOLIDAY",
    "WEEKLY_OFF",
  ];
  return (
    <Stack
      direction="row"
      sx={{ mb: 1, gap: 2, flexWrap: "wrap", p: 0, listStyle: "none" }}
      component="ul"
      aria-label="Status codes"
    >
      {items.map((s) => {
        const style = statusStyle(s);
        return (
          <Typography
            key={s}
            variant="caption"
            component="li"
            sx={{ listStyle: "none" }}
          >
            <strong>{style.code}</strong> {style.label}
          </Typography>
        );
      })}
    </Stack>
  );
}
