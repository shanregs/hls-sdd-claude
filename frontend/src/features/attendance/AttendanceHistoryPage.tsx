import { useEffect, useState } from "react";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { getMyMonth, type TeacherMonthView } from "./attendanceApi";
import { MonthCalendar } from "./MonthCalendar";
import { currentMonth, monthLabel, shiftMonth } from "./monthUtils";
import { RollupSummary } from "./RollupSummary";

/** Attendance History (spec 008 US7): earlier months, read-only; a locked month shows its frozen rollup. */
export function AttendanceHistoryPage() {
  const { authFetch } = useAuth();
  const thisMonth = currentMonth();
  const [month, setMonth] = useState(shiftMonth(thisMonth, -1));
  const [view, setView] = useState<TeacherMonthView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await getMyMonth(authFetch, month);
      if (cancelled) return;
      if (result.ok) {
        setError(null);
        setView(result.data);
      } else {
        setError(result.reason);
        setView(null);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, month]);

  return (
    <Box>
      <Typography variant="h5" component="h1" sx={{ mb: 2 }}>
        Attendance History
      </Typography>
      <Stack
        direction="row"
        spacing={2}
        sx={{ mb: 2, alignItems: "center", flexWrap: "wrap", gap: 1 }}
      >
        <Button onClick={() => setMonth(shiftMonth(month, -1))}>
          Previous month
        </Button>
        <Typography
          component="span"
          sx={{ fontWeight: 700, minWidth: 140, textAlign: "center" }}
          aria-live="polite"
        >
          {monthLabel(month)}
        </Typography>
        <Button
          disabled={month >= shiftMonth(thisMonth, -1)}
          onClick={() => setMonth(shiftMonth(month, 1))}
        >
          Next month
        </Button>
      </Stack>
      {error && (
        <Alert severity="info" role="alert" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {!view && !error && <Typography>Loading your attendance…</Typography>}
      {view && (
        <Stack spacing={2}>
          {view.locked && (
            <Alert severity="info">
              Locked: this month is closed and cannot be changed. Ask your
              Manager if something needs to be corrected.
            </Alert>
          )}
          <MonthCalendar days={view.days} locked={view.locked} />
          <RollupSummary rollup={view.rollup} />
        </Stack>
      )}
    </Box>
  );
}
