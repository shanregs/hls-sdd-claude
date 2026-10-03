import { useForm } from "react-hook-form";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { addPlace, editPlace, type PlaceSummary } from "./zonesApi";

interface PlaceDialogProps {
  zoneId: string;
  /** The Place being edited, or undefined to add a new one. */
  place?: PlaceSummary;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  name: string;
  pinCode: string;
}

/** Add or edit a Place (FR-002): name plus a six-digit PIN code. */
export function PlaceDialog({
  zoneId,
  place,
  onClose,
  onSaved,
}: PlaceDialogProps) {
  const { authFetch } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: { name: place?.name ?? "", pinCode: place?.pinCode ?? "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    const result = place
      ? await editPlace(authFetch, place, values.name, values.pinCode)
      : await addPlace(authFetch, zoneId, values.name, values.pinCode);
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onSaved();
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>{place ? "Edit place" : "Add place"}</DialogTitle>
      <form onSubmit={onSubmit} noValidate>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {errors.root && (
              <Alert severity="error" role="alert">
                {errors.root.message}
              </Alert>
            )}
            <TextField
              label="Place name"
              required
              {...register("name", {
                validate: (v) =>
                  v.trim().length > 0 || "Place name is required.",
              })}
              error={Boolean(errors.name)}
              helperText={errors.name?.message}
            />
            <TextField
              label="PIN code"
              required
              slotProps={{ htmlInput: { inputMode: "numeric", maxLength: 6 } }}
              {...register("pinCode", {
                validate: (v) =>
                  /^[0-9]{6}$/.test(v.trim()) || "PIN code must be six digits.",
              })}
              error={Boolean(errors.pinCode)}
              helperText={errors.pinCode?.message}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            {place ? "Save" : "Add"}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
