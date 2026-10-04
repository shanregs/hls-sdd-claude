import { useForm } from "react-hook-form";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { reopenMonth } from "./attendanceApi";
import { monthLabel } from "./monthUtils";

interface ReopenDialogProps {
  teacherId: string;
  teacherName: string;
  month: string;
  onClose: () => void;
  onReopened: () => void;
}

interface FormValues {
  reason: string;
}

/** Reopens one Teacher's locked month; a reason is required and recorded (spec 008 FR-018). */
export function ReopenDialog({
  teacherId,
  teacherName,
  month,
  onClose,
  onReopened,
}: ReopenDialogProps) {
  const { authFetch } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ defaultValues: { reason: "" } });

  const submit = handleSubmit(async (values) => {
    const result = await reopenMonth(
      authFetch,
      teacherId,
      month,
      values.reason.trim(),
    );
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onReopened();
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Reopen {monthLabel(month)}</DialogTitle>
      <form onSubmit={submit} noValidate>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2 }}>
            {teacherName}
          </Typography>
          {errors.root && (
            <Alert severity="error" role="alert" sx={{ mb: 2 }}>
              {errors.root.message}
            </Alert>
          )}
          <TextField
            label="Reason"
            required
            multiline
            minRows={2}
            fullWidth
            slotProps={{ htmlInput: { maxLength: 500 } }}
            error={!!errors.reason}
            helperText={errors.reason?.message}
            {...register("reason", {
              validate: (v) => v.trim().length > 0 || "Enter a reason.",
              maxLength: {
                value: 500,
                message: "The reason can be at most 500 characters.",
              },
            })}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Reopen
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
