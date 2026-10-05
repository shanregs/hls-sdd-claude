import { useEffect, useState } from "react";
import { Alert, Box, Chip, Paper, Stack, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate } from "./formatters";
import { STATUS_LABELS, type TeacherSummary } from "./teachersApi";

type LoadState =
  | { kind: "loading" }
  | { kind: "ready"; teacher: TeacherSummary }
  | { kind: "notSetUp" }
  | { kind: "error" };

/**
 * A Teacher's own profile (spec 005 FR-024): name, contact details, status and current School -
 * never salary, another Teacher, or placement history. A Teacher with no record yet sees a message,
 * not an error.
 */
export function MyTeacherProfile() {
  const { authFetch } = useAuth();
  const [state, setState] = useState<LoadState>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const response = await authFetch("/api/v1/teachers/me");
        if (cancelled) return;
        if (response.status === 404) {
          setState({ kind: "notSetUp" });
        } else if (!response.ok) {
          setState({ kind: "error" });
        } else {
          setState({
            kind: "ready",
            teacher: (await response.json()) as TeacherSummary,
          });
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
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        My teacher record
      </Typography>
      {state.kind === "loading" && <Typography>Loading…</Typography>}
      {state.kind === "error" && (
        <Alert severity="error" role="alert">
          Could not load your profile.
        </Alert>
      )}
      {state.kind === "notSetUp" && (
        <Typography>
          Your profile has not been set up yet. Please ask your administrator.
        </Typography>
      )}
      {state.kind === "ready" && (
        <Stack spacing={1}>
          <Box>
            <Typography variant="subtitle1">{state.teacher.name}</Typography>
            <Chip
              size="small"
              label={STATUS_LABELS[state.teacher.status]}
              color={state.teacher.status === "ACTIVE" ? "success" : "default"}
            />
          </Box>
          <Typography variant="body2">
            Phone: {state.teacher.phone ?? "Not recorded"}
          </Typography>
          <Typography variant="body2">
            Email: {state.teacher.email ?? "Not recorded"}
          </Typography>
          <Typography variant="body2">
            Address: {state.teacher.address ?? "Not recorded"}
          </Typography>
          <Typography variant="body2">
            Current school: {state.teacher.school?.name ?? "Not placed yet"}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Status since {formatDate(state.teacher.statusEffectiveOn)}
          </Typography>
        </Stack>
      )}
    </Paper>
  );
}
