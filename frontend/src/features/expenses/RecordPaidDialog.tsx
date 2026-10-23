import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import type { ApiResult } from "../common/masterDataApi";
import { todayIso } from "../teachers/formatters";
import {
  MODE_LABELS,
  PAID_MODES,
  type ClaimDetail,
  type PaidMode,
} from "./expensesApi";

interface Props {
  onRecord: (body: {
    paidOn: string;
    mode: PaidMode;
    reference: string | null;
  }) => Promise<ApiResult<ClaimDetail>>;
  onDone: (claim: ClaimDetail) => void;
  onCancel: () => void;
}

/** The Admin records that an approved claim was paid outside payroll: date, mode and a reference. */
export function RecordPaidDialog({ onRecord, onDone, onCancel }: Props) {
  const [paidOn, setPaidOn] = useState(todayIso());
  const [mode, setMode] = useState<PaidMode>("BANK_TRANSFER");
  const [reference, setReference] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    if (paidOn === "") {
      setError("The payment date is required.");
      return;
    }
    setBusy(true);
    const result = await onRecord({
      paidOn,
      mode,
      reference: reference.trim() === "" ? null : reference.trim(),
    });
    setBusy(false);
    if (result.ok) {
      onDone(result.data);
    } else {
      setError(result.reason);
    }
  }

  return (
    <Dialog open onClose={onCancel} aria-labelledby="paid-title" fullWidth maxWidth="sm">
      <DialogTitle id="paid-title">Record as paid outside payroll</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Payroll stops offering this claim once it is recorded as paid.
        </DialogContentText>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Stack spacing={2}>
          <TextField
            label="Paid on"
            type="date"
            value={paidOn}
            onChange={(e) => setPaidOn(e.target.value)}
            required
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: todayIso() } }}
          />
          <TextField
            select
            label="Mode"
            value={mode}
            onChange={(e) => setMode(e.target.value as PaidMode)}
          >
            {PAID_MODES.map((m) => (
              <MenuItem key={m} value={m}>
                {MODE_LABELS[m]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Reference"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 100 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant="contained" onClick={() => void save()} disabled={busy}>
          Record payment
        </Button>
      </DialogActions>
    </Dialog>
  );
}
