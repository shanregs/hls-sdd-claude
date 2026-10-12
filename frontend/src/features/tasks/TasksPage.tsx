import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  MenuItem,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useAccessModel } from "../../access-model/useAccessModel";
import { formatDate, todayIso } from "../teachers/formatters";
import {
  STATUS_LABELS,
  TASKS_ROUTE,
  createTask,
  getAssignees,
  getSummary,
  getTypes,
  listTasks,
  type Assignee,
  type TaskRow,
  type TaskSummary,
  type TaskView,
  type TypeOption,
} from "./taskApi";

const TABS: { view: TaskView; label: string; count?: keyof TaskSummary }[] = [
  { view: "today", label: "Today", count: "today" },
  { view: "upcoming", label: "Upcoming", count: "upcoming" },
  { view: "overdue", label: "Overdue", count: "overdue" },
  { view: "awaiting", label: "Awaiting verification", count: "awaiting" },
  { view: "done", label: "Done", count: "done" },
  { view: "all", label: "All" },
];

type Rows =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: TaskRow[] };

/**
 * OPERATIONS -> My Tasks (Manager) or Manager Tasks (Admin, Director), spec 025 US1 and US2: the Today, Upcoming, Overdue,
 * Awaiting verification and Done lists with counts, a create form, and for management filters by Manager and type.
 */
export function TasksPage() {
  const { authFetch } = useAuth();
  const { accessModel } = useAccessModel();
  const management = Boolean(
    accessModel?.roles.some((r) => r === "ADMIN" || r === "DIRECTOR"),
  );
  const [view, setView] = useState<TaskView>("today");
  const [assignee, setAssignee] = useState("");
  const [type, setType] = useState("");
  const [rows, setRows] = useState<Rows>({ kind: "loading" });
  const [summary, setSummary] = useState<TaskSummary | null>(null);
  const [types, setTypes] = useState<TypeOption[]>([]);
  const [assignees, setAssignees] = useState<Assignee[]>([]);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setRows({ kind: "loading" });
    const [list, counts] = await Promise.all([
      listTasks(authFetch, {
        view,
        assigneeUserId: assignee || undefined,
        type: type || undefined,
      }),
      getSummary(authFetch, assignee || undefined),
    ]);
    setRows(
      list.ok
        ? { kind: "ready", rows: list.data.content }
        : { kind: "error", reason: list.reason },
    );
    if (counts.ok) setSummary(counts.data);
  }, [authFetch, view, assignee, type]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  useEffect(() => {
    (async () => {
      const [t, a] = await Promise.all([
        getTypes(authFetch),
        getAssignees(authFetch),
      ]);
      if (t.ok) setTypes(t.data);
      if (a.ok) setAssignees(a.data);
    })();
  }, [authFetch]);

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
          {management ? "Manager Tasks" : "My Tasks"}
        </Typography>
        <Button variant="contained" onClick={() => setCreating(true)}>
          New task
        </Button>
      </Stack>

      {summary && summary.escalated > 0 && management && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {summary.escalated} open{" "}
          {summary.escalated === 1 ? "task has" : "tasks have"} been escalated
          to the Director.
        </Alert>
      )}

      {management && (
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={2}
          sx={{ mb: 2 }}
        >
          <TextField
            select
            size="small"
            label="Manager"
            value={assignee}
            onChange={(e) => setAssignee(e.target.value)}
            sx={{ minWidth: 200 }}
          >
            <MenuItem value="">All Managers</MenuItem>
            {assignees.map((a) => (
              <MenuItem key={a.userId} value={a.userId}>
                {a.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            size="small"
            label="Type"
            value={type}
            onChange={(e) => setType(e.target.value)}
            sx={{ minWidth: 220 }}
          >
            <MenuItem value="">All types</MenuItem>
            {types.map((t) => (
              <MenuItem key={t.value} value={t.value}>
                {t.label}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      )}

      <Tabs
        value={view}
        onChange={(_, v: TaskView) => setView(v)}
        variant="scrollable"
        aria-label="Task lists"
        sx={{ mb: 2 }}
      >
        {TABS.map((t) => (
          <Tab
            key={t.view}
            value={t.view}
            label={
              t.count && summary ? `${t.label} (${summary[t.count]})` : t.label
            }
          />
        ))}
      </Tabs>

      {rows.kind === "loading" && (
        <CircularProgress aria-label="Loading tasks" />
      )}
      {rows.kind === "error" && <Alert severity="error">{rows.reason}</Alert>}
      {rows.kind === "ready" && rows.rows.length === 0 && (
        <Typography color="text.secondary">No tasks here.</Typography>
      )}
      {rows.kind === "ready" && rows.rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label="Tasks">
            <TableHead>
              <TableRow>
                <TableCell>Task</TableCell>
                <TableCell>Type</TableCell>
                {management && <TableCell>Manager</TableCell>}
                <TableCell>Due</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.rows.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>
                    <Link
                      component={RouterLink}
                      to={`${TASKS_ROUTE}/${row.id}`}
                    >
                      {row.title}
                    </Link>
                    {row.linkedLabel && (
                      <Typography
                        variant="caption"
                        component="div"
                        color="text.secondary"
                      >
                        {row.linkedLabel}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{row.typeLabel}</TableCell>
                  {management && <TableCell>{row.assigneeName}</TableCell>}
                  <TableCell>
                    {formatDate(row.dueOn)}
                    {row.overdue && (
                      <Chip
                        size="small"
                        color="error"
                        label={`${row.daysOverdue} ${row.daysOverdue === 1 ? "day" : "days"} overdue`}
                        sx={{ ml: 1 }}
                      />
                    )}
                    {row.escalated && (
                      <Chip
                        size="small"
                        color="warning"
                        label="Escalated"
                        sx={{ ml: 1 }}
                      />
                    )}
                  </TableCell>
                  <TableCell>{STATUS_LABELS[row.status]}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {creating && (
        <NewTaskDialog
          types={types}
          assignees={assignees}
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false);
            void load();
          }}
        />
      )}
    </Box>
  );
}

function NewTaskDialog({
  types,
  assignees,
  onClose,
  onCreated,
}: {
  types: TypeOption[];
  assignees: Assignee[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const [type, setType] = useState("SCHOOL_VISIT");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [assignee, setAssignee] = useState(
    assignees.length === 1 ? assignees[0].userId : "",
  );
  const [dueOn, setDueOn] = useState(todayIso());
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  async function submit() {
    const found: Record<string, string> = {};
    if (title.trim().length < 3)
      found.title = "The title must be at least 3 characters.";
    if (!assignee) found.assignee = "Choose who the task is for.";
    if (!dueOn) found.dueOn = "The due date is required.";
    else if (dueOn < todayIso())
      found.dueOn = "The due date cannot be in the past.";
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    const result = await createTask(authFetch, {
      type,
      title: title.trim(),
      description: description.trim() || null,
      assigneeUserId: assignee,
      dueOn,
      escalateAfterDays: null,
    });
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onCreated();
    navigate(`${TASKS_ROUTE}/${result.data.task.id}`);
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="new-task-title"
    >
      <DialogTitle id="new-task-title">New task</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            select
            label="Type"
            value={type}
            onChange={(e) => setType(e.target.value)}
          >
            {types.map((t) => (
              <MenuItem key={t.value} value={t.value}>
                {t.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            error={Boolean(errors.title)}
            helperText={errors.title}
            required
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={2}
          />
          <TextField
            select
            label="Manager"
            value={assignee}
            onChange={(e) => setAssignee(e.target.value)}
            error={Boolean(errors.assignee)}
            helperText={errors.assignee}
            required
          >
            {assignees.map((a) => (
              <MenuItem key={a.userId} value={a.userId}>
                {a.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Due date"
            type="date"
            value={dueOn}
            onChange={(e) => setDueOn(e.target.value)}
            error={Boolean(errors.dueOn)}
            helperText={errors.dueOn}
            slotProps={{ inputLabel: { shrink: true } }}
            required
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => void submit()}
          disabled={saving}
        >
          Create task
        </Button>
      </DialogActions>
    </Dialog>
  );
}
