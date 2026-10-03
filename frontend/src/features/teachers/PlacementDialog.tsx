import { useEffect, useState } from "react";
import {
  Alert,
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { listSchools, type SchoolSummary } from "../schools/schoolsApi";
import { formatDate, todayIso } from "./formatters";
import {
  cancelScheduledPlacement,
  placeTeacher,
  type TeacherSummary,
} from "./teachersApi";

interface PlacementDialogProps {
  teacher: TeacherSummary;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * Places or moves a Teacher with an effective date (FR-012). A future date schedules the move; a
 * scheduled move can be cancelled. The placement is interim: a later billing/contract feature
 * replaces it, and every place it is shown says so.
 */
export function PlacementDialog({
  teacher,
  onClose,
  onSaved,
}: PlacementDialogProps) {
  const { authFetch } = useAuth();
  const [schools, setSchools] = useState<SchoolSummary[]>([]);
  const [school, setSchool] = useState<SchoolSummary | null>(null);
  const [effectiveOn, setEffectiveOn] = useState(todayIso());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listSchools(authFetch, {
        query: "",
        zoneId: "",
        active: "true",
        page: 0,
        size: 100,
      });
      if (!cancelled && result.ok) setSchools(result.data.content);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const save = async () => {
    if (!school) {
      setError("Choose a school.");
      return;
    }
    setSaving(true);
    const result = await placeTeacher(
      authFetch,
      teacher.id,
      school.id,
      effectiveOn,
    );
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  const cancelScheduled = async () => {
    setSaving(true);
    const result = await cancelScheduledPlacement(authFetch, teacher.id);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>School placement for {teacher.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2">
            Interim placement (replaced later by the school contract).{" "}
            {teacher.school
              ? `Currently at ${teacher.school.name}.`
              : "Not placed in a school."}
          </Typography>
          {teacher.pendingPlacement && (
            <Alert
              severity="info"
              role="status"
              action={
                <Button
                  color="inherit"
                  size="small"
                  onClick={cancelScheduled}
                  disabled={saving}
                >
                  Cancel scheduled move
                </Button>
              }
            >
              Scheduled: {teacher.pendingPlacement.schoolName} from{" "}
              {formatDate(teacher.pendingPlacement.startsOn)}.
            </Alert>
          )}
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <Autocomplete
            options={schools}
            value={school}
            onChange={(_event, value) => {
              setSchool(value);
              setError(null);
            }}
            getOptionLabel={(option) => `${option.name} (${option.zone.name})`}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            renderInput={(params) => (
              <TextField {...params} label="School" required />
            )}
          />
          <TextField
            label="Effective date"
            type="date"
            value={effectiveOn}
            onChange={(e) => setEffectiveOn(e.target.value)}
            helperText="A future date schedules the move."
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>
          Save placement
        </Button>
      </DialogActions>
    </Dialog>
  );
}
