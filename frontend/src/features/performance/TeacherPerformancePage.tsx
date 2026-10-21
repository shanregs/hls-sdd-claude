import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Link,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "../teachers/formatters";
import { ResultChip } from "./Chips";
import {
  PERFORMANCE_ROUTE,
  RESULT_LABELS,
  getTeacherPage,
  startObservation,
  type Result,
  type TeacherPage,
  type TimelineEntry,
} from "./performanceApi";
import { PlanDialog } from "./PlanDialog";

type Loaded =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; page: TeacherPage };

function entryLink(entry: TimelineEntry): string | null {
  switch (entry.type) {
    case "OBSERVATION":
      return `${PERFORMANCE_ROUTE}/observations/${entry.id}`;
    case "ISSUE":
      return `${PERFORMANCE_ROUTE}/issues/${entry.id}`;
    default:
      return null;
  }
}

/**
 * One Teacher's whole performance record on one page (spec 026 US6): observations, Principal feedback, issues and referrals in
 * date order, the latest result first.
 */
export function TeacherPerformancePage() {
  const { teacherId } = useParams();
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const granted = useGrantedActions(PERFORMANCE_ROUTE);
  const [loaded, setLoaded] = useState<Loaded>({ kind: "loading" });
  const [planning, setPlanning] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!teacherId) return;
    const result = await getTeacherPage(authFetch, teacherId);
    setLoaded(
      result.ok
        ? { kind: "ready", page: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, teacherId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  if (loaded.kind === "loading") {
    return <CircularProgress aria-label="Loading performance" />;
  }
  if (loaded.kind === "error") {
    return <Alert severity="error">{loaded.reason}</Alert>;
  }
  const page = loaded.page;

  async function start() {
    const result = await startObservation(authFetch, page.teacherId);
    if (result.ok) {
      navigate(`${PERFORMANCE_ROUTE}/observations/${result.data.id}`);
    } else {
      setProblem(result.reason);
    }
  }

  return (
    <Box>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{
          alignItems: { sm: "center" },
          justifyContent: "space-between",
          mb: 1,
        }}
      >
        <Typography variant="h5" component="h1">
          {page.teacherName}: performance
        </Typography>
        <Stack direction="row" spacing={1}>
          {granted.has("CREATE") && (
            <Button variant="contained" onClick={() => void start()}>
              Start observation
            </Button>
          )}
          {granted.has("CREATE") && (
            <Button variant="outlined" onClick={() => setPlanning(true)}>
              Plan an observation
            </Button>
          )}
        </Stack>
      </Stack>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {page.schoolName ? `${page.schoolName}. ` : "Not placed. "}
        {page.managerName ? `Manager: ${page.managerName}.` : ""}
      </Typography>
      {problem && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {problem}
        </Alert>
      )}

      <Stack direction="row" spacing={1} sx={{ alignItems: "center", mb: 2 }}>
        <Typography>Latest result:</Typography>
        {page.latestResult ? (
          <ResultChip result={page.latestResult} />
        ) : (
          <Typography color="text.secondary">No observation yet</Typography>
        )}
        {page.openPlan && (
          <Chip
            size="small"
            label={`Observation planned for ${formatDate(page.openPlan.plannedOn)}`}
          />
        )}
      </Stack>

      <Typography variant="h6" component="h2">
        History
      </Typography>
      {page.entries.length === 0 ? (
        <Typography color="text.secondary">Nothing recorded yet.</Typography>
      ) : (
        <List dense aria-label="Performance history">
          {page.entries.map((entry) => {
            const to = entryLink(entry);
            return (
              <ListItem key={`${entry.type}-${entry.id}`}>
                <ListItemText
                  primary={
                    to ? (
                      <Link component={RouterLink} to={to}>
                        {entry.title}
                      </Link>
                    ) : (
                      entry.title
                    )
                  }
                  secondary={[
                    formatDate(entry.date),
                    entry.result ? RESULT_LABELS[entry.result as Result] : null,
                    entry.status.toLowerCase().replace(/_/g, " "),
                  ]
                    .filter(Boolean)
                    .join(" - ")}
                />
              </ListItem>
            );
          })}
        </List>
      )}

      {planning && (
        <PlanDialog
          teacherId={page.teacherId}
          onClose={() => setPlanning(false)}
          onSaved={() => {
            setPlanning(false);
            void load();
          }}
        />
      )}
    </Box>
  );
}
