import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  assignSchoolManager,
  listManagers,
  type ManagerSummary,
} from "../managers/managersApi";
import type { SchoolSummary } from "./schoolsApi";

interface AssignSchoolManagerDialogProps {
  school: SchoolSummary;
  onClose: () => void;
  onSaved: () => void;
}

/** Chooses the School's Manager from the Managers who cover the School's Zone (FR-009); the server
 * re-checks the rule and refuses a non-covering Manager. */
export function AssignSchoolManagerDialog({
  school,
  onClose,
  onSaved,
}: AssignSchoolManagerDialogProps) {
  const { authFetch } = useAuth();
  const [managers, setManagers] = useState<ManagerSummary[]>([]);
  const [managerId, setManagerId] = useState(school.manager?.id ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listManagers(authFetch, "", 0, 100);
      if (cancelled) return;
      if (result.ok) {
        setManagers(
          result.data.content.filter(
            (m) => m.active && m.zones.some((z) => z.id === school.zone.id),
          ),
        );
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, school.zone.id]);

  const save = async () => {
    setSaving(true);
    const result = await assignSchoolManager(
      authFetch,
      school.id,
      managerId || null,
    );
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Manager for {school.name}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} role="alert">
            {error}
          </Alert>
        )}
        {managers.length === 0 && !error && (
          <Alert severity="info" sx={{ mb: 2 }}>
            No active manager covers {school.zone.name} yet. Assign a manager to
            this zone first.
          </Alert>
        )}
        <TextField
          select
          fullWidth
          label="Manager"
          sx={{ mt: 1 }}
          value={managerId}
          onChange={(e) => {
            setManagerId(e.target.value);
            setError(null);
          }}
        >
          <MenuItem value="">No manager</MenuItem>
          {managers.map((m) => (
            <MenuItem key={m.id} value={m.id}>
              {m.displayName}
            </MenuItem>
          ))}
        </TextField>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
