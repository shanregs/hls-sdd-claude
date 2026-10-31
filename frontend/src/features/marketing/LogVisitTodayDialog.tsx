import { useEffect, useState } from "react";
import {
  Alert,
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  ACTIVITY_LABEL,
  listProspects,
  logVisitToday,
  type ActivityType,
  type ProspectRow,
} from "./marketingApi";

interface Props {
  onClose: () => void;
  onSaved: () => void;
}

/**
 * A Manager logs a visit, call or meeting that happened today with no plan (spec 040 US3). It is saved already Completed,
 * so an outcome is required. The date is not chosen: it is today.
 */
export function LogVisitTodayDialog({ onClose, onSaved }: Props) {
  const { authFetch } = useAuth();
  const [prospects, setProspects] = useState<ProspectRow[]>([]);
  const [search, setSearch] = useState("");
  const [prospectId, setProspectId] = useState("");
  const [type, setType] = useState<ActivityType>("VISIT");
  const [outcome, setOutcome] = useState("");
  const [notes, setNotes] = useState("");
  const [followUpOn, setFollowUpOn] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      void (async () => {
        const p = await listProspects(authFetch, {
          size: 50,
          query: search.trim() || undefined,
        });
        if (p.ok) setProspects(p.data.content);
      })();
    }, 250);
    return () => clearTimeout(timer);
  }, [authFetch, search]);

  const ready = prospectId !== "" && outcome.trim() !== "";

  const save = async () => {
    setSaving(true);
    setError(null);
    const result = await logVisitToday(authFetch, {
      prospectId,
      type,
      outcome: outcome.trim(),
      notes: notes.trim() || undefined,
      followUpOn: followUpOn || undefined,
    });
    setSaving(false);
    if (result.ok) onSaved();
    else setError(result.reason);
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="log-visit-today-title"
    >
      <DialogTitle id="log-visit-today-title">
        Log a visit for today
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Alert severity="info">
            This is saved as completed, for today. Nobody needs to approve it.
          </Alert>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <Autocomplete
            options={prospects}
            getOptionLabel={(p) => p.name}
            onInputChange={(_, value) => setSearch(value)}
            onChange={(_, p) => setProspectId(p?.id ?? "")}
            renderInput={(params) => (
              <TextField {...params} required label="Prospect" size="small" />
            )}
          />
          <TextField
            select
            size="small"
            label="Kind"
            value={type}
            onChange={(e) => setType(e.target.value as ActivityType)}
          >
            {(Object.keys(ACTIVITY_LABEL) as ActivityType[]).map((t) => (
              <MenuItem key={t} value={t}>
                {ACTIVITY_LABEL[t]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            required
            multiline
            minRows={2}
            size="small"
            label="Outcome"
            helperText="Who was met, what was discussed and the next action."
            value={outcome}
            onChange={(e) => setOutcome(e.target.value)}
          />
          <TextField
            multiline
            minRows={2}
            size="small"
            label="Notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
          <TextField
            size="small"
            type="date"
            label="Follow-up on"
            value={followUpOn}
            onChange={(e) => setFollowUpOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={!ready || saving}>
          Save visit
        </Button>
      </DialogActions>
    </Dialog>
  );
}
