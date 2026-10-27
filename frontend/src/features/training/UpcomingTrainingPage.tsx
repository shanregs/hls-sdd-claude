import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate, formatTime } from "./format";
import { SessionDetail } from "./SessionDetail";
import {
  STATUS_LABELS,
  TYPE_LABELS,
  cancelRegistration,
  getUpcoming,
  registerForSession,
  type UpcomingItem,
} from "./trainingApi";
import { StatusChip } from "../../theme/StatusChip";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; items: UpcomingItem[] };

/** MY ATTENDANCE -> Upcoming Training (Teacher): sessions the Teacher is Required at or can register for, with register and cancel. */
export function UpcomingTrainingPage() {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [failure, setFailure] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = useCallback(async () => {
    const result = await getUpcoming(authFetch);
    setState(
      result.ok
        ? { kind: "ready", items: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- loads the list once
    void load();
  }, [load]);

  async function act(call: () => ReturnType<typeof registerForSession>) {
    setFailure(null);
    const result = await call();
    if (!result.ok) setFailure(result.reason);
    await load();
  }

  return (
    <Box>
      <Typography variant="h5" component="h1" sx={{ mb: 2 }}>
        Upcoming Training
      </Typography>
      {failure && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {failure}
        </Alert>
      )}
      {state.kind === "loading" && <CircularProgress aria-label="Loading" />}
      {state.kind === "error" && <Alert severity="error">{state.reason}</Alert>}
      {state.kind === "ready" && state.items.length === 0 && (
        <Typography>No upcoming training for you right now.</Typography>
      )}
      {state.kind === "ready" && (
        <Stack
          component="ul"
          spacing={2}
          sx={{ listStyle: "none", p: 0, m: 0 }}
        >
          {state.items.map(({ label, session: s }) => (
            <li key={s.id}>
              <Stack
                direction="row"
                spacing={1}
                useFlexGap
                sx={{ alignItems: "center", flexWrap: "wrap" }}
              >
                <span>{formatDate(s.date)}</span>
                <span>{formatTime(s.startsAt)}</span>
                <strong>{s.topic}</strong>
                <span>{TYPE_LABELS[s.type]}</span>
                {label === "REQUIRED" && (
                  <StatusChip kind="pending" size="small" label="Required" />
                )}
                {label === "REGISTERED" && (
                  <StatusChip kind="approved" size="small" label="Registered" />
                )}
                {label === "OPEN" && (
                  <Chip size="small" variant="outlined" label="Open" />
                )}
                {s.status !== "PUBLISHED" && (
                  <Chip size="small" label={STATUS_LABELS[s.status]} />
                )}
                {s.actions.includes("REGISTER") && (
                  <Button
                    size="small"
                    variant="contained"
                    onClick={() =>
                      void act(() => registerForSession(authFetch, s.id))
                    }
                  >
                    Register
                  </Button>
                )}
                {s.actions.includes("CANCEL_REGISTRATION") && (
                  <Button
                    size="small"
                    onClick={() =>
                      void act(() => cancelRegistration(authFetch, s.id))
                    }
                  >
                    Cancel registration
                  </Button>
                )}
                <Button size="small" onClick={() => setOpenId(s.id)}>
                  Details
                </Button>
              </Stack>
            </li>
          ))}
        </Stack>
      )}
      {openId && (
        <SessionDetail
          sessionId={openId}
          onChanged={() => void load()}
          onClose={() => setOpenId(null)}
        />
      )}
    </Box>
  );
}
