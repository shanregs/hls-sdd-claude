import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../auth/useAuth";
import type { SessionSummary } from "../auth/authApi";
import { MyTeacherProfile } from "../features/teachers/MyTeacherProfile";

/**
 * Self-service session management (FR-015, User Story 5): shows only the caller's own active
 * sessions, marks the current one, and lets them end any other one. Uses {@link useAuth}'s
 * `authFetch` so an expired access token renews silently instead of surfacing an error here
 * (User Story 3).
 */
export function ProfilePage() {
  const { user, authFetch } = useAuth();
  const [sessions, setSessions] = useState<SessionSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const response = await authFetch("/api/v1/me/sessions");
        if (!response.ok) {
          throw new Error("Could not load your sessions.");
        }
        const data = (await response.json()) as SessionSummary[];
        if (!cancelled) {
          setSessions(data);
        }
      } catch {
        if (!cancelled) {
          setError("Could not load your sessions.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, reloadCount]);

  const handleEnd = async (sessionId: string) => {
    setError(null);
    try {
      const response = await authFetch(`/api/v1/me/sessions/${sessionId}`, {
        method: "DELETE",
      });
      if (!response.ok) {
        throw new Error("Could not end that session.");
      }
      setReloadCount((count) => count + 1);
    } catch {
      setError("Could not end that session.");
    }
  };

  if (!user) {
    return null;
  }

  return (
    <Box sx={{ p: 4, maxWidth: 600 }}>
      {user.roles.includes("TEACHER") && <MyTeacherProfile />}
      <Typography variant="h5" component="h1" gutterBottom>
        My sessions
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}

      {sessions === null && !error && <Typography>Loading…</Typography>}
      {sessions !== null && sessions.length === 0 && (
        <Typography>No active sessions.</Typography>
      )}

      <List>
        {sessions?.map((session) => (
          <ListItem
            key={session.id}
            secondaryAction={
              !session.current && (
                <Button size="small" onClick={() => handleEnd(session.id)}>
                  End session
                </Button>
              )
            }
          >
            <ListItemText
              primary={
                <Stack
                  direction="row"
                  spacing={1}
                  sx={{ alignItems: "center" }}
                >
                  <span>{session.deviceDescription}</span>
                  {session.current && (
                    <Chip label="This device" size="small" color="primary" />
                  )}
                </Stack>
              }
              secondary={`Signed in ${new Date(session.signedInAt).toLocaleString()} · last active ${new Date(session.lastActivityAt).toLocaleString()}`}
            />
          </ListItem>
        ))}
      </List>
    </Box>
  );
}
