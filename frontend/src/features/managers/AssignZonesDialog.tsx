import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  FormGroup,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { listZones, type ZoneSummary } from "../zones/zonesApi";
import { setManagerZones, type ManagerSummary } from "./managersApi";

interface AssignZonesDialogProps {
  manager: ManagerSummary;
  onClose: () => void;
  onSaved: () => void;
}

/** Replaces the Manager's Zone set (FR-008); the server refuses dropping a Zone where they still
 * have Schools and names those Schools. */
export function AssignZonesDialog({
  manager,
  onClose,
  onSaved,
}: AssignZonesDialogProps) {
  const { authFetch } = useAuth();
  const [zones, setZones] = useState<ZoneSummary[]>([]);
  const [selected, setSelected] = useState<Set<string>>(
    new Set(manager.zones.map((z) => z.id)),
  );
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listZones(authFetch, "", 0, 100);
      if (cancelled) return;
      if (result.ok) {
        setZones(result.data.content);
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const toggle = (id: string, checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const save = async () => {
    setSaving(true);
    const result = await setManagerZones(authFetch, manager, [...selected]);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Zones for {manager.displayName}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} role="alert">
            {error}
          </Alert>
        )}
        <FormGroup>
          {zones.map((zone) => (
            <FormControlLabel
              key={zone.id}
              label={zone.name}
              control={
                <Checkbox
                  checked={selected.has(zone.id)}
                  onChange={(e) => toggle(zone.id, e.target.checked)}
                />
              }
            />
          ))}
        </FormGroup>
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
