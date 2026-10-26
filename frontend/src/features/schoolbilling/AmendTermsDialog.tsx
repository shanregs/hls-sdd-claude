import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  amendMouTerms,
  type AmendResult,
  type AmendTermsInput,
} from "./mouApprovalApi";
import type { ContractRow } from "./schoolContractsApi";

const MONEY = /^\d+(\.\d{1,2})?$/;

/**
 * The approver corrects the amounts and dates of a pending MoU (spec 031 FR-012). Only the fields that changed are sent;
 * the number of Teachers, the salary mode and the signatories are not offered.
 */
export function AmendTermsDialog({
  contract,
  approvalVersion,
  onClose,
  onSaved,
}: {
  contract: ContractRow;
  approvalVersion: number;
  onClose: () => void;
  onSaved: (result: AmendResult) => void;
}) {
  const { authFetch } = useAuth();
  const sameForAll = contract.salaryMode === "SAME_FOR_ALL";
  const [rate, setRate] = useState(contract.rate ?? "");
  const [salaries, setSalaries] = useState<string[]>(
    contract.positions.map((p) => p.salary),
  );
  const [signedOn, setSignedOn] = useState(contract.signedOn ?? "");
  const [startsOn, setStartsOn] = useState(contract.startsOn);
  const [endsOn, setEndsOn] = useState(contract.endsOn ?? "");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const save = () => {
    const input: AmendTermsInput = { version: approvalVersion };
    if (sameForAll) {
      if (!MONEY.test(rate) || Number(rate) <= 0) {
        setError("Enter the salary as an amount above zero with at most two decimals.");
        return;
      }
      if (Number(rate) !== Number(contract.rate)) {
        input.rate = rate;
      }
    } else {
      const changed: { number: number; salary: string }[] = [];
      for (let i = 0; i < contract.positions.length; i++) {
        const p = contract.positions[i];
        if (!MONEY.test(salaries[i]) || Number(salaries[i]) <= 0) {
          setError(
            `Enter the salary of position ${p.number} as an amount above zero with at most two decimals.`,
          );
          return;
        }
        if (Number(salaries[i]) !== Number(p.salary)) {
          changed.push({ number: p.number, salary: salaries[i] });
        }
      }
      if (changed.length > 0) {
        input.positions = changed;
      }
    }
    if (signedOn && signedOn !== contract.signedOn) {
      input.signedOn = signedOn;
    }
    if (startsOn && startsOn !== contract.startsOn) {
      input.startsOn = startsOn;
    }
    if (endsOn && endsOn !== (contract.endsOn ?? "")) {
      input.endsOn = endsOn;
    }
    if (Object.keys(input).length === 1) {
      setError("Nothing was changed.");
      return;
    }
    setBusy(true);
    setError(null);
    void (async () => {
      const result = await amendMouTerms(authFetch, contract.id, input);
      setBusy(false);
      if (result.ok) {
        onSaved(result.data);
      } else {
        setError(result.reason);
      }
    })();
  };

  return (
    <Dialog open onClose={onClose} aria-labelledby="amend-title" fullWidth>
      <DialogTitle id="amend-title">Correct the terms</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Change only what does not match the signed document. Every change is
          recorded with the old and the new value.
        </DialogContentText>
        <Stack spacing={2}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          {sameForAll ? (
            <TextField
              label="Monthly salary for each Teacher"
              value={rate}
              onChange={(e) => setRate(e.target.value)}
              slotProps={{ htmlInput: { inputMode: "decimal" } }}
            />
          ) : (
            contract.positions.map((p, i) => (
              <TextField
                key={p.id}
                label={`Monthly salary of position ${p.number}`}
                value={salaries[i]}
                onChange={(e) =>
                  setSalaries(
                    salaries.map((s, j) => (j === i ? e.target.value : s)),
                  )
                }
                slotProps={{ htmlInput: { inputMode: "decimal" } }}
              />
            ))
          )}
          <TextField
            type="date"
            label="Signed on"
            value={signedOn}
            onChange={(e) => setSignedOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            type="date"
            label="Starts on"
            value={startsOn}
            onChange={(e) => setStartsOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            helperText="Can move only when no earlier MoU ends the day before and no Teacher was placed before the new date."
          />
          <TextField
            type="date"
            label="Ends on"
            value={endsOn}
            onChange={(e) => setEndsOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={busy}>
          Save corrections
        </Button>
      </DialogActions>
    </Dialog>
  );
}
