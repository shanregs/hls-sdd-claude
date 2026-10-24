import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from "@mui/material";
import { MovementHistory } from "./MovementHistory";

/** The movement history of one Teacher or School in a dialog, opened from the Teacher and School lists. */
export function MovementsDialog({
  title,
  teacherId,
  schoolId,
  onClose,
}: {
  title: string;
  teacherId?: string;
  schoolId?: string;
  onClose: () => void;
}) {
  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="md"
      aria-labelledby="movements-title"
    >
      <DialogTitle id="movements-title">Movements: {title}</DialogTitle>
      <DialogContent>
        <MovementHistory teacherId={teacherId} schoolId={schoolId} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
