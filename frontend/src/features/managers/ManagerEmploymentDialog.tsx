import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  listDesignationOptions,
  type DesignationRow,
} from "../designations/designationsApi";
import { formatDate, todayIso } from "../teachers/formatters";
import {
  getManager,
  recordManagerDesignation,
  updateManagerEmployment,
  type ManagerSummary,
} from "./managersApi";

interface Props {
  manager: ManagerSummary;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * A Manager's designation (a new dated row; earlier ones stay in the history), employee id, joining date and,
 * while the Manager is inactive, exit date. Shown only to those who hold DESIGNATIONS EDIT.
 */
export function ManagerEmploymentDialog({ manager, onClose, onSaved }: Props) {
  const { authFetch } = useAuth();
  const employment = manager.employment;
  const [current, setCurrent] = useState<ManagerSummary>(manager);
  const [options, setOptions] = useState<DesignationRow[]>([]);
  const [designationId, setDesignationId] = useState(
    employment?.designation?.id ?? "",
  );
  const [effectiveOn, setEffectiveOn] = useState(todayIso());
  const [employeeId, setEmployeeId] = useState(employment?.employeeId ?? "");
  const [joiningDate, setJoiningDate] = useState(employment?.joiningDate ?? "");
  const [exitDate, setExitDate] = useState(employment?.exitDate ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [choices, detail] = await Promise.all([
        listDesignationOptions(authFetch, "MANAGER"),
        getManager(authFetch, manager.id),
      ]);
      if (cancelled) return;
      if (choices.ok) setOptions(choices.data);
      else setError(choices.reason);
      if (detail.ok) setCurrent(detail.data);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, manager.id]);

  const heldId = employment?.designation?.id ?? "";
  const heldRetired = employment?.designation?.retired ?? false;
  const choices =
    heldRetired && employment?.designation
      ? [
          ...options,
          {
            id: employment.designation.id,
            name: `${employment.designation.name} (retired)`,
          } as DesignationRow,
        ]
      : options;

  const save = async () => {
    setError(null);
    setSaving(true);
    const employmentChanged =
      (employeeId.trim() || null) !== (employment?.employeeId ?? null) ||
      (joiningDate || null) !== (employment?.joiningDate ?? null) ||
      (exitDate || null) !== (employment?.exitDate ?? null);
    if (employmentChanged) {
      const result = await updateManagerEmployment(authFetch, current, {
        employeeId: employeeId.trim() || null,
        joiningDate: joiningDate || null,
        exitDate: manager.active ? null : exitDate || null,
      });
      if (!result.ok) {
        setSaving(false);
        setError(result.reason);
        return;
      }
    }
    if (designationId && designationId !== heldId) {
      const result = await recordManagerDesignation(
        authFetch,
        manager.id,
        designationId,
        effectiveOn,
      );
      if (!result.ok) {
        setSaving(false);
        setError(result.reason);
        return;
      }
    }
    setSaving(false);
    onSaved();
  };

  const history = current.employment?.history ?? [];

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Employment details: {manager.displayName}</DialogTitle>
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
          helperText="A designation can be changed but not removed."
        >
          {choices.map((d) => (
            <MenuItem key={d.id} value={d.id}>
              {d.name}
            </MenuItem>
          ))}
        </TextField>
        {designationId && designationId !== heldId && (
          <TextField
            fullWidth
            type="date"
            label="Effective from"
            sx={{ mt: 2 }}
            value={effectiveOn}
            onChange={(e) => setEffectiveOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        )}
        <TextField
          fullWidth
          label="Employee id"
          sx={{ mt: 2 }}
          value={employeeId}
          onChange={(e) => setEmployeeId(e.target.value)}
          helperText="Optional. 1 to 20 letters, digits or hyphens; unique across Managers and Teachers."
        />
        <TextField
          fullWidth
          type="date"
          label="Joining date"
          sx={{ mt: 2 }}
          value={joiningDate}
          onChange={(e) => setJoiningDate(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        {!manager.active && (
          <TextField
            fullWidth
            type="date"
            label="Exit date"
            sx={{ mt: 2 }}
            value={exitDate}
            onChange={(e) => setExitDate(e.target.value)}
            helperText="The last day the Manager is paid; at most 90 days after today."
            slotProps={{ inputLabel: { shrink: true } }}
          />
        )}
        {history.length > 0 && (
          <>
            <Typography variant="subtitle2" sx={{ mt: 2 }}>
              Designation history
            </Typography>
            <List dense>
              {history.map((row, i) => (
                <ListItem key={`${row.designationId}-${i}`} disableGutters>
                  <ListItemText
                    primary={row.name}
                    secondary={`from ${formatDate(row.effectiveOn)}`}
                  />
                </ListItem>
              ))}
            </List>
          </>
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
