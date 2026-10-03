import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import type { PlaceSummary } from "../zones/zonesApi";
import { PlacePicker } from "./PlacePicker";
import { changeSchoolPlace, type SchoolSummary } from "./schoolsApi";

interface ChangePlaceDialogProps {
  school: SchoolSummary;
  onClose: () => void;
  onSaved: () => void;
}

/** Moves a School to another Place (FR-004); the server refuses a move that would strand its Manager. */
export function ChangePlaceDialog({
  school,
  onClose,
  onSaved,
}: ChangePlaceDialogProps) {
  const { authFetch } = useAuth();
  const [place, setPlace] = useState<PlaceSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    if (!place) {
      setError("Choose the new place.");
      return;
    }
    setSaving(true);
    const result = await changeSchoolPlace(authFetch, school, place.id);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Move {school.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2">
            Currently in {school.place.name} ({school.zone.name}).
          </Typography>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <PlacePicker value={place} onChange={setPlace} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>
          Move
        </Button>
      </DialogActions>
    </Dialog>
  );
}
