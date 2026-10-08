import { useState } from "react";
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
  createDesignation,
  KIND_LABELS,
  updateDesignation,
  type DesignationKind,
  type DesignationRow,
} from "./designationsApi";

interface DesignationDialogProps {
  /** The designation being renamed, or undefined to add one. */
  designation?: DesignationRow;
  onClose: () => void;
  onSaved: () => void;
}

const MAX_NAME = 80;

/** Adds a designation (name and kind) or renames one; the kind of an existing one is not editable here. */
export function DesignationDialog({
  designation,
  onClose,
  onSaved,
}: DesignationDialogProps) {
  const { authFetch } = useAuth();
  const [name, setName] = useState(designation?.name ?? "");
  const [kind, setKind] = useState<DesignationKind>(
    designation?.kind ?? "TEACHER",
  );
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    const trimmed = name.trim();
    if (!trimmed) {
      setError("Enter a name.");
      return;
    }
    if (trimmed.length > MAX_NAME) {
      setError(`The name can be at most ${MAX_NAME} characters.`);
      return;
    }
    setSaving(true);
    const result = designation
      ? await updateDesignation(authFetch, designation, { name: trimmed })
      : await createDesignation(authFetch, trimmed, kind);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>
        {designation ? "Rename designation" : "Add designation"}
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} role="alert">
            {error}
          </Alert>
        )}
        <TextField
          autoFocus
          fullWidth
          required
          label="Name"
          sx={{ mt: 1 }}
          value={name}
          onChange={(e) => {
            setName(e.target.value);
            setError(null);
          }}
        />
        {!designation && (
          <TextField
            select
            fullWidth
            required
            label="For"
            sx={{ mt: 2 }}
            value={kind}
            onChange={(e) => setKind(e.target.value as DesignationKind)}
          >
            {(Object.keys(KIND_LABELS) as DesignationKind[]).map((k) => (
              <MenuItem key={k} value={k}>
                {KIND_LABELS[k]}
              </MenuItem>
            ))}
          </TextField>
        )}
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
