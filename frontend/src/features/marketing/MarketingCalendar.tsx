import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  FormControlLabel,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "../teachers/formatters";
import { ActivityDialog, type ActivityMode } from "./ActivityDialog";
import {
  ACTIVITY_LABEL,
  listActivities,
  type Activity,
  type ActivityList,
} from "./marketingApi";

const ROUTE = "/marketing/calendar";

function monthRange(year: number, month: number) {
  const pad = (n: number) => String(n).padStart(2, "0");
  const last = new Date(year, month + 1, 0).getDate();
  return {
    from: `${year}-${pad(month + 1)}-01`,
    to: `${year}-${pad(month + 1)}-${pad(last)}`,
  };
}

function statusLabel(a: Activity) {
  switch (a.effectiveStatus) {
    case "MISSED":
      return "Missed";
    case "COMPLETED":
      return "Completed";
    case "CANCELLED":
      return "Cancelled";
    default:
      return "Planned";
  }
}

/**
 * MARKETING -> Calendar (spec 023 US1): the month's visits, calls and meetings with the person's own recruitment
 * drives beside them; a clash is shown, never blocked. Missed visits and rescheduled ones are marked.
 */
export function MarketingCalendar() {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const actions = useGrantedActions(ROUTE);
  const today = new Date();
  const [cursor, setCursor] = useState({
    year: today.getFullYear(),
    month: today.getMonth(),
  });
  const [mine, setMine] = useState(true);
  const [data, setData] = useState<ActivityList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<{
    mode: ActivityMode;
    activity?: Activity;
  } | null>(null);

  const load = useCallback(async () => {
    const range = monthRange(cursor.year, cursor.month);
    const result = await listActivities(authFetch, { ...range, mine });
    if (result.ok) {
      setError(null);
      setData(result.data);
    } else {
      setError(result.reason);
    }
  }, [authFetch, cursor, mine]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and month-driven fetch
    void load();
  }, [load]);

  const byDate = useMemo(() => {
    const map = new Map<string, { activities: Activity[]; drives: number }>();
    data?.content.forEach((a) => {
      const entry = map.get(a.date) ?? { activities: [], drives: 0 };
      entry.activities.push(a);
      map.set(a.date, entry);
    });
    data?.drives.forEach((d) => {
      const entry = map.get(d.date) ?? { activities: [], drives: 0 };
      entry.drives += 1;
      map.set(d.date, entry);
    });
    return map;
  }, [data]);

  const step = (delta: number) =>
    setCursor((c) => {
      const next = new Date(c.year, c.month + delta, 1);
      return { year: next.getFullYear(), month: next.getMonth() };
    });
  const first = new Date(cursor.year, cursor.month, 1);
  const lead = (first.getDay() + 6) % 7;
  const days = new Date(cursor.year, cursor.month + 1, 0).getDate();
  const cells: (number | null)[] = [
    ...Array<null>(lead).fill(null),
    ...Array.from({ length: days }, (_x, i) => i + 1),
  ];
  const monthTitle = first.toLocaleString("en-IN", {
    month: "long",
    year: "numeric",
  });

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Stack
          direction="row"
          sx={{
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: 1,
          }}
        >
          <Typography variant="h5" component="h1">
            Marketing calendar
          </Typography>
          {actions.has("CREATE") && (
            <Button
              variant="contained"
              onClick={() => setDialog({ mode: "plan" })}
            >
              Plan an activity
            </Button>
          )}
        </Stack>
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: "center", flexWrap: "wrap" }}
        >
          <Button
            size="small"
            onClick={() => step(-1)}
            aria-label="Previous month"
          >
            Previous
          </Button>
          <Typography variant="subtitle1" component="h2">
            {monthTitle}
          </Typography>
          <Button size="small" onClick={() => step(1)} aria-label="Next month">
            Next
          </Button>
          <FormControlLabel
            control={
              <Checkbox
                checked={mine}
                onChange={(e) => setMine(e.target.checked)}
              />
            }
            label="My activities"
          />
        </Stack>
        {error && (
          <Alert severity="error" role="alert">
            {error}
          </Alert>
        )}
        <Box
          aria-hidden="true"
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
            const entry = day ? byDate.get(iso) : undefined;
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
                {entry?.activities.map((a) => (
                  <Chip
                    key={a.id}
                    size="small"
                    color={
                      a.effectiveStatus === "MISSED"
                        ? "error"
                        : a.effectiveStatus === "COMPLETED"
                          ? "success"
                          : "default"
                    }
                    label={
                      a.prospect?.name ??
                      a.school?.name ??
                      ACTIVITY_LABEL[a.type]
                    }
                    sx={{ display: "flex", maxWidth: "100%" }}
                  />
                ))}
                {entry && entry.drives > 0 && (
                  <Chip
                    size="small"
                    variant="outlined"
                    label={`${entry.drives} drive${entry.drives > 1 ? "s" : ""}`}
                  />
                )}
              </Box>
            );
          })}
        </Box>
        {data && data.content.length === 0 && data.drives.length === 0 && (
          <Alert severity="info">
            Nothing planned this month. Plan a visit to a prospect.
          </Alert>
        )}
        {data && (data.content.length > 0 || data.drives.length > 0) && (
          <List aria-label={`Activities in ${monthTitle}`}>
            {data.content.map((a) => (
              <ListItem
                key={a.id}
                divider
                secondaryAction={
                  <Stack direction="row" spacing={0.5}>
                    {a.prospect && (
                      <Button
                        size="small"
                        onClick={() =>
                          navigate(`/marketing/prospects/${a.prospect!.id}`)
                        }
                        aria-label={`Open ${a.prospect.name}`}
                      >
                        Open
                      </Button>
                    )}
                    {actions.has("EDIT") && a.status === "PLANNED" && (
                      <>
                        <Button
                          size="small"
                          onClick={() =>
                            setDialog({ mode: "complete", activity: a })
                          }
                          aria-label={`Complete ${ACTIVITY_LABEL[a.type]} on ${formatDate(a.date)}`}
                        >
                          Complete
                        </Button>
                        <Button
                          size="small"
                          onClick={() =>
                            setDialog({ mode: "reschedule", activity: a })
                          }
                          aria-label={`Reschedule ${ACTIVITY_LABEL[a.type]} on ${formatDate(a.date)}`}
                        >
                          Reschedule
                        </Button>
                      </>
                    )}
                  </Stack>
                }
              >
                <ListItemText
                  primary={`${formatDate(a.date)}: ${ACTIVITY_LABEL[a.type]} - ${a.prospect?.name ?? a.school?.name ?? ""}`}
                  secondary={`${statusLabel(a)}${a.rescheduled ? ", rescheduled" : ""}${a.followUpOverdue ? ", follow-up overdue" : ""}`}
                />
              </ListItem>
            ))}
            {data.drives.map((d) => (
              <ListItem key={`${d.id}-${d.date}`} divider>
                <ListItemText
                  primary={`${formatDate(d.date)}: Campus drive - ${d.place ?? ""}`}
                  secondary={`Recruitment, ${d.status.toLowerCase()}`}
                />
              </ListItem>
            ))}
          </List>
        )}
      </Stack>
      {dialog && (
        <ActivityDialog
          mode={dialog.mode}
          activity={dialog.activity}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null);
            void load();
          }}
        />
      )}
    </Paper>
  );
}
