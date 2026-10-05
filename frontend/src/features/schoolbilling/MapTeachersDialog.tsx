import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate, formatRupees } from "../teachers/formatters";
import { mapTeachersToContract, type ContractRow } from "./schoolContractsApi";

export interface MappableTeacher {
  teacherId: string;
  teacherName: string;
  /** Where the Teacher is today, for the label ("Position 2 of the earlier MoU", "not mapped"). */
  from: string;
}

interface MapTeachersDialogProps {
  contract: ContractRow;
  teachers: MappableTeacher[];
  onClose: () => void;
  onSaved: () => void;
}

/**
 * Maps the Teachers already at a School to the positions of its (new) contract in one step (spec 012
 * FR-007). Each Teacher's earlier assignment ends the day before the contract starts and the new one begins
 * under the chosen position, so the Teacher is never without a School. All or nothing.
 */
export function MapTeachersDialog({
  contract,
  teachers,
  onClose,
  onSaved,
}: MapTeachersDialogProps) {
  const { authFetch } = useAuth();
  const [choice, setChoice] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const taken = new Set(Object.values(choice).filter(Boolean));

  const save = async () => {
    const entries = Object.entries(choice)
      .filter(([, positionId]) => positionId)
      .map(([teacherId, positionId]) => ({ teacherId, positionId }));
    if (entries.length === 0) {
      setError("Choose a position for at least one Teacher.");
      return;
    }
    setSaving(true);
    const result = await mapTeachersToContract(authFetch, contract.id, entries);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Map Teachers to the MoU</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2">
            MoU from {formatDate(contract.startsOn)}. Each Teacher keeps working
            at the School; only the position (and its salary) changes.
          </Typography>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          {teachers.length === 0 && (
            <Alert severity="info">
              There are no Teachers to map at this School.
            </Alert>
          )}
          {teachers.map((t) => (
            <TextField
              key={t.teacherId}
              select
              label={`${t.teacherName} (${t.from})`}
              value={choice[t.teacherId] ?? ""}
              onChange={(e) => {
                setChoice((old) => ({ ...old, [t.teacherId]: e.target.value }));
                setError(null);
              }}
              slotProps={{
                select: { native: true },
                inputLabel: { shrink: true },
              }}
            >
              <option value=""></option>
              {contract.positions.map((p) => (
                <option
                  key={p.id}
                  value={p.id}
                  disabled={taken.has(p.id) && choice[t.teacherId] !== p.id}
                >
                  {`Position ${p.number}${p.title ? ` (${p.title})` : ""} - ${formatRupees(Number(p.salary))}`}
                </option>
              ))}
            </TextField>
          ))}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={save}
          disabled={saving || teachers.length === 0}
        >
          Map Teachers
        </Button>
      </DialogActions>
    </Dialog>
  );
}
