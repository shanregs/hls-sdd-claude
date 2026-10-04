import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { Alert, Box, Link, Paper, Typography } from "@mui/material";
import { useAuth } from "../auth/useAuth";
import { PlaceholderWidget } from "./PlaceholderWidget";
import { PendingLeaveWidget } from "./PendingLeaveWidget";

interface ScopeSummary {
  orgWide: boolean;
  zoneCount: number;
  schoolCount: number;
}

type Counts =
  | { kind: "loading" }
  | { kind: "ready"; zones: number; schools: number; teachers: number }
  | { kind: "error" };

/** One count card with a link to the matching scoped list. */
function CountWidget({
  title,
  count,
  to,
  linkLabel,
}: {
  title: string;
  count: number;
  to: string;
  linkLabel: string;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2.5, minHeight: 96 }}>
      <Typography variant="subtitle2" color="text.secondary">
        {title}
      </Typography>
      <Typography variant="h4" component="p">
        {count}
      </Typography>
      <Link component={RouterLink} to={to} variant="body2">
        {linkLabel}
      </Link>
    </Paper>
  );
}

/**
 * Manager landing dashboard (spec 005 FR-023): the Zones, Schools and Teachers assigned to them,
 * with links to the scoped lists, and an empty state when nothing is assigned. Never org-wide
 * content for a Manager (Constitution Principle III).
 */
export function ManagerDashboard() {
  const { authFetch } = useAuth();
  const [counts, setCounts] = useState<Counts>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [scopeResponse, teachersResponse] = await Promise.all([
          authFetch("/api/v1/me/scope"),
          authFetch("/api/v1/teachers?size=1"),
        ]);
        if (!scopeResponse.ok || !teachersResponse.ok) {
          throw new Error("Could not load your overview.");
        }
        const scope = (await scopeResponse.json()) as ScopeSummary;
        const teachers = (await teachersResponse.json()) as {
          totalElements: number;
        };
        if (!cancelled) {
          setCounts({
            kind: "ready",
            zones: scope.zoneCount,
            schools: scope.schoolCount,
            teachers: teachers.totalElements,
          });
        }
      } catch {
        if (!cancelled) setCounts({ kind: "error" });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const empty =
    counts.kind === "ready" &&
    counts.zones === 0 &&
    counts.schools === 0 &&
    counts.teachers === 0;

  return (
    <Box>
      <Typography variant="h6" component="h2" gutterBottom>
        My assigned overview
      </Typography>
      {counts.kind === "error" && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          Could not load your assigned overview.
        </Alert>
      )}
      {empty && (
        <Alert severity="info" sx={{ mb: 2 }} role="status">
          Nothing has been assigned to you yet. An administrator assigns your
          zones and schools.
        </Alert>
      )}
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          gap: 2,
        }}
      >
        {counts.kind === "ready" && !empty && (
          <>
            <CountWidget
              title="My assigned zones"
              count={counts.zones}
              to="/master-data/schools"
              linkLabel="View my schools"
            />
            <CountWidget
              title="My assigned schools"
              count={counts.schools}
              to="/master-data/schools"
              linkLabel="View schools"
            />
            <CountWidget
              title="My assigned teachers"
              count={counts.teachers}
              to="/master-data/teachers"
              linkLabel="View teachers"
            />
          </>
        )}
        {counts.kind === "loading" && (
          <Typography variant="body2" color="text.secondary">
            Loading…
          </Typography>
        )}
        <PendingLeaveWidget />
        <PlaceholderWidget title="This month's attendance" />
      </Box>
    </Box>
  );
}
