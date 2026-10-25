import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  createSession,
  updateSession,
  type SessionBody,
  type SessionType,
  type SessionView,
} from "./trainingApi";

interface Props {
  /** The session being edited or moved; absent when creating. */
  session?: SessionView;
  /** True when the dialog moves the date or time and so needs a reason. */
  move?: boolean;
  onSaved: (session: SessionView) => void;
  onCancel: () => void;
}

const HTTPS = /^https:\/\/\S+$/i;

/** Create, edit or move a training session (Admin, Director). Validation mirrors the server so the message appears before saving. */
export function SessionDialog({ session, move = false, onSaved, onCancel }: Props) {
  const { authFetch } = useAuth();
  const [type, setType] = useState<SessionType>(session?.type ?? "WEEKEND");
  const [topic, setTopic] = useState(session?.topic ?? "");
  const [date, setDate] = useState(session?.date ?? "");
  const [startsAt, setStartsAt] = useState(session?.startsAt?.slice(0, 5) ?? "10:00");
  const [endsAt, setEndsAt] = useState(session?.endsAt?.slice(0, 5) ?? "13:00");
  const [trainerName, setTrainerName] = useState(session?.trainerName ?? "");
  const [trainerContact, setTrainerContact] = useState(session?.trainerContact ?? "");
  const [venueKind, setVenueKind] = useState(session?.venueKind ?? "PHYSICAL");
  const [venue, setVenue] = useState(session?.venue ?? "");
  const [seatLimit, setSeatLimit] = useState(
    session?.seatLimit != null ? String(session.seatLimit) : "",
  );
  const [description, setDescription] = useState(session?.description ?? "");
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const moved =
    move ||
    (session !== undefined &&
      (date !== session.date ||
        startsAt !== session.startsAt.slice(0, 5) ||
        endsAt !== session.endsAt.slice(0, 5)));

  function validate(): Record<string, string> {
    const found: Record<string, string> = {};
    if (!topic.trim()) found.topic = "Enter the topic.";
    if (!date) found.date = "Choose the date.";
    if (!startsAt) found.startsAt = "Enter the start time.";
    if (!endsAt) found.endsAt = "Enter the end time.";
    if (startsAt && endsAt && endsAt <= startsAt) {
      found.endsAt = "The end time must be after the start time.";
    }
    if (seatLimit && (!/^\d+$/.test(seatLimit) || Number(seatLimit) < 1)) {
      found.seatLimit = "Enter a whole number of seats, or leave it empty.";
    }
    if (venueKind === "ONLINE" && venue && !HTTPS.test(venue)) {
      found.venue = "The meeting link must start with https://";
    }
    if (session && moved && !reason.trim()) {
      found.reason = "Enter the reason for moving the session.";
    }
    return found;
  }

  async function save(publish: boolean) {
    const found = validate();
    setErrors(found);
    setFailure(null);
    if (Object.keys(found).length > 0) return;
    const body: SessionBody = {
      type,
      topic: topic.trim(),
      date,
      startsAt,
      endsAt,
      trainerName: trainerName.trim() || undefined,
      trainerContact: trainerContact.trim() || undefined,
      venueKind,
      venue: venue.trim() || undefined,
      seatLimit: seatLimit ? Number(seatLimit) : null,
      description: description.trim() || undefined,
      reason: moved ? reason.trim() : undefined,
      publish: session ? undefined : publish,
      version: session?.version,
    };
    setBusy(true);
    const result = session
      ? await updateSession(authFetch, session.id, body)
      : await createSession(authFetch, body);
    setBusy(false);
    if (result.ok) {
      onSaved(result.data);
    } else {
      setFailure(result.reason);
    }
  }

  const title = !session
    ? "New training session"
    : move
      ? "Move this session"
      : "Edit this session";

  return (
    <Dialog open onClose={onCancel} fullWidth maxWidth="sm" aria-labelledby="session-dialog-title">
      <DialogTitle id="session-dialog-title">{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {failure && <Alert severity="error">{failure}</Alert>}
          <TextField
            select
            label="Type"
            value={type}
            onChange={(e) => setType(e.target.value as SessionType)}
            disabled={Boolean(session)}
            slotProps={{ select: { native: true } }}
          >
            <option value="WEEKEND">Weekend refresher</option>
            <option value="MONTHLY">Monthly training</option>
          </TextField>
          <TextField
            label="Topic"
            value={topic}
            onChange={(e) => setTopic(e.target.value)}
            error={Boolean(errors.topic)}
            helperText={errors.topic}
            slotProps={{ htmlInput: { maxLength: 200 } }}
            required
          />
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              label="Date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              error={Boolean(errors.date)}
              helperText={errors.date}
              slotProps={{ inputLabel: { shrink: true } }}
              required
              fullWidth
            />
            <TextField
              label="Starts at"
              type="time"
              value={startsAt}
              onChange={(e) => setStartsAt(e.target.value)}
              error={Boolean(errors.startsAt)}
              helperText={errors.startsAt}
              slotProps={{ inputLabel: { shrink: true } }}
              required
            />
            <TextField
              label="Ends at"
              type="time"
              value={endsAt}
              onChange={(e) => setEndsAt(e.target.value)}
              error={Boolean(errors.endsAt)}
              helperText={errors.endsAt}
              slotProps={{ inputLabel: { shrink: true } }}
              required
            />
          </Stack>
          {session && moved && (
            <TextField
              label="Reason for moving"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              error={Boolean(errors.reason)}
              helperText={errors.reason ?? "People registered keep their place and are told."}
              slotProps={{ htmlInput: { maxLength: 300 } }}
              required
            />
          )}
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              label="Trainer"
              value={trainerName}
              onChange={(e) => setTrainerName(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 120 } }}
              fullWidth
            />
            <TextField
              label="Trainer contact"
              value={trainerContact}
              onChange={(e) => setTrainerContact(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 160 } }}
              fullWidth
            />
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              select
              label="Venue kind"
              value={venueKind}
              onChange={(e) => setVenueKind(e.target.value)}
              slotProps={{ select: { native: true } }}
            >
              <option value="PHYSICAL">In person</option>
              <option value="ONLINE">Online</option>
            </TextField>
            <TextField
              label={venueKind === "ONLINE" ? "Meeting link" : "Venue"}
              value={venue}
              onChange={(e) => setVenue(e.target.value)}
              error={Boolean(errors.venue)}
              helperText={errors.venue}
              slotProps={{ htmlInput: { maxLength: 300 } }}
              fullWidth
            />
          </Stack>
          <TextField
            label="Seat limit"
            value={seatLimit}
            onChange={(e) => setSeatLimit(e.target.value)}
            error={Boolean(errors.seatLimit)}
            helperText={errors.seatLimit ?? "Leave empty for no limit."}
            slotProps={{ htmlInput: { inputMode: "numeric" } }}
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={2}
            slotProps={{ htmlInput: { maxLength: 1000 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        {session ? (
          <Button variant="contained" onClick={() => void save(false)} disabled={busy}>
            Save
          </Button>
        ) : (
          <>
            <Button onClick={() => void save(false)} disabled={busy}>
              Save as draft
            </Button>
            <Button variant="contained" onClick={() => void save(true)} disabled={busy}>
              Save and publish
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
}
