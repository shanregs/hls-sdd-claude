import { useCallback, useEffect, useState } from "react";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  getMyMonth,
  saveMyMark,
  type DayView,
  type TeacherMonthView,
} from "./attendanceApi";
import { MarkDialog } from "./MarkDialog";
import { MonthCalendar } from "./MonthCalendar";
import { currentMonth, monthLabel, todayKey } from "./monthUtils";
import { RollupSummary } from "./RollupSummary";

/** My Attendance (spec 008 US1): this month as a calendar; mark today or a day in the last 3 days. */
export function MyAttendancePage() {
  const { authFetch } = useAuth();
  const month = currentMonth();
  const [view, setView] = useState<TeacherMonthView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [marking, setMarking] = useState<DayView | null>(null);

  const load = useCallback(async () => {
    const result = await getMyMonth(authFetch, month);
    if (result.ok) {
      setError(null);
      setView(result.data);
    } else {
      setError(result.reason);
      setView(null);
    }
  }, [authFetch, month]);

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

  const today = view?.days.find((d) => d.date === todayKey());

  return (
    <Box>
      <Stack
        direction="row"
        sx={{ alignItems: "center", justifyContent: "space-between", mb: 2 }}
      >
        <Typography variant="h5" component="h1">
          My Attendance - {monthLabel(month)}
        </Typography>
        {today && today.editableBy === "SELF" && (
          <Button variant="contained" onClick={() => setMarking(today)}>
            Mark today
          </Button>
        )}
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
              This month is locked. Ask your Manager if something needs to
              change.
            </Alert>
          )}
          <MonthCalendar
            days={view.days}
            locked={view.locked}
            editor="SELF"
            onSelect={setMarking}
          />
          <Typography variant="caption">
            You can mark today and the previous 3 days. Older days or days your
            Manager set must be corrected by your Manager.
          </Typography>
          <RollupSummary rollup={view.rollup} />
        </Stack>
      )}
      {marking && (
        <MarkDialog
          date={marking.date}
          existing={marking.mark}
          onSave={(body) => saveMyMark(authFetch, marking.date, body)}
          onClose={() => setMarking(null)}
          onSaved={() => {
            setMarking(null);
            void load();
          }}
        />
      )}
    </Box>
  );
}
