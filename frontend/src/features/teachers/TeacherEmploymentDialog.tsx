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
  listDesignationOptions,
  type DesignationRow,
} from "../designations/designationsApi";
import { updateTeacherEmployment, type TeacherSummary } from "./teachersApi";

interface Props {
  teacher: TeacherSummary;
  onClose: () => void;
  onSaved: () => void;
}

/** A Teacher's designation and optional employee id; shown only to those who hold DESIGNATIONS EDIT. */
export function TeacherEmploymentDialog({ teacher, onClose, onSaved }: Props) {
  const { authFetch } = useAuth();
  const employment = teacher.employment;
  const [options, setOptions] = useState<DesignationRow[]>([]);
  const [designationId, setDesignationId] = useState(
    employment?.designation?.id ?? "",
  );
  const [employeeId, setEmployeeId] = useState(employment?.employeeId ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listDesignationOptions(authFetch, "TEACHER");
      if (cancelled) return;
      if (result.ok) setOptions(result.data);
      else setError(result.reason);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  // a retired designation the Teacher already holds stays selectable so it is not lost by accident
  const held = employment?.designation;
  const choices =
    held && held.retired && !options.some((o) => o.id === held.id)
      ? [
          ...options,
          { id: held.id, name: `${held.name} (retired)` } as DesignationRow,
        ]
      : options;

  const save = async () => {
    setSaving(true);
    const result = await updateTeacherEmployment(authFetch, teacher, {
      designationId: designationId || null,
      employeeId: employeeId.trim() || null,
    });
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Employment details: {teacher.name}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} role="alert">
            {error}
          </Alert>
        )}
        <TextField
          select
          fullWidth
          label="Designation"
          sx={{ mt: 1 }}
          value={designationId}
          onChange={(e) => setDesignationId(e.target.value)}
        >
          <MenuItem value="">None</MenuItem>
          {choices.map((d) => (
            <MenuItem key={d.id} value={d.id}>
              {d.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          fullWidth
          label="Employee id"
          sx={{ mt: 2 }}
          value={employeeId}
          onChange={(e) => setEmployeeId(e.target.value)}
          helperText="Optional. 1 to 20 letters, digits or hyphens; unique across Managers and Teachers."
        />
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
