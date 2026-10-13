import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  rowHistory,
  type HistoryEntry,
  type WorkingDaysRow,
} from "./schoolWorkingDaysApi";
import { formatFigure, formatWhen, monthName } from "./workingDaysRules";

const ACTION_LABELS: Record<HistoryEntry["action"], string> = {
  CREATE: "Added",
  EDIT: "Changed",
  DELETE: "Deleted",
  COPY: "Copied",
};

interface HistoryDialogProps {
  row: WorkingDaysRow;
  onClose: () => void;
}

/** The changes of one School's figure for one month, newest first (FR-023). */
export function HistoryDialog({ row, onClose }: HistoryDialogProps) {
  const { authFetch } = useAuth();
  const [entries, setEntries] = useState<HistoryEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await rowHistory(authFetch, row);
      if (cancelled) return;
      if (result.ok) setEntries(result.data);
      else setError(result.reason);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, row]);

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        History: {row.schoolName}, {monthName(row.month)} {row.year}
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" role="alert">
            {error}
          </Alert>
        )}
        {!entries && !error && <LinearProgress aria-label="Loading history" />}
        {entries && entries.length === 0 && (
          <Typography>No changes recorded.</Typography>
        )}
        {entries && entries.length > 0 && (
          <Table size="small" aria-label="Change history">
            <TableHead>
              <TableRow>
                <TableCell>When</TableCell>
                <TableCell>Change</TableCell>
                <TableCell>From</TableCell>
                <TableCell>To</TableCell>
                <TableCell>By</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {entries.map((e, index) => (
                <TableRow key={index}>
                  <TableCell>{formatWhen(e.occurredAt)}</TableCell>
                  <TableCell>{ACTION_LABELS[e.action]}</TableCell>
                  <TableCell>
                    {e.oldValue === null ? "-" : formatFigure(e.oldValue)}
                  </TableCell>
                  <TableCell>
                    {e.newValue === null ? "-" : formatFigure(e.newValue)}
                  </TableCell>
                  <TableCell>
                    {e.actorName} ({e.actorRoles})
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
