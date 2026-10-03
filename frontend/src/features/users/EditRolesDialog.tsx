import { Controller, useForm } from "react-hook-form";
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
  FormHelperText,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  ALL_ROLES,
  updateRoles,
  type Role,
  type UserSummary,
} from "./userManagementApi";

interface EditRolesDialogProps {
  user: UserSummary;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  roles: Role[];
}

/** Replaces a user's full role set (FR-003). A rejection (last-admin safeguard, empty set) is
 * shown inline and the selection is kept, not silently cleared. */
export function EditRolesDialog({
  user,
  onClose,
  onSaved,
}: EditRolesDialogProps) {
  const { authFetch } = useAuth();
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ defaultValues: { roles: user.roles } });

  const onSubmit = handleSubmit(async (values) => {
    const result = await updateRoles(authFetch, user.id, values.roles);
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onSaved();
  });

  return (
    <Dialog open onClose={onClose}>
      <DialogTitle>Roles for {user.displayName}</DialogTitle>
      <form onSubmit={onSubmit} noValidate>
        <DialogContent>
          {errors.root && (
            <Alert severity="error" sx={{ mb: 2 }} role="alert">
              {errors.root.message}
            </Alert>
          )}
          <Controller
            name="roles"
            control={control}
            rules={{
              validate: (v) =>
                v.length > 0 || "A user must hold at least one role.",
            }}
            render={({ field }) => (
              <FormGroup>
                {ALL_ROLES.map((role) => (
                  <FormControlLabel
                    key={role}
                    label={role}
                    control={
                      <Checkbox
                        checked={field.value.includes(role)}
                        onChange={(e) =>
                          field.onChange(
                            e.target.checked
                              ? [...field.value, role]
                              : field.value.filter((r) => r !== role),
                          )
                        }
                      />
                    }
                  />
                ))}
              </FormGroup>
            )}
          />
          {errors.roles && (
            <FormHelperText error>{errors.roles.message}</FormHelperText>
          )}
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
