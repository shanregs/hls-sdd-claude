import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  CATEGORY_LABELS,
  createStatusCode,
  listStatusCodes,
  updateStatusCode,
  type StatusCategory,
  type StatusCode,
} from "./attendanceApi";

interface FormValues {
  shortCode: string;
  name: string;
  category: StatusCategory;
  weight: string;
}

interface CodeDialogProps {
  code?: StatusCode;
  onClose: () => void;
  onSaved: () => void;
}

/** Add a status code, or change an existing one's name, weight and active flag (FR-007). */
function CodeDialog({ code, onClose, onSaved }: CodeDialogProps) {
  const { authFetch } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: {
      shortCode: code?.shortCode ?? "",
      name: code?.name ?? "",
      category: code?.category ?? "WORKED",
      weight: String(code?.weight ?? 1),
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    const weight = Number(values.weight);
    const result = code
      ? await updateStatusCode(authFetch, code, {
          name: values.name,
          weight,
          active: code.active,
        })
      : await createStatusCode(authFetch, {
          shortCode: values.shortCode,
          name: values.name,
          category: values.category,
          weight,
        });
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onSaved();
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>{code ? "Edit status code" : "Add status code"}</DialogTitle>
      <form onSubmit={onSubmit} noValidate>
        <DialogContent>
          {errors.root && (
            <Alert severity="error" sx={{ mb: 2 }} role="alert">
              {errors.root.message}
            </Alert>
          )}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Short code"
              required
              disabled={!!code}
              slotProps={{ htmlInput: { maxLength: 8 } }}
              error={!!errors.shortCode}
              helperText={errors.shortCode?.message ?? "Up to 8 characters."}
              {...register("shortCode", {
                required: "The short code is required.",
              })}
            />
            <TextField
              label="Name"
              required
              slotProps={{ htmlInput: { maxLength: 60 } }}
              error={!!errors.name}
              helperText={errors.name?.message}
              {...register("name", { required: "The name is required." })}
            />
            <TextField
              select
              label="Category"
              disabled={!!code}
              defaultValue={code?.category ?? "WORKED"}
              {...register("category")}
            >
              {(Object.keys(CATEGORY_LABELS) as StatusCategory[]).map((c) => (
                <MenuItem key={c} value={c}>
                  {CATEGORY_LABELS[c]}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Weight (0 to 1)"
              type="number"
              slotProps={{ htmlInput: { min: 0, max: 1, step: 0.05 } }}
              error={!!errors.weight}
              helperText={
                errors.weight?.message ?? "Counts towards the weighted total."
              }
              {...register("weight", {
                required: "The weight is required.",
                validate: (v) =>
                  (Number(v) >= 0 && Number(v) <= 1) ||
                  "The weight must be between 0 and 1.",
              })}
            />
          </Stack>
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

/** The status code list with add, edit and activate/deactivate (US5). */
export function StatusCodesPanel({ canEdit }: { canEdit: boolean }) {
  const { authFetch } = useAuth();
  const [codes, setCodes] = useState<StatusCode[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<StatusCode | null>(null);

  const load = useCallback(async () => {
    const result = await listStatusCodes(authFetch, false);
    if (result.ok) {
      setError(null);
      setCodes(result.data);
    } else {
      setError(result.reason);
      setCodes([]);
    }
  }, [authFetch]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listStatusCodes(authFetch, false);
      if (cancelled) return;
      if (result.ok) {
        setError(null);
        setCodes(result.data);
      } else {
        setError(result.reason);
        setCodes([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const toggleActive = async (code: StatusCode) => {
    setActionError(null);
    const result = await updateStatusCode(authFetch, code, {
      name: code.name,
      weight: code.weight,
      active: !code.active,
    });
    if (!result.ok) {
      setActionError(result.reason);
      return;
    }
    await load();
  };

  return (
    <Box component="section" aria-labelledby="status-codes-heading">
      <Stack
        direction="row"
        sx={{ alignItems: "center", justifyContent: "space-between", mb: 1 }}
      >
        <Typography variant="h6" component="h2" id="status-codes-heading">
          Status codes
        </Typography>
        {canEdit && (
          <Button variant="contained" onClick={() => setAdding(true)}>
            Add status code
          </Button>
        )}
      </Stack>
      {error && (
        <Alert severity="error" sx={{ mb: 1 }} role="alert">
          {error}
        </Alert>
      )}
      {actionError && (
        <Alert severity="error" sx={{ mb: 1 }} role="alert">
          {actionError}
        </Alert>
      )}
      {codes === null ? (
        <Typography>Loading status codes…</Typography>
      ) : codes.length === 0 && !error ? (
        <Typography>No status codes yet.</Typography>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label="Status codes">
            <TableHead>
              <TableRow>
                <TableCell>Code</TableCell>
                <TableCell>Name</TableCell>
                <TableCell>Category</TableCell>
                <TableCell align="right">Weight</TableCell>
                <TableCell>State</TableCell>
                {canEdit && <TableCell>Actions</TableCell>}
              </TableRow>
            </TableHead>
            <TableBody>
              {codes.map((code) => (
                <TableRow key={code.id}>
                  <TableCell>{code.shortCode}</TableCell>
                  <TableCell>
                    {code.name}
                    {code.system && (
                      <Chip
                        size="small"
                        label="Built-in"
                        sx={{ ml: 1 }}
                        variant="outlined"
                      />
                    )}
                  </TableCell>
                  <TableCell>{CATEGORY_LABELS[code.category]}</TableCell>
                  <TableCell align="right">{code.weight.toFixed(2)}</TableCell>
                  <TableCell>{code.active ? "Active" : "Inactive"}</TableCell>
                  {canEdit && (
                    <TableCell>
                      <Button size="small" onClick={() => setEditing(code)}>
                        Edit
                      </Button>
                      {!code.system && (
                        <Button size="small" onClick={() => toggleActive(code)}>
                          {code.active ? "Deactivate" : "Activate"}
                        </Button>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {(adding || editing) && (
        <CodeDialog
          code={editing ?? undefined}
          onClose={() => {
            setAdding(false);
            setEditing(null);
          }}
          onSaved={() => {
            setAdding(false);
            setEditing(null);
            void load();
          }}
        />
      )}
    </Box>
  );
}
