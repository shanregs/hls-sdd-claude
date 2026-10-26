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
import type { ApiResult } from "../common/masterDataApi";
import type { ClaimDetail } from "./expensesApi";

interface ReasonDialogProps {
  title: string;
  intro?: string;
  label: string;
  confirmLabel: string;
  destructive?: boolean;
  /** Runs the action with the reason typed; the dialog closes on success and shows the reason on failure. */
  onSubmit: (reason: string) => Promise<ApiResult<ClaimDetail>>;
  onDone: (claim: ClaimDetail) => void;
  onCancel: () => void;
}

/** A dialog that needs a reason before it acts (reject, reverse, remove a bill). Focus returns to the opener on close. */
export function ReasonDialog({
  title,
  intro,
  label,
  confirmLabel,
  destructive = true,
  onSubmit,
  onDone,
  onCancel,
}: ReasonDialogProps) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function confirm() {
    if (reason.trim() === "") {
      setError(`${label} is required.`);
      return;
    }
    setBusy(true);
    const result = await onSubmit(reason.trim());
    setBusy(false);
    if (result.ok) {
      onDone(result.data);
    } else {
      setError(result.reason);
    }
  }

  return (
    <Dialog
      open
      onClose={onCancel}
      aria-labelledby="reason-title"
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle id="reason-title">{title}</DialogTitle>
      <DialogContent>
        {intro && <DialogContentText sx={{ mb: 2 }}>{intro}</DialogContentText>}
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <TextField
          label={label}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          fullWidth
          multiline
          minRows={2}
          required
          autoFocus
          slotProps={{ htmlInput: { maxLength: 500 } }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button
          variant="contained"
          color={destructive ? "error" : "primary"}
          onClick={() => void confirm()}
          disabled={busy}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

interface DecisionDialogProps {
  mode: "approve" | "reject";
  claim: ClaimDetail;
  onApprove: () => Promise<ApiResult<ClaimDetail>>;
  onReject: (reason: string) => Promise<ApiResult<ClaimDetail>>;
  onDone: (claim: ClaimDetail) => void;
  onCancel: () => void;
}

/** Approve (a plain confirmation) or reject (a reason is required) a submitted claim. */
export function DecisionDialog({
  mode,
  claim,
  onApprove,
  onReject,
  onDone,
  onCancel,
}: DecisionDialogProps) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (mode === "reject") {
    return (
      <ReasonDialog
        title="Reject this claim"
        intro={`${claim.claimantName} will see the reason and can correct and resubmit.`}
        label="Reason for rejecting"
        confirmLabel="Reject"
        onSubmit={onReject}
        onDone={onDone}
        onCancel={onCancel}
      />
    );
  }

  async function approve() {
    setBusy(true);
    const result = await onApprove();
    setBusy(false);
    if (result.ok) {
      onDone(result.data);
    } else {
      setError(result.reason);
    }
  }

  return (
    <Dialog open onClose={onCancel} aria-labelledby="approve-title">
      <DialogTitle id="approve-title">Approve this claim</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <DialogContentText>
          Approve the claim of {claim.claimantName} for the amount shown. A
          claim the claimant paid is then offered to their next pay run.
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => void approve()}
          disabled={busy}
        >
          Approve
        </Button>
      </DialogActions>
    </Dialog>
  );
}
