import { useForm } from "react-hook-form";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { createZone, renameZone, type ZoneSummary } from "./zonesApi";

interface ZoneDialogProps {
  /** The Zone being renamed, or undefined to create a new one. */
  zone?: ZoneSummary;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  name: string;
}

/** Create or rename a Zone (FR-001); server rejections (duplicate name, stale version) show inline. */
export function ZoneDialog({ zone, onClose, onSaved }: ZoneDialogProps) {
  const { authFetch } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ defaultValues: { name: zone?.name ?? "" } });

  const onSubmit = handleSubmit(async (values) => {
    const result = zone
      ? await renameZone(authFetch, zone, values.name)
      : await createZone(authFetch, values.name);
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onSaved();
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>{zone ? "Rename zone" : "Create zone"}</DialogTitle>
      <form onSubmit={onSubmit} noValidate>
        <DialogContent>
          {errors.root && (
            <Alert severity="error" sx={{ mb: 2 }} role="alert">
              {errors.root.message}
            </Alert>
          )}
          <TextField
            label="Zone name"
            required
            fullWidth
            autoFocus
            {...register("name", {
              validate: (v) => v.trim().length > 0 || "Zone name is required.",
            })}
            error={Boolean(errors.name)}
            helperText={errors.name?.message}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            {zone ? "Save" : "Create"}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
