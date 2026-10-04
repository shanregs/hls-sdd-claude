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
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  linkTeacherAccount,
  listAccountCandidates,
  type AccountCandidate,
  type TeacherSummary,
} from "./teachersApi";

interface LinkAccountDialogProps {
  teacher: TeacherSummary;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * Links a Teacher record to a user account that holds the Teacher role (or unlinks it), so the
 * Teacher sees their own profile after signing in. The account itself is created in
 * SYSTEM -> User Management; a user account and a Teacher record are separate things.
 */
export function LinkAccountDialog({
  teacher,
  onClose,
  onSaved,
}: LinkAccountDialogProps) {
  const { authFetch } = useAuth();
  const [candidates, setCandidates] = useState<AccountCandidate[] | null>(null);
  const [userId, setUserId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listAccountCandidates(authFetch);
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

  const save = async (target: string | null) => {
    if (target === null && !teacher.userId) return;
    if (target !== null && !target) {
      setError("Choose an account.");
      return;
    }
    setSaving(true);
    const result = await linkTeacherAccount(authFetch, teacher.id, target);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Account for {teacher.name}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} role="alert">
            {error}
          </Alert>
        )}
        <Typography variant="body2" sx={{ mb: 2 }}>
          {teacher.userId
            ? "This teacher is linked to a user account. You can unlink it or link a different one."
            : "This teacher is not linked to a user account yet, so they cannot see their profile."}
        </Typography>
        {candidates !== null && candidates.length === 0 && !error && (
          <Alert severity="info" sx={{ mb: 2 }}>
            No free account with the Teacher role. First create a user with the
            Teacher role in SYSTEM → User Management, then come back here.
          </Alert>
        )}
        <TextField
          select
          fullWidth
          label="User account"
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
        {teacher.userId && (
          <Button color="error" onClick={() => save(null)} disabled={saving}>
            Unlink
          </Button>
        )}
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => save(userId)}
          disabled={saving}
        >
          Link account
        </Button>
      </DialogActions>
    </Dialog>
  );
}
