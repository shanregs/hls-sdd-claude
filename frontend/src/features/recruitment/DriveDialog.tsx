import { useEffect, useState } from "react";
import {
  Alert,
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  listColleges,
  listInterviewers,
  scheduleDrive,
  updateDrive,
  type College,
  type Drive,
  type PersonRef,
} from "./recruitmentApi";

interface DriveDialogProps {
  /** The drive being changed; absent when scheduling a new one. */
  drive?: Drive;
  onClose: () => void;
  onSaved: () => void;
}

/** Schedule a campus drive, or change the dates, venue and interviewers of one (spec 016 US1). */
export function DriveDialog({ drive, onClose, onSaved }: DriveDialogProps) {
  const { authFetch } = useAuth();
  const [colleges, setColleges] = useState<College[]>([]);
  const [people, setPeople] = useState<PersonRef[]>([]);
  const [collegeId, setCollegeId] = useState(drive?.college.id ?? "");
  const [dates, setDates] = useState<string[]>(drive?.dates ?? [""]);
  const [venue, setVenue] = useState(drive?.venue ?? "");
  const [season, setSeason] = useState(drive?.season ?? "");
  const [interviewers, setInterviewers] = useState<PersonRef[]>(
    drive?.interviewers ?? [],
  );
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void (async () => {
      const [c, p] = await Promise.all([
        listColleges(authFetch),
        listInterviewers(authFetch),
      ]);
      if (c.ok) setColleges(c.data);
      if (p.ok) setPeople(p.data);
    })();
  }, [authFetch]);

  const setDate = (index: number, value: string) =>
    setDates((all) => all.map((d, i) => (i === index ? value : d)));

  const save = async () => {
    const chosen = dates.filter(Boolean);
    if (!collegeId) {
      setError("Choose a college.");
      return;
    }
    if (chosen.length === 0) {
      setError("Add at least one date.");
      return;
    }
    setSaving(true);
    const input = {
      collegeId,
      dates: chosen,
      venue: venue || undefined,
      season: season || undefined,
      interviewerUserIds: interviewers.map((p) => p.userId),
      version: drive?.version,
    };
    const result = drive
      ? await updateDrive(authFetch, drive.id, input)
      : await scheduleDrive(authFetch, input);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="drive-title"
    >
      <DialogTitle id="drive-title">
        {drive ? "Change drive" : "Schedule a drive"}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField
            select
            required
            label="College"
            value={collegeId}
            onChange={(e) => setCollegeId(e.target.value)}
            disabled={!!drive}
          >
            {colleges.map((c) => (
              <MenuItem key={c.id} value={c.id}>
                {c.name}, {c.city}
              </MenuItem>
            ))}
          </TextField>
          {dates.map((d, index) => (
            <Stack
              key={index}
              direction="row"
              spacing={1}
              sx={{ alignItems: "center" }}
            >
              <TextField
                type="date"
                label={`Date ${index + 1}`}
                value={d}
                onChange={(e) => setDate(index, e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
                fullWidth
              />
              {dates.length > 1 && (
                <IconButton
                  aria-label={`Remove date ${index + 1}`}
                  onClick={() =>
                    setDates((all) => all.filter((_x, i) => i !== index))
                  }
                >
                  ×
                </IconButton>
              )}
            </Stack>
          ))}
          <Button
            onClick={() => setDates((all) => [...all, ""])}
            sx={{ alignSelf: "flex-start" }}
          >
            Add another date
          </Button>
          <TextField
            label="Venue"
            value={venue}
            onChange={(e) => setVenue(e.target.value)}
          />
          <TextField
            label="Season"
            helperText="For example 2026-27; used to group the dashboard."
            value={season}
            onChange={(e) => setSeason(e.target.value)}
          />
          <Autocomplete
            multiple
            options={people}
            value={interviewers}
            getOptionLabel={(p) => p.name}
            isOptionEqualToValue={(a, b) => a.userId === b.userId}
            onChange={(_e, value) => setInterviewers(value)}
            renderInput={(params) => (
              <TextField {...params} label="Interviewers" />
            )}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => void save()}
          disabled={saving}
        >
          {drive ? "Save" : "Schedule"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
