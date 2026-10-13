import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  LinearProgress,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  deletedRows,
  type DeletedEntry,
  type SchoolOption,
} from "./schoolWorkingDaysApi";
import { formatFigure, formatWhen, monthName } from "./workingDaysRules";

interface DeletedRowsDialogProps {
  schools: SchoolOption[];
  /** The School and year selected in the grid; the School may be empty. */
  schoolId: string;
  year: number;
  onClose: () => void;
}

/** Every deletion for a School and year, from the audit history, even when the row is gone (FR-023). */
export function DeletedRowsDialog({
  schools,
  schoolId: initialSchool,
  year,
  onClose,
}: DeletedRowsDialogProps) {
  const { authFetch } = useAuth();
  const [schoolId, setSchoolId] = useState(initialSchool);
  const [loaded, setLoaded] = useState<{
    key: string;
    entries: DeletedEntry[] | null;
    error: string | null;
  } | null>(null);

  // show only the answer that belongs to the School and year now chosen
  const key = `${schoolId}|${year}`;
  const current = schoolId && loaded?.key === key ? loaded : null;
  const entries = current?.entries ?? null;
  const error = current?.error ?? null;

  useEffect(() => {
    if (!schoolId) return;
    let cancelled = false;
    (async () => {
      const result = await deletedRows(authFetch, schoolId, year);
      if (cancelled) return;
      setLoaded(
        result.ok
          ? { key: `${schoolId}|${year}`, entries: result.data, error: null }
          : { key: `${schoolId}|${year}`, entries: null, error: result.reason },
      );
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, schoolId, year]);

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Deleted rows for {year}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            select
            label="School"
            value={schoolId}
            onChange={(e) => setSchoolId(e.target.value)}
          >
            {schools.map((s) => (
              <MenuItem key={s.id} value={s.id}>
                {s.name}
              </MenuItem>
            ))}
          </TextField>
          {!schoolId && (
            <Typography>Choose a School to see its deleted rows.</Typography>
          )}
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          {schoolId && !entries && !error && (
            <LinearProgress aria-label="Loading deleted rows" />
          )}
          {entries && entries.length === 0 && (
            <Typography>
              No rows were deleted for this School in {year}.
            </Typography>
          )}
          {entries && entries.length > 0 && (
            <Table size="small" aria-label="Deleted rows">
              <TableHead>
                <TableRow>
                  <TableCell>Month</TableCell>
                  <TableCell>Last figure</TableCell>
                  <TableCell>Deleted by</TableCell>
                  <TableCell>When</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {entries.map((e, index) => (
                  <TableRow key={index}>
                    <TableCell>{monthName(e.month)}</TableCell>
                    <TableCell>{formatFigure(e.lastValue)}</TableCell>
                    <TableCell>{e.deletedByName}</TableCell>
                    <TableCell>{formatWhen(e.deletedAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
