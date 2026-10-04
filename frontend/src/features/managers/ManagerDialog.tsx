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
  createManager,
  listCandidates,
  type ManagerCandidate,
} from "./managersApi";

interface ManagerDialogProps {
  onClose: () => void;
  onSaved: () => void;
}

/** Creates a Manager record for an active user who holds the Manager role (FR-007). */
export function ManagerDialog({ onClose, onSaved }: ManagerDialogProps) {
  const { authFetch } = useAuth();
  const [candidates, setCandidates] = useState<ManagerCandidate[] | null>(null);
  const [userId, setUserId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listCandidates(authFetch);
      if (cancelled) return;
      if (result.ok) {
        setCandidates(result.data);
      } else {
        setCandidates([]);
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const save = async () => {
    if (!userId) {
      setError("Choose a user.");
      return;
    }
    setSaving(true);
    const result = await createManager(authFetch, userId);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Create manager</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} role="alert">
            {error}
          </Alert>
        )}
        {candidates !== null && candidates.length === 0 && !error && (
          <Alert severity="info" sx={{ mb: 2 }}>
            No user is waiting for a manager record. First create a user with
            the Manager role in SYSTEM → User Management, then come back here.
          </Alert>
        )}
        <TextField
          select
          fullWidth
          required
          label="User"
          sx={{ mt: 1 }}
          value={userId}
          onChange={(e) => {
            setUserId(e.target.value);
            setError(null);
          }}
        >
          {(candidates ?? []).map((c) => (
            <MenuItem key={c.userId} value={c.userId}>
              {c.displayName} ({c.phone})
            </MenuItem>
          ))}
        </TextField>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}
