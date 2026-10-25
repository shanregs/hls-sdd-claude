import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import type { ApiResult } from "../common/masterDataApi";
import { formatDate, formatTime } from "./format";
import { SessionDialog } from "./SessionDialog";
import {
  STATUS_LABELS,
  TYPE_LABELS,
  cancelRegistration,
  cancelSession,
  deleteSession,
  getRoster,
  getSession,
  publishSession,
  registerForSession,
  type RegistrationRow,
  type SessionView,
} from "./trainingApi";

interface Props {
  sessionId: string;
  /** Called after any change so the list behind the dialog reloads. */
  onChanged: () => void;
  onClose: () => void;
}

/** One session: fields, holiday notice, roster, earlier dates, and the buttons the server's actions list allows. */
export function SessionDetail({ sessionId, onChanged, onClose }: Props) {
  const { authFetch } = useAuth();
  const [session, setSession] = useState<SessionView | null>(null);
  const [roster, setRoster] = useState<RegistrationRow[]>([]);
  const [failure, setFailure] = useState<string | null>(null);
  const [mode, setMode] = useState<"edit" | "move" | "cancel" | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const result = await getSession(authFetch, sessionId);
    if (!result.ok) {
      setFailure(result.reason);
      return;
    }
    setSession(result.data);
    if (result.data.actions.includes("REGISTER_TEACHER") || result.data.counts) {
      const rows = await getRoster(authFetch, sessionId);
      setRoster(rows.ok ? rows.data : []);
    }
  }, [authFetch, sessionId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- loads the session once opened
    void load();
  }, [load]);

  async function run<T>(call: () => Promise<ApiResult<T>>) {
    setFailure(null);
    const result = await call();
    if (!result.ok) {
      setFailure(result.reason);
      return false;
    }
    await load();
    onChanged();
    return true;
  }

  if (!session) {
    return (
      <Dialog open onClose={onClose} fullWidth maxWidth="sm">
        <DialogContent>
          {failure ? <Alert severity="error">{failure}</Alert> : "Loading..."}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }

  const can = (action: string) => session.actions.includes(action as never);
  const registered = roster.filter((r) => r.status === "REGISTERED");

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md" aria-labelledby="session-detail-title">
      <DialogTitle id="session-detail-title">{session.topic}</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          {failure && <Alert severity="error">{failure}</Alert>}
          {session.holidayNotice && (
            <Alert severity="info">This date is a holiday or non-working day.</Alert>
          )}
          {session.attendanceNotRecorded && (
            <Alert severity="warning">Attendance has not been recorded for this session.</Alert>
          )}
          {session.status === "CANCELLED" && session.cancelReason && (
            <Alert severity="warning">Cancelled: {session.cancelReason}</Alert>
          )}
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
            <Chip label={STATUS_LABELS[session.status]} />
            <Chip label={TYPE_LABELS[session.type]} variant="outlined" />
            {session.my?.status === "REQUIRED" && <Chip label="Required" color="warning" />}
            {session.my?.status === "REGISTERED" && <Chip label="Registered" color="success" />}
          </Stack>
          <Box component="dl" sx={{ m: 0, display: "grid", gridTemplateColumns: "max-content 1fr", gap: 0.5, columnGap: 2 }}>
            <dt>Date</dt>
            <dd>{formatDate(session.date)}</dd>
            <dt>Time</dt>
            <dd>
              {formatTime(session.startsAt)} to {formatTime(session.endsAt)}
            </dd>
            {session.trainerName && (
              <>
                <dt>Trainer</dt>
                <dd>
                  {session.trainerName}
                  {session.trainerContact ? ` (${session.trainerContact})` : ""}
                </dd>
              </>
            )}
            {session.venue && (
              <>
                <dt>{session.venueKind === "ONLINE" ? "Meeting link" : "Venue"}</dt>
                <dd>{session.venue}</dd>
              </>
            )}
            <dt>Seats</dt>
            <dd>
              {session.seatLimit == null
                ? `${session.registered} registered, no limit`
                : `${session.registered} of ${session.seatLimit} taken`}
            </dd>
          </Box>
          {session.description && <Typography>{session.description}</Typography>}
          {session.groupNotFullyAssigned != null && session.groupNotFullyAssigned > 0 && (
            <Alert severity="info">
              {session.groupNotFullyAssigned} people in the group are not assigned.
            </Alert>
          )}
          {session.earlierDates.length > 0 && (
            <Box>
              <Typography variant="subtitle2" component="h3">
                Earlier dates
              </Typography>
              <ul>
                {session.earlierDates.map((e, i) => (
                  <li key={i}>
                    {formatDate(e.date)}
                    {e.reason ? `, moved because: ${e.reason}` : ""}
                  </li>
                ))}
              </ul>
            </Box>
          )}
          {session.counts && (
            <Typography variant="body2">
              Expected {session.counts.expected}, attended {session.counts.attended}, absent{" "}
              {session.counts.absent}, excused {session.counts.excused}, walk-ins{" "}
              {session.counts.walkIns}.
            </Typography>
          )}
          {session.type === "WEEKEND" && roster.length > 0 && (
            <Box>
              <Divider sx={{ mb: 1 }} />
              <Typography variant="subtitle2" component="h3">
                Registered ({registered.length})
              </Typography>
              <ul>
                {registered.map((r) => (
                  <li key={r.teacherId}>
                    {r.name}
                    {can("EDIT") && (
                      <Button
                        size="small"
                        onClick={() =>
                          void run(() => cancelRegistration(authFetch, session.id, r.teacherId))
                        }
                      >
                        Cancel registration for {r.name}
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ flexWrap: "wrap" }}>
        {can("REGISTER") && (
          <Button variant="contained" onClick={() => void run(() => registerForSession(authFetch, session.id))}>
            Register
          </Button>
        )}
        {can("CANCEL_REGISTRATION") && (
          <Button onClick={() => void run(() => cancelRegistration(authFetch, session.id))}>
            Cancel my registration
          </Button>
        )}
        {session.my?.open === false && session.seatsLeft === 0 && session.my?.status == null && (
          <Typography color="text.secondary" sx={{ mr: 1 }}>
            No seats left.
          </Typography>
        )}
        {can("PUBLISH") && (
          <Button variant="contained" onClick={() => void run(() => publishSession(authFetch, session.id, session.version))}>
            Publish
          </Button>
        )}
        {can("EDIT") && <Button onClick={() => setMode("edit")}>Edit</Button>}
        {can("MOVE") && <Button onClick={() => setMode("move")}>Move</Button>}
        {can("CANCEL") && (
          <Button color="warning" onClick={() => setMode("cancel")}>
            Cancel session
          </Button>
        )}
        {can("DELETE") && (
          <Button
            color="error"
            onClick={async () => {
              if (await run(() => deleteSession(authFetch, session.id))) onClose();
            }}
          >
            Delete draft
          </Button>
        )}
        <Button onClick={onClose}>Close</Button>
      </DialogActions>

      {(mode === "edit" || mode === "move") && (
        <SessionDialog
          session={session}
          move={mode === "move"}
          onCancel={() => setMode(null)}
          onSaved={() => {
            setMode(null);
            void load();
            onChanged();
          }}
        />
      )}
      {mode === "cancel" && (
        <Dialog open onClose={() => setMode(null)} fullWidth maxWidth="xs">
          <DialogTitle>Cancel this session</DialogTitle>
          <DialogContent>
            <TextField
              autoFocus
              fullWidth
              margin="dense"
              label="Reason for cancelling"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              error={Boolean(reasonError)}
              helperText={reasonError ?? "Registered and assigned people are told."}
              slotProps={{ htmlInput: { maxLength: 300 } }}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setMode(null)}>Back</Button>
            <Button
              color="warning"
              variant="contained"
              onClick={async () => {
                if (!reason.trim()) {
                  setReasonError("Enter the reason for cancelling.");
                  return;
                }
                setReasonError(null);
                if (await run(() => cancelSession(authFetch, session.id, reason.trim(), session.version))) {
                  setMode(null);
                  setReason("");
                }
              }}
            >
              Cancel session
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Dialog>
  );
}
