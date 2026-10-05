import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
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
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "../teachers/formatters";
import { CollegeDialog } from "./CollegeDialog";
import { DriveDialog } from "./DriveDialog";
import {
  listColleges,
  listDrives,
  type College,
  type Drive,
} from "./recruitmentApi";

const ROUTE = "/recruitment/drives";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; drives: Drive[]; colleges: College[] };

const STATUS_LABEL: Record<Drive["status"], string> = {
  PLANNED: "Planned",
  HELD: "Held",
  CANCELLED: "Cancelled",
};

function monthLabel(year: number, month: number) {
  return new Date(year, month, 1).toLocaleString("en-IN", {
    month: "long",
    year: "numeric",
  });
}

/** A month grid with the drives on their dates; the list below carries the same information for screen readers. */
function MonthCalendar({
  drives,
  onOpen,
}: {
  drives: Drive[];
  onOpen: (id: string) => void;
}) {
  const today = new Date();
  const [cursor, setCursor] = useState({
    year: today.getFullYear(),
    month: today.getMonth(),
  });
  const first = new Date(cursor.year, cursor.month, 1);
  const lead = (first.getDay() + 6) % 7; // Monday first
  const days = new Date(cursor.year, cursor.month + 1, 0).getDate();
  const byDate = useMemo(() => {
    const map = new Map<string, Drive[]>();
    for (const d of drives) {
      for (const date of d.dates) {
        map.set(date, [...(map.get(date) ?? []), d]);
      }
    }
    return map;
  }, [drives]);
  const step = (delta: number) =>
    setCursor((c) => {
      const next = new Date(c.year, c.month + delta, 1);
      return { year: next.getFullYear(), month: next.getMonth() };
    });
  const cells: (number | null)[] = [
    ...Array<null>(lead).fill(null),
    ...Array.from({ length: days }, (_x, i) => i + 1),
  ];
  return (
    <Box aria-hidden="true">
      <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: "center" }}>
        <Button
          size="small"
          onClick={() => step(-1)}
          aria-label="Previous month"
          tabIndex={-1}
        >
          Previous
        </Button>
        <Typography variant="subtitle1">
          {monthLabel(cursor.year, cursor.month)}
        </Typography>
        <Button
          size="small"
          onClick={() => step(1)}
          aria-label="Next month"
          tabIndex={-1}
        >
          Next
        </Button>
      </Stack>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(7, 1fr)",
          gap: 0.5,
        }}
      >
        {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((d) => (
          <Typography key={d} variant="caption" color="text.secondary">
            {d}
          </Typography>
        ))}
        {cells.map((day, i) => {
          const iso = day
            ? `${cursor.year}-${String(cursor.month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`
            : "";
          const here = day ? (byDate.get(iso) ?? []) : [];
          return (
            <Box
              key={i}
              sx={{
                minHeight: 56,
                border: 1,
                borderColor: "divider",
                borderRadius: 1,
                p: 0.5,
                overflow: "hidden",
              }}
            >
              {day && <Typography variant="caption">{day}</Typography>}
              {here.map((d) => (
                <Chip
                  key={d.id}
                  size="small"
                  label={d.college.name}
                  onClick={() => onOpen(d.id)}
                  sx={{ display: "flex", maxWidth: "100%" }}
                  tabIndex={-1}
                />
              ))}
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}

/** RECRUITMENT -> Campus Drives (spec 016 US1): calendar and list of drives, scheduling, and the college list. */
export function DrivesPage() {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const actions = useGrantedActions(ROUTE);
  const [state, setState] = useState<State>({ kind: "loading" });
  const [season, setSeason] = useState("");
  const [mine, setMine] = useState(false);
  const [scheduling, setScheduling] = useState(false);
  const [collegeDialog, setCollegeDialog] = useState<{
    college?: College;
  } | null>(null);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const [drives, colleges] = await Promise.all([
      listDrives(authFetch, { season: season || undefined, mine }),
      listColleges(authFetch),
    ]);
    if (!drives.ok) {
      setState({ kind: "error", reason: drives.reason });
    } else if (!colleges.ok) {
      setState({ kind: "error", reason: colleges.reason });
    } else {
      setState({ kind: "ready", drives: drives.data, colleges: colleges.data });
    }
  }, [authFetch, season, mine]);

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
            alignItems: "center",
            justifyContent: "space-between",
            flexWrap: "wrap",
            gap: 1,
          }}
        >
          <Typography variant="h5" component="h1">
            Campus Drives
          </Typography>
          <Stack direction="row" spacing={1}>
            {actions.has("CREATE") && (
              <Button onClick={() => setCollegeDialog({})}>Add college</Button>
            )}
            {actions.has("CREATE") && (
              <Button variant="contained" onClick={() => setScheduling(true)}>
                Schedule drive
              </Button>
            )}
          </Stack>
        </Stack>
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
            sx={{ maxWidth: 200 }}
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
          <Typography role="status">Loading drives...</Typography>
        )}
        {state.kind === "error" && (
          <Alert severity="error" role="alert">
            {state.reason}
          </Alert>
        )}
        {state.kind === "ready" && (
          <>
            <MonthCalendar
              drives={state.drives}
              onOpen={(id) => navigate(`/recruitment/drives/${id}`)}
            />
            {state.drives.length === 0 ? (
              <Alert severity="info">
                No drives yet. Add a college, then schedule the first campus
                drive.
              </Alert>
            ) : (
              <TableContainer>
                <Table size="small" aria-label="Campus drives">
                  <TableHead>
                    <TableRow>
                      <TableCell>College</TableCell>
                      <TableCell>Dates</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Candidates</TableCell>
                      <TableCell>
                        <span style={{ position: "absolute", left: -9999 }}>
                          Actions
                        </span>
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {state.drives.map((d) => (
                      <TableRow key={d.id} hover>
                        <TableCell>
                          {d.college.name}, {d.college.city}
                          {d.season ? ` (${d.season})` : ""}
                        </TableCell>
                        <TableCell>
                          {d.dates.map(formatDate).join(", ")}
                        </TableCell>
                        <TableCell>
                          {d.heldNoCandidates
                            ? "Held, no candidates"
                            : STATUS_LABEL[d.status]}
                        </TableCell>
                        <TableCell>{d.candidates}</TableCell>
                        <TableCell align="right">
                          <Button
                            size="small"
                            onClick={() =>
                              navigate(`/recruitment/drives/${d.id}`)
                            }
                            aria-label={`Open drive at ${d.college.name}`}
                          >
                            Open
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
            <Typography variant="h6" component="h2">
              Colleges
            </Typography>
            {state.colleges.length === 0 ? (
              <Typography color="text.secondary">No colleges yet.</Typography>
            ) : (
              <TableContainer>
                <Table size="small" aria-label="Colleges">
                  <TableHead>
                    <TableRow>
                      <TableCell>College</TableCell>
                      <TableCell>Placement officer</TableCell>
                      <TableCell>Principal</TableCell>
                      <TableCell>Drives</TableCell>
                      <TableCell>
                        <span style={{ position: "absolute", left: -9999 }}>
                          Actions
                        </span>
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {state.colleges.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell>
                          {c.name}, {c.city}
                        </TableCell>
                        <TableCell>{c.placementOfficer?.name ?? "-"}</TableCell>
                        <TableCell>{c.principal?.name ?? "-"}</TableCell>
                        <TableCell>{c.drives}</TableCell>
                        <TableCell align="right">
                          {actions.has("EDIT") && (
                            <Button
                              size="small"
                              onClick={() => setCollegeDialog({ college: c })}
                              aria-label={`Change ${c.name}`}
                            >
                              Change
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </>
        )}
      </Stack>
      {scheduling && (
        <DriveDialog
          onClose={() => setScheduling(false)}
          onSaved={() => {
            setScheduling(false);
            void load();
          }}
        />
      )}
      {collegeDialog && (
        <CollegeDialog
          college={collegeDialog.college}
          onClose={() => setCollegeDialog(null)}
          onSaved={() => {
            setCollegeDialog(null);
            void load();
          }}
        />
      )}
    </Paper>
  );
}
