import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormLabel,
  MenuItem,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  listStatusCodes,
  type ApiResultLike,
  type MarkBody,
  type MarkView,
  type StatusCode,
} from "./attendanceApi";
import { formatDate } from "./monthUtils";

interface FormValues {
  statusCode: string;
  dayValue: "1" | "0.5";
  note: string;
}

interface MarkDialogProps {
  /** Heading context, for example the Teacher's name. */
  subject?: string;
  date: string;
  existing: MarkView | null;
  onSave: (body: MarkBody) => Promise<ApiResultLike>;
  /** Present only for supervisors who may clear a mark. */
  onClear?: () => Promise<ApiResultLike>;
  onClose: () => void;
  onSaved: () => void;
}

/** Mark or correct one day: status, whole or half day, and a note (spec 008 FR-001, FR-003). */
export function MarkDialog({
  subject,
  date,
  existing,
  onSave,
  onClear,
  onClose,
  onSaved,
}: MarkDialogProps) {
  const { authFetch } = useAuth();
  const [codes, setCodes] = useState<StatusCode[]>([]);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: {
      statusCode: existing?.code ?? "P",
      dayValue: existing && existing.dayValue < 1 ? "0.5" : "1",
      note: existing?.note ?? "",
    },
  });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listStatusCodes(authFetch, true);
      if (!cancelled && result.ok) setCodes(result.data);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const submit = handleSubmit(async (values) => {
    const result = await onSave({
      statusCode: values.statusCode,
      dayValue: Number(values.dayValue),
      note: values.note.trim(),
      version: existing?.version,
    });
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onSaved();
  });

  const clear = async () => {
    if (!onClear) return;
    const result = await onClear();
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>
        {existing ? "Correct attendance" : "Mark attendance"} -{" "}
        {formatDate(date)}
      </DialogTitle>
      <form onSubmit={submit} noValidate>
        <DialogContent>
          {subject && (
            <Typography variant="body2" sx={{ mb: 1 }}>
              {subject}
            </Typography>
          )}
          {errors.root && (
            <Alert severity="error" sx={{ mb: 2 }} role="alert">
              {errors.root.message}
            </Alert>
          )}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Controller
              name="statusCode"
              control={control}
              rules={{ required: "Choose a status." }}
              render={({ field }) => (
                <TextField
                  select
                  label="Status"
                  required
                  error={!!errors.statusCode}
                  helperText={errors.statusCode?.message}
                  {...field}
                  value={
                    codes.some((c) => c.shortCode === field.value) ||
                    existing?.code === field.value
                      ? field.value
                      : ""
                  }
                >
                  {codes.map((c) => (
                    <MenuItem key={c.id} value={c.shortCode}>
                      {c.name} ({c.shortCode})
                    </MenuItem>
                  ))}
                  {existing &&
                    !codes.some((c) => c.shortCode === existing.code) && (
                      <MenuItem value={existing.code}>
                        {existing.codeName} ({existing.code})
                      </MenuItem>
                    )}
                </TextField>
              )}
            />
            <FormControl>
              <FormLabel id="day-value-label">Day value</FormLabel>
              <Controller
                name="dayValue"
                control={control}
                render={({ field }) => (
                  <RadioGroup row aria-labelledby="day-value-label" {...field}>
                    <FormControlLabel
                      value="1"
                      control={<Radio />}
                      label="Whole day"
                    />
                    <FormControlLabel
                      value="0.5"
                      control={<Radio />}
                      label="Half day"
                    />
                  </RadioGroup>
                )}
              />
            </FormControl>
            <Typography variant="caption">
              For a split day, record the larger share and put the other in the
              note; for equal halves choose either.
            </Typography>
            <TextField
              label="Note"
              multiline
              minRows={2}
              slotProps={{ htmlInput: { maxLength: 500 } }}
              error={!!errors.note}
              helperText={errors.note?.message}
              {...register("note", {
                maxLength: {
                  value: 500,
                  message: "The note can be at most 500 characters.",
                },
              })}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          {existing && onClear && (
            <Button color="error" onClick={clear} disabled={isSubmitting}>
              Clear mark
            </Button>
          )}
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Save
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
