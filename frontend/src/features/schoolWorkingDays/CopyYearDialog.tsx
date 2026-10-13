import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  copyYear,
  type CopyResult,
  type SchoolOption,
} from "./schoolWorkingDaysApi";
import { formatFigure, monthName } from "./workingDaysRules";

interface CopyYearDialogProps {
  schools: SchoolOption[];
  /** The year shown in the grid, offered as the year to copy from. */
  year: number;
  schoolId: string;
  onClose: () => void;
  /** Called after a copy so the grid can reload. */
  onCopied: () => void;
}

/** Copies one year's figures of a School to another year; months that already have a figure are left alone. */
export function CopyYearDialog({
  schools,
  year,
  schoolId: initialSchool,
  onClose,
  onCopied,
}: CopyYearDialogProps) {
  const { authFetch } = useAuth();
  const [schoolId, setSchoolId] = useState(initialSchool);
  const [fromYear, setFromYear] = useState(String(year));
  const [toYear, setToYear] = useState(String(year + 1));
  const [touched, setTouched] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CopyResult | null>(null);
  const [busy, setBusy] = useState(false);

  const validYear = (value: string) => /^\d{4}$/.test(value);

  const copy = async () => {
    setTouched(true);
    setError(null);
    if (!schoolId || !validYear(fromYear) || !validYear(toYear)) return;
    setBusy(true);
    const outcome = await copyYear(authFetch, {
      schoolId,
      fromYear: Number(fromYear),
      toYear: Number(toYear),
    });
    setBusy(false);
    if (!outcome.ok) {
      setError(outcome.reason);
      return;
    }
    setResult(outcome.data);
    onCopied();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Copy a year</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          {!result && (
            <>
              <TextField
                select
                required
                label="School"
                value={schoolId}
                onChange={(e) => setSchoolId(e.target.value)}
                error={touched && !schoolId}
                helperText={
                  touched && !schoolId ? "Choose a School." : undefined
                }
              >
                {schools.map((s) => (
                  <MenuItem key={s.id} value={s.id}>
                    {s.name}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                required
                label="Copy from year"
                value={fromYear}
                onChange={(e) => setFromYear(e.target.value)}
                error={touched && !validYear(fromYear)}
                helperText={
                  touched && !validYear(fromYear)
                    ? "Enter a 4-digit year."
                    : undefined
                }
              />
              <TextField
                required
                label="Copy to year"
                value={toYear}
                onChange={(e) => setToYear(e.target.value)}
                error={touched && !validYear(toYear)}
                helperText={
                  touched && !validYear(toYear)
                    ? "Enter a 4-digit year."
                    : undefined
                }
              />
              <Typography variant="body2">
                Months that already have a figure are not changed.
              </Typography>
            </>
          )}
          {result && (
            <>
              <Typography>
                {result.created.length === 0
                  ? "No figures were copied."
                  : `Copied ${result.created.length} month${result.created.length === 1 ? "" : "s"}: ${result.created
                      .map(
                        (c) =>
                          `${monthName(c.month)} ${formatFigure(c.workingDays)}`,
                      )
                      .join(", ")}.`}
              </Typography>
              {result.skipped.length > 0 && (
                <>
                  <Typography>Not copied:</Typography>
                  <List dense aria-label="Months not copied">
                    {result.skipped.map((s) => (
                      <ListItem key={s.month}>
                        <ListItemText
                          primary={`${monthName(s.month)}: ${
                            s.reason === "EXISTS"
                              ? "already has a figure"
                              : `${formatFigure(s.workingDays)} working days does not fit the ${s.daysInMonth} days; enter it yourself`
                          }`}
                        />
                      </ListItem>
                    ))}
                  </List>
                </>
              )}
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{result ? "Close" : "Cancel"}</Button>
        {!result && (
          <Button variant="contained" onClick={copy} disabled={busy}>
            Copy
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
