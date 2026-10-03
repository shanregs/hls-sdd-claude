import { useForm } from "react-hook-form";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  createTeacher,
  STATUS_LABELS,
  updateTeacher,
  type TeacherContact,
  type TeacherStatus,
  type TeacherSummary,
} from "./teachersApi";

interface TeacherDialogProps {
  /** The Teacher being edited, or undefined to create one. */
  teacher?: TeacherSummary;
  /** A Manager edits contact details only (FR-015): the name is read-only. */
  limited?: boolean;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues extends TeacherContact {
  status: TeacherStatus;
}

/** Create a Teacher, or edit contact details (FR-011). Status changes and placement have their own dialogs. */
export function TeacherDialog({
  teacher,
  limited = false,
  onClose,
  onSaved,
}: TeacherDialogProps) {
  const { authFetch } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: {
      name: teacher?.name ?? "",
      phone: teacher?.phone ?? "",
      email: teacher?.email ?? "",
      address: teacher?.address ?? "",
      status: "IN_TRAINING",
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    const contact: TeacherContact = {
      name: values.name,
      phone: values.phone,
      email: values.email,
      address: values.address,
    };
    const result = teacher
      ? await updateTeacher(authFetch, teacher, contact)
      : await createTeacher(authFetch, contact, values.status);
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onSaved();
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{teacher ? "Edit teacher" : "Create teacher"}</DialogTitle>
      <form onSubmit={onSubmit} noValidate>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {errors.root && (
              <Alert severity="error" role="alert">
                {errors.root.message}
              </Alert>
            )}
            <TextField
              label="Teacher name"
              required
              disabled={limited}
              {...register("name", {
                validate: (v) =>
                  v.trim().length > 0 || "Teacher name is required.",
              })}
              error={Boolean(errors.name)}
              helperText={errors.name?.message}
            />
            <TextField label="Phone" {...register("phone")} />
            <TextField label="Email" type="email" {...register("email")} />
            <TextField
              label="Address"
              multiline
              minRows={2}
              {...register("address")}
            />
            {!teacher && (
              <TextField
                select
                label="Starting status"
                defaultValue="IN_TRAINING"
                {...register("status")}
              >
                <MenuItem value="IN_TRAINING">
                  {STATUS_LABELS.IN_TRAINING}
                </MenuItem>
                <MenuItem value="ACTIVE">{STATUS_LABELS.ACTIVE}</MenuItem>
                <MenuItem value="ON_LEAVE">{STATUS_LABELS.ON_LEAVE}</MenuItem>
              </TextField>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            {teacher ? "Save" : "Create"}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
