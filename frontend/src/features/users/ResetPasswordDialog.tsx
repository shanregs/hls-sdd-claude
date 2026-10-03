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
import { resetPassword, type UserSummary } from "./userManagementApi";

interface ResetPasswordDialogProps {
  user: UserSummary;
  /** True when the actor is resetting their own password (their session will end). */
  isSelf: boolean;
  onClose: () => void;
  onDone: () => void;
}

interface FormValues {
  newPassword: string;
}

/** Admin-triggered password reset (FR-006). The server enforces the same policy as spec 001's
 * self-service reset; its message is shown inline. The password is never kept after submit. */
export function ResetPasswordDialog({
  user,
  isSelf,
  onClose,
  onDone,
}: ResetPasswordDialogProps) {
  const { authFetch } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ defaultValues: { newPassword: "" } });

  const onSubmit = handleSubmit(async (values) => {
    const result = await resetPassword(authFetch, user.id, values.newPassword);
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onDone();
  });

  return (
    <Dialog open onClose={onClose}>
      <DialogTitle>Reset password for {user.displayName}</DialogTitle>
      <form onSubmit={onSubmit} noValidate>
        <DialogContent>
          {isSelf && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              This is your own account. Your current session will end after the
              reset.
            </Alert>
          )}
          {errors.root && (
            <Alert severity="error" sx={{ mb: 2 }} role="alert">
              {errors.root.message}
            </Alert>
          )}
          <TextField
            label="New password"
            type="password"
            autoComplete="new-password"
            required
            fullWidth
            {...register("newPassword", {
              validate: (v) =>
                v.length >= 10 || "Password must be at least 10 characters.",
            })}
            error={Boolean(errors.newPassword)}
            helperText={errors.newPassword?.message}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Reset password
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
