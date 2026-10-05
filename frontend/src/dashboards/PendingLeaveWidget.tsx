import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { Link, Paper, Typography } from "@mui/material";
import { useAuth } from "../auth/useAuth";

type State =
  { kind: "loading" } | { kind: "ready"; pending: number } | { kind: "error" };

/**
 * Pending leave approvals (spec 009): how many leave requests in the caller's scope await a decision,
 * with a link to Leave Management. The count comes from the same scoped list the screen uses, so a
 * Manager only ever counts their own Teachers' requests.
 */
export function PendingLeaveWidget({ title = "Pending leave approvals" }) {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const response = await authFetch("/api/v1/leave?status=PENDING&size=1");
        if (!response.ok) throw new Error("not ok");
        const body = (await response.json()) as { pendingCount?: number };
        if (!cancelled) {
          setState({ kind: "ready", pending: body.pendingCount ?? 0 });
        }
      } catch {
        if (!cancelled) setState({ kind: "error" });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  return (
    <Paper variant="outlined" sx={{ p: 2.5, minHeight: 96 }}>
      <Typography variant="subtitle2" color="text.secondary">
        {title}
      </Typography>
      {state.kind === "loading" && (
        <Typography variant="body2" color="text.secondary">
          Loading…
        </Typography>
      )}
      {state.kind === "error" && (
        <Typography variant="body2" color="text.secondary">
          Could not load the pending leave requests.
        </Typography>
      )}
      {state.kind === "ready" && (
        <>
          <Typography variant="h4" component="p" aria-live="polite">
            {state.pending}
          </Typography>
          <Link component={RouterLink} to="/operations/leave" variant="body2">
            {state.pending === 0 ? "Open Leave Management" : "Review requests"}
          </Link>
        </>
      )}
    </Paper>
  );
}
