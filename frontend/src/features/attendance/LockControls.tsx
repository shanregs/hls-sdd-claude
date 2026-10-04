import { useEffect, useState } from "react";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { getMonthEvents, relockMonth, type MonthEvent } from "./attendanceApi";
import { formatDate } from "./monthUtils";
import { ReopenDialog } from "./ReopenDialog";

interface LockControlsProps {
  teacherId: string;
  teacherName: string;
  month: string;
  /** Changes when the grid reloads. */
  refreshKey: number;
  onChanged: () => void;
}

const EVENT_TEXT: Record<MonthEvent["event"], string> = {
  LOCKED: "Locked",
  REOPENED: "Reopened",
  RELOCKED: "Locked again",
};

/** Reopen, relock and the lock history of one Teacher-month, for callers with PROCESS (spec 008 US6). */
export function LockControls({
  teacherId,
  teacherName,
  month,
  refreshKey,
  onChanged,
}: LockControlsProps) {
  const { authFetch } = useAuth();
  const [events, setEvents] = useState<MonthEvent[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reopening, setReopening] = useState(false);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await getMonthEvents(authFetch, teacherId, month);
      if (cancelled) return;
      if (result.ok) setEvents(result.data);
      else setError(result.reason);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, teacherId, month, refreshKey, version]);

  const last = events?.[events.length - 1];
  const locked = last?.event === "LOCKED" || last?.event === "RELOCKED";
  const reopened = last?.event === "REOPENED";

  const relock = async () => {
    const result = await relockMonth(authFetch, teacherId, month);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    setError(null);
    setVersion((v) => v + 1);
    onChanged();
  };

  return (
    <Box>
      <Typography variant="subtitle1" component="h3">
        Month lock
      </Typography>
      {error && (
        <Alert severity="error" role="alert" sx={{ my: 1 }}>
          {error}
        </Alert>
      )}
      {events && (
        <Typography variant="body2" sx={{ my: 1 }}>
          {locked ? "This month is locked." : "This month is open."}
        </Typography>
      )}
      <Stack direction="row" spacing={1}>
        {locked && (
          <Button variant="outlined" onClick={() => setReopening(true)}>
            Reopen month
          </Button>
        )}
        {reopened && (
          <Button variant="outlined" onClick={relock}>
            Lock again
          </Button>
        )}
      </Stack>
      {events && events.length > 0 && (
        <Box component="ul" sx={{ m: 0, mt: 1, pl: 3 }}>
          {events.map((e, i) => (
            <li key={i}>
              <Typography variant="body2">
                {EVENT_TEXT[e.event]} on {formatDate(e.occurredAt.slice(0, 10))}
                {e.reason ? ` - ${e.reason}` : ""}
              </Typography>
            </li>
          ))}
        </Box>
      )}
      {reopening && (
        <ReopenDialog
          teacherId={teacherId}
          teacherName={teacherName}
          month={month}
          onClose={() => setReopening(false)}
          onReopened={() => {
            setReopening(false);
            setVersion((v) => v + 1);
            onChanged();
          }}
        />
      )}
    </Box>
  );
}
