import { Controller, useForm } from "react-hook-form";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Switch,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import type { MatrixRow } from "./types";

interface EditGrantDialogProps {
  row: MatrixRow;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  granted: boolean;
}

/** Confirms and applies a single grant edit (FR-003/FR-004): surfaces the backend's 409
 * last-manager-safeguard rejection inline rather than a generic error. */
export function EditGrantDialog({
  row,
  onClose,
  onSaved,
}: EditGrantDialogProps) {
  const { authFetch } = useAuth();
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ defaultValues: { granted: row.granted } });

  const onSubmit = handleSubmit(async (values) => {
    try {
      const response = await authFetch(
        `/api/v1/identity/permission-matrix/${row.role}/${row.module}/${row.action}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ granted: values.granted }),
        },
      );
      if (response.status === 409) {
        const body = (await response.json()) as { reason: string };
        setError("root", { message: body.reason });
        return;
      }
      if (!response.ok) {
        setError("root", { message: "Could not save this change." });
        return;
      }
      onSaved();
    } catch {
      setError("root", { message: "Could not save this change." });
    }
  });

  return (
    <Dialog open onClose={onClose}>
      <DialogTitle>
        {row.role} &middot; {row.module} &middot; {row.action}
      </DialogTitle>
      <form onSubmit={onSubmit}>
        <DialogContent>
          {errors.root && (
            <Alert severity="error" sx={{ mb: 2 }} role="alert">
              {errors.root.message}
            </Alert>
          )}
          <Controller
            name="granted"
            control={control}
            render={({ field }) => (
              <FormControlLabel
                control={
                  <Switch
                    checked={field.value}
                    onChange={(e) => field.onChange(e.target.checked)}
                  />
                }
                label="Granted"
              />
            )}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Save
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
