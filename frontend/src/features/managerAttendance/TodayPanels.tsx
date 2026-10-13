import { useEffect, useState } from "react";
import { Alert, Paper, Stack, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  getMissed,
  getOnLeave,
  type MissedManager,
  type OnLeaveManager,
} from "./managerAttendanceApi";

/** Today's missed check-ins and the Managers on leave today (spec 032 US4), for the Admin and Director. */
export function TodayPanels() {
  const { authFetch } = useAuth();
  const [missed, setMissed] = useState<MissedManager[] | null>(null);
  const [onLeave, setOnLeave] = useState<OnLeaveManager[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      const [m, l] = await Promise.all([
        getMissed(authFetch),
        getOnLeave(authFetch),
      ]);
      if (!m.ok) setError(m.reason);
      else setMissed(m.data);
      if (!l.ok) setError(l.reason);
      else setOnLeave(l.data);
    })();
  }, [authFetch]);

  if (error) {
    return (
      <Alert severity="error" role="alert" sx={{ mb: 2 }}>
        {error}
      </Alert>
    );
  }
  return (
    <Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ mb: 2 }}>
      <Paper
        variant="outlined"
        sx={{ p: 2, flex: 1 }}
        component="section"
        aria-labelledby="missed-title"
      >
        <Typography id="missed-title" variant="h6" component="h2" gutterBottom>
          Missed check-ins today
        </Typography>
        {missed === null ? null : missed.length === 0 ? (
          <Typography color="text.secondary">
            Nobody has missed a check-in.
          </Typography>
        ) : (
          <Stack component="ul" spacing={0.5} sx={{ m: 0, pl: 2 }}>
            {missed.map((m) => (
              <Typography key={m.managerId} component="li">
                {m.name}
                {m.employeeId ? ` (${m.employeeId})` : ""}
              </Typography>
            ))}
          </Stack>
        )}
      </Paper>
      <Paper
        variant="outlined"
        sx={{ p: 2, flex: 1 }}
        component="section"
        aria-labelledby="on-leave-title"
      >
        <Typography
          id="on-leave-title"
          variant="h6"
          component="h2"
          gutterBottom
        >
          On leave today
        </Typography>
        {onLeave === null ? null : onLeave.length === 0 ? (
          <Typography color="text.secondary">
            Nobody is on leave today.
          </Typography>
        ) : (
          <Stack component="ul" spacing={0.5} sx={{ m: 0, pl: 2 }}>
            {onLeave.map((m) => (
              <Typography key={m.managerId} component="li">
                {m.name}, {m.fraction >= 1 ? "full day" : "half day"}
                {m.unpaid ? ", unpaid" : ""}
              </Typography>
            ))}
          </Stack>
        )}
      </Paper>
    </Stack>
  );
}
