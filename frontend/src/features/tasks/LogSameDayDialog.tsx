import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormLabel,
  MenuItem,
  Radio,
  RadioGroup,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { logSameDayTask, type TypeOption } from "./taskApi";

const MIN_NOTE = 10;
const MAX_NOTE = 2000;

interface Props {
  types: TypeOption[];
  onClose: () => void;
  onLogged: () => void;
}

/**
 * A Manager logs their own work for today (spec 040 US3): saved as In progress, or Completed and accepted in one step.
 * There is no assignee and no date to choose: it is for the signed-in Manager, for today.
 */
export function LogSameDayDialog({ types, onClose, onLogged }: Props) {
  const { authFetch } = useAuth();
  const [type, setType] = useState("SCHOOL_VISIT");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [place, setPlace] = useState("");
  const [status, setStatus] = useState<"IN_PROGRESS" | "COMPLETED">(
    "COMPLETED",
  );
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const noteProblem =
    status === "COMPLETED" && note.trim().length < MIN_NOTE
      ? `Write at least ${MIN_NOTE} characters about what was done.`
      : null;
  const ready = title.trim() !== "" && noteProblem === null;

  const save = async () => {
    setBusy(true);
    setError(null);
    const result = await logSameDayTask(authFetch, {
      type,
      title: title.trim(),
      description: description.trim() || null,
      status,
      completionNote: status === "COMPLETED" ? note.trim() : null,
      place: place.trim() || null,
    });
    setBusy(false);
    if (result.ok) onLogged();
    else setError(result.reason);
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="log-same-day-title"
    >
      <DialogTitle id="log-same-day-title">Log a task for today</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Alert severity="info">
            This is for you, for today. It is accepted at once and does not wait
            for verification.
          </Alert>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField
            select
            label="Type"
            value={type}
            onChange={(e) => setType(e.target.value)}
          >
            {types.map((t) => (
              <MenuItem key={t.value} value={t.value}>
                {t.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            required
            label="Title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={2}
          />
          <TextField
            label="Place"
            value={place}
            onChange={(e) => setPlace(e.target.value)}
          />
          <FormControl>
            <FormLabel id="log-same-day-status">Save as</FormLabel>
            <RadioGroup
              row
              aria-labelledby="log-same-day-status"
              value={status}
              onChange={(e) =>
                setStatus(e.target.value as "IN_PROGRESS" | "COMPLETED")
              }
            >
              <FormControlLabel
                value="COMPLETED"
                control={<Radio />}
                label="Completed"
              />
              <FormControlLabel
                value="IN_PROGRESS"
                control={<Radio />}
                label="In progress"
              />
            </RadioGroup>
          </FormControl>
          {status === "COMPLETED" && (
            <TextField
              required
              label="What was done"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              multiline
              minRows={2}
              error={note !== "" && noteProblem !== null}
              helperText={
                note !== "" && noteProblem !== null
                  ? noteProblem
                  : `${note.length} of ${MAX_NOTE}`
              }
              slotProps={{ htmlInput: { maxLength: MAX_NOTE } }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={!ready || busy}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
