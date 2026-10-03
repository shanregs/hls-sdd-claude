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
  FormLabel,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { ALL_ROLES, createUser, type Role } from "./userManagementApi";

interface CreateUserDialogProps {
  onClose: () => void;
  onCreated: () => void;
}

interface FormValues {
  displayName: string;
  phone: string;
  roles: Role[];
  username: string;
  email: string;
  initialPassword: string;
}

/** Create-user form (FR-001): required name, phone and at least one role; optional username,
 * email and initial password. Server rejections (duplicate phone/username/email) show inline. */
export function CreateUserDialog({
  onClose,
  onCreated,
}: CreateUserDialogProps) {
  const { authFetch } = useAuth();
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: {
      displayName: "",
      phone: "",
      roles: [],
      username: "",
      email: "",
      initialPassword: "",
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    const result = await createUser(authFetch, values);
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onCreated();
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Create user</DialogTitle>
      <form onSubmit={onSubmit} noValidate>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {errors.root && (
              <Alert severity="error" role="alert">
                {errors.root.message}
              </Alert>
            )}
            <TextField
              label="Display name"
              required
              {...register("displayName", {
                validate: (v) =>
                  v.trim().length > 0 || "Display name is required.",
              })}
              error={Boolean(errors.displayName)}
              helperText={errors.displayName?.message}
            />
            <TextField
              label="Phone number"
              required
              {...register("phone", {
                validate: (v) =>
                  v.trim().length > 0 || "Phone number is required.",
              })}
              error={Boolean(errors.phone)}
              helperText={errors.phone?.message}
            />
            <Controller
              name="roles"
              control={control}
              rules={{
                validate: (v) => v.length > 0 || "Choose at least one role.",
              }}
              render={({ field }) => (
                <div>
                  <FormLabel component="legend" required>
                    Roles
                  </FormLabel>
                  <FormGroup row>
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
                  {errors.roles && (
                    <FormHelperText error>
                      {errors.roles.message}
                    </FormHelperText>
                  )}
                </div>
              )}
            />
            <TextField label="Username (optional)" {...register("username")} />
            <TextField
              label="Email (optional)"
              type="email"
              {...register("email")}
            />
            <TextField
              label="Initial password (optional)"
              type="password"
              autoComplete="new-password"
              helperText="Leave empty for one-time-code sign-in only."
              {...register("initialPassword")}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Create
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
