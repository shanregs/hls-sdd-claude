import { useEffect, useState } from "react";
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
  createWorkingDays,
  listWorkingDays,
  type SchoolOption,
  type WorkingDaysRow,
} from "./schoolWorkingDaysApi";
import { figureProblem, MONTH_NAMES } from "./workingDaysRules";

interface AddRowDialogProps {
  schools: SchoolOption[];
  /** The year currently shown in the grid, offered as the starting year. */
  year: number;
  onClose: () => void;
  onSaved: (row: WorkingDaysRow) => void;
  /** Called when the School and month already have a figure and the user chooses to edit it. */
  onEditExisting: (row: WorkingDaysRow) => void;
}

/** Adds the figure for a School and month. A School and month that already have one cannot be picked again. */
export function AddRowDialog({
  schools,
  year: initialYear,
  onClose,
  onSaved,
  onEditExisting,
}: AddRowDialogProps) {
  const { authFetch } = useAuth();
  const [schoolId, setSchoolId] = useState("");
  const [year, setYear] = useState(String(initialYear));
  const [month, setMonth] = useState("");
  const [figure, setFigure] = useState("");
  const [takenFor, setTakenFor] = useState<{
    key: string;
    months: Set<number>;
  } | null>(null);
  const [touched, setTouched] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [existing, setExisting] = useState<WorkingDaysRow | null>(null);
  const [saving, setSaving] = useState(false);

  const yearNumber = Number(year);
  const yearValid = /^\d{4}$/.test(year);

  // the months this School already has in the chosen year; used only while it still matches the choice
  const takenKey = schoolId && yearValid ? `${schoolId}|${year}` : "";
  const taken =
    takenFor && takenFor.key === takenKey ? takenFor.months : new Set<number>();

  useEffect(() => {
    if (!schoolId || !yearValid) return;
    let cancelled = false;
    (async () => {
      const result = await listWorkingDays(authFetch, {
        year: yearNumber,
        schoolId,
        page: 0,
        size: 100,
      });
      if (cancelled || !result.ok) return;
      setTakenFor({
        key: `${schoolId}|${year}`,
        months: new Set(result.data.items.map((r) => r.month)),
      });
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, schoolId, year, yearValid, yearNumber]);

  const monthNumber = Number(month);
  const figureError =
    month && yearValid ? figureProblem(figure, yearNumber, monthNumber) : null;

  const save = async () => {
    setTouched(true);
    setError(null);
    setExisting(null);
    if (!schoolId || !yearValid || !month || figureError || figure === "") {
      return;
    }
    setSaving(true);
    const result = await createWorkingDays(authFetch, {
      schoolId,
      year: yearNumber,
      month: monthNumber,
      workingDays: Number(figure),
    });
    setSaving(false);
    if (result.ok) {
      onSaved(result.data);
      return;
    }
    setError(result.reason);
    if (result.code === "DUPLICATE" && result.existing) {
      setExisting(result.existing);
    }
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Add working days</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert
              severity="error"
              role="alert"
              action={
                existing ? (
                  <Button
                    color="inherit"
                    size="small"
                    onClick={() => onEditExisting(existing)}
                  >
                    Edit it
                  </Button>
                ) : undefined
              }
            >
              {error}
            </Alert>
          )}
          <TextField
            select
            required
            label="School"
            value={schoolId}
            onChange={(e) => setSchoolId(e.target.value)}
            error={touched && !schoolId}
            helperText={touched && !schoolId ? "Choose a School." : undefined}
          >
            {schools.map((s) => (
              <MenuItem key={s.id} value={s.id}>
                {s.name}
                {s.active ? "" : " (inactive)"}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            required
            label="Year"
            value={year}
            onChange={(e) => setYear(e.target.value)}
            slotProps={{ htmlInput: { inputMode: "numeric", maxLength: 4 } }}
            error={touched && !yearValid}
            helperText={
              touched && !yearValid ? "Enter a 4-digit year." : undefined
            }
          />
          <TextField
            select
            required
            label="Month"
            value={month}
            onChange={(e) => setMonth(e.target.value)}
            error={touched && !month}
            helperText={touched && !month ? "Choose a month." : undefined}
          >
            {MONTH_NAMES.map((name, index) => (
              <MenuItem
                key={name}
                value={String(index + 1)}
                disabled={taken.has(index + 1)}
              >
                {name}
                {taken.has(index + 1) ? " (already set)" : ""}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            required
            label="Working days"
            value={figure}
            onChange={(e) => setFigure(e.target.value)}
            slotProps={{ htmlInput: { inputMode: "decimal" } }}
            error={touched && (figure === "" || Boolean(figureError))}
            helperText={
              touched && (figure === "" || figureError)
                ? (figureError ?? "Enter the working days.")
                : month && yearValid
                  ? undefined
                  : "Choose a month to see the allowed range."
            }
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
