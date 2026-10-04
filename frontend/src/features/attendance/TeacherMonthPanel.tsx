import { useEffect, useState, type ReactNode } from "react";
import {
  Alert,
  Box,
  Button,
  Divider,
  Drawer,
  IconButton,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  getMarkHistory,
  getTeacherMonth,
  type HistoryEntry,
  type TeacherMonthView,
} from "./attendanceApi";
import { formatDate, monthLabel } from "./monthUtils";
import { RollupSummary } from "./RollupSummary";

interface TeacherMonthPanelProps {
  teacherId: string;
  teacherName: string;
  month: string;
  /** Changes when the grid reloads, so the panel refreshes too. */
  refreshKey: number;
  /** Extra controls such as lock and reopen. */
  extras?: ReactNode;
  onClose: () => void;
}

/** A side panel with one Teacher's rollup and the history of each marked day (spec 008 US2, US4). */
export function TeacherMonthPanel({
  teacherId,
  teacherName,
  month,
  refreshKey,
  extras,
  onClose,
}: TeacherMonthPanelProps) {
  const { authFetch } = useAuth();
  const [view, setView] = useState<TeacherMonthView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [history, setHistory] = useState<{
    date: string;
    entries: HistoryEntry[];
  } | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await getTeacherMonth(authFetch, teacherId, month);
      if (cancelled) return;
      if (result.ok) {
        setError(null);
        setView(result.data);
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, teacherId, month, refreshKey]);

  const showHistory = async (date: string) => {
    const result = await getMarkHistory(authFetch, teacherId, date);
    if (result.ok) {
      setHistory({ date, entries: result.data });
    } else {
      setError(result.reason);
    }
  };

  const marked = view?.days.filter((d) => d.mark) ?? [];

  return (
    <Drawer anchor="right" open onClose={onClose}>
      <Box
        sx={{ width: { xs: "100vw", sm: 420 }, p: 2 }}
        role="region"
        aria-label={`${teacherName} attendance`}
      >
        <Stack
          direction="row"
          sx={{ justifyContent: "space-between", alignItems: "center" }}
        >
          <Typography variant="h6" component="h2">
            {teacherName} - {monthLabel(month)}
          </Typography>
          <IconButton aria-label="Close" onClick={onClose}>
            ✕
          </IconButton>
        </Stack>
        {error && (
          <Alert severity="error" sx={{ my: 1 }} role="alert">
            {error}
          </Alert>
        )}
        {!view && !error && <Typography>Loading…</Typography>}
        {view && (
          <Stack spacing={2} sx={{ mt: 1 }}>
            <RollupSummary rollup={view.rollup} />
            {extras}
            <Divider />
            <Typography variant="subtitle1" component="h3">
              Marked days
            </Typography>
            {marked.length === 0 && (
              <Typography variant="body2">
                No days marked this month.
              </Typography>
            )}
            {marked.map((d) => (
              <Box key={d.date}>
                <Stack
                  direction="row"
                  spacing={1}
                  sx={{ alignItems: "center" }}
                >
                  <Typography sx={{ minWidth: 100 }}>
                    {formatDate(d.date)}
                  </Typography>
                  <Typography variant="body2" sx={{ flexGrow: 1 }}>
                    {d.mark!.codeName}
                    {d.mark!.dayValue < 1 ? " (half day)" : ""} · set by{" "}
                    {d.mark!.setByName}
                  </Typography>
                  <Button size="small" onClick={() => showHistory(d.date)}>
                    History {formatDate(d.date)}
                  </Button>
                </Stack>
                {history?.date === d.date && (
                  <Box component="ul" sx={{ m: 0, pl: 3 }}>
                    {history.entries.map((e, i) => (
                      <li key={i}>
                        <Typography variant="body2">
                          {e.action.toLowerCase()}
                          {e.codeName ? ` ${e.codeName}` : ""}
                          {e.dayValue !== null && e.dayValue < 1
                            ? " (half day)"
                            : ""}{" "}
                          by {e.setByName} on {formatDate(e.setAt.slice(0, 10))}
                        </Typography>
                      </li>
                    ))}
                  </Box>
                )}
              </Box>
            ))}
          </Stack>
        )}
      </Box>
    </Drawer>
  );
}
