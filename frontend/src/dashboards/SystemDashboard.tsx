import { useEffect, useState } from "react";
import { Alert, Box, CircularProgress, Paper, Typography } from "@mui/material";
import { useAuth } from "../auth/useAuth";
import { formatDateTime, formatUptime } from "../features/settings/format";
import { getDashboard, type Dashboard } from "../features/settings/settingsApi";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; data: Dashboard };

function Card({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
        {title}
      </Typography>
      {children}
    </Paper>
  );
}

/** System's own dashboard (spec 011 US1): platform health, users and recent audit. Never business data. */
export function SystemDashboard() {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;
    void getDashboard(authFetch).then((result) => {
      if (cancelled) return;
      setState(
        result.ok
          ? { kind: "ready", data: result.data }
          : { kind: "error", reason: result.reason },
      );
    });
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  return (
    <Box>
      <Typography variant="h6" component="h2" gutterBottom>
        System overview
      </Typography>
      {state.kind === "loading" && (
        <CircularProgress aria-label="Loading the system overview" />
      )}
      {state.kind === "error" && <Alert severity="error">{state.reason}</Alert>}
      {state.kind === "ready" && <Ready data={state.data} />}
    </Box>
  );
}

function Ready({ data }: { data: Dashboard }) {
  const { health, users, recentAudit } = data;
  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))",
        gap: 2,
      }}
    >
      <Card title="System health">
        <Typography
          variant="h5"
          color={health.database === "UP" ? "success.main" : "error.main"}
        >
          Database {health.database}
        </Typography>
        <Typography variant="body2">
          Version {health.applicationVersion}
          {health.migrationVersion
            ? ` · migration ${health.migrationVersion}`
            : ""}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Up {formatUptime(health.uptimeSeconds)} since{" "}
          {formatDateTime(health.startedAt)}
        </Typography>
      </Card>
      <Card title="Users">
        <Typography variant="h5">{users.active} active</Typography>
        <Typography variant="body2">
          {users.total} in total · {users.inactive} inactive · {users.lockedNow}{" "}
          locked now
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {Object.entries(users.byRole)
            .map(([role, count]) => `${role.toLowerCase()} ${count}`)
            .join(" · ")}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {data.failedSignInsLast24h} failed sign-ins in the last 24 hours
        </Typography>
      </Card>
      <Card title="Recent audit events">
        {recentAudit.status !== "OK" ? (
          <Typography variant="body2" color="text.secondary">
            Audit events are not available right now.
          </Typography>
        ) : recentAudit.events.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            Nothing recorded yet.
          </Typography>
        ) : (
          <Box component="ul" sx={{ m: 0, pl: 2 }}>
            {recentAudit.events.map((e, i) => (
              <li key={`${e.at}-${i}`}>
                <Typography variant="body2">
                  {e.summary}
                  <Typography
                    component="span"
                    variant="caption"
                    color="text.secondary"
                  >
                    {" "}
                    · {e.actorName ?? "unknown"} · {formatDateTime(e.at)}
                  </Typography>
                </Typography>
              </li>
            ))}
          </Box>
        )}
      </Card>
    </Box>
  );
}
