import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  TextField,
} from "@mui/material";
import { formatDate } from "../attendance/monthUtils";
import type { LeaveRequestView } from "./leaveApi";

export type Decision = "approve" | "reject" | "revoke";

const COPY: Record<
  Decision,
  { title: string; confirm: string; label: string; required: boolean }
> = {
  approve: {
    title: "Approve leave",
    confirm: "Approve",
    label: "Note (optional)",
    required: false,
  },
  reject: {
    title: "Reject leave",
    confirm: "Reject",
    label: "Reason for rejecting",
    required: true,
  },
  revoke: {
    title: "Revoke approved leave",
    confirm: "Revoke",
    label: "Reason for revoking",
    required: true,
  },
};

interface Props {
  request: LeaveRequestView;
  decision: Decision;
  /** Resolves to an error message, or null when the decision went through. */
  onSubmit: (text: string) => Promise<string | null>;
  onClose: () => void;
}

/** Approve, reject (reason required) or revoke one request; shows the server's refusal inline. */
export function LeaveDecisionDialog({
  request,
  decision,
  onSubmit,
  onClose,
}: Props) {
  const copy = COPY[decision];
  const [text, setText] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const range =
    request.firstDate === request.lastDate
      ? formatDate(request.firstDate)
      : `${formatDate(request.firstDate)} to ${formatDate(request.lastDate)}`;

  const submit = async () => {
    if (copy.required && text.trim() === "") {
      setError("A reason is required.");
      return;
    }
    setBusy(true);
    const failure = await onSubmit(text.trim());
    setBusy(false);
    if (failure) setError(failure);
  };

  return (
    <Dialog
      open
      onClose={onClose}
      aria-labelledby="leave-decision-title"
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle id="leave-decision-title">{copy.title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {request.teacherName}, {request.leaveType} leave, {range} (
          {request.workingDays} working days). Reason given: {request.reason}
        </DialogContentText>
        {error && (
          <Alert severity="error" role="alert" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <TextField
          autoFocus
          fullWidth
          multiline
          minRows={2}
          required={copy.required}
          label={copy.label}
          value={text}
          onChange={(e) => setText(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 500 } }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          color={decision === "approve" ? "primary" : "error"}
          disabled={busy}
          onClick={() => void submit()}
        >
          {copy.confirm}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
