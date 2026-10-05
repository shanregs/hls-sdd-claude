import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { createProposal, type Proposal } from "./marketingApi";

interface ProposalFormProps {
  prospectId: string;
  /** The latest revision, used as the starting point for the next one. */
  latest?: Proposal | null;
  onClose: () => void;
  onSaved: () => void;
}

const MONEY = /^\d+(\.\d{1,2})?$/;

/** A new proposal revision: Teacher count, start month, and the same salary for all or one per position. Never edits an old one. */
export function ProposalForm({
  prospectId,
  latest,
  onClose,
  onSaved,
}: ProposalFormProps) {
  const { authFetch } = useAuth();
  const [count, setCount] = useState(String(latest?.teacherCount ?? 1));
  const [month, setMonth] = useState(latest?.startMonth?.slice(0, 7) ?? "");
  const [mode, setMode] = useState<"SAME_FOR_ALL" | "PER_TEACHER">(
    latest?.salaryMode ?? "SAME_FOR_ALL",
  );
  const [rate, setRate] = useState(latest?.rate ?? "");
  const [salaries, setSalaries] = useState<string[]>(
    latest?.positions.map((p) => p.salary) ?? [],
  );
  const [titles, setTitles] = useState<string[]>(
    latest?.positions.map((p) => p.title ?? "") ?? [],
  );
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const n = Math.max(0, Math.min(500, Number.parseInt(count, 10) || 0));
  const rows = Array.from(
    { length: mode === "PER_TEACHER" ? n : 0 },
    (_x, i) => i,
  );

  const save = async () => {
    if (n < 1) {
      setError("The number of Teachers must be between 1 and 500.");
      return;
    }
    if (!month) {
      setError("Choose the start month.");
      return;
    }
    if (mode === "SAME_FOR_ALL" && !MONEY.test(rate.trim())) {
      setError(
        "Enter the monthly salary as an amount with at most two decimals.",
      );
      return;
    }
    if (
      mode === "PER_TEACHER" &&
      rows.some((i) => !MONEY.test((salaries[i] ?? "").trim()))
    ) {
      setError("Enter a salary for every position, with at most two decimals.");
      return;
    }
    setSaving(true);
    const result = await createProposal(authFetch, prospectId, {
      teacherCount: n,
      startMonth: month,
      salaryMode: mode,
      rate: mode === "SAME_FOR_ALL" ? rate.trim() : undefined,
      positions:
        mode === "PER_TEACHER"
          ? rows.map((i) => ({
              title: titles[i] || undefined,
              salary: salaries[i].trim(),
            }))
          : undefined,
      notes: notes || undefined,
    });
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="proposal-title"
    >
      <DialogTitle id="proposal-title">New proposal revision</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Alert severity="info">
            Proposal (not a contract). The signed MoU is recorded separately in
            School Contracts.
          </Alert>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField
            required
            type="number"
            label="Number of Teachers"
            value={count}
            onChange={(e) => setCount(e.target.value)}
            slotProps={{ htmlInput: { min: 1, max: 500 } }}
          />
          <TextField
            required
            type="month"
            label="Start month"
            value={month}
            onChange={(e) => setMonth(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <RadioGroup
            row
            aria-label="Salary mode"
            value={mode}
            onChange={(e) =>
              setMode(e.target.value as "SAME_FOR_ALL" | "PER_TEACHER")
            }
          >
            <FormControlLabel
              value="SAME_FOR_ALL"
              control={<Radio />}
              label="Same for all Teachers"
            />
            <FormControlLabel
              value="PER_TEACHER"
              control={<Radio />}
              label="Different for each Teacher"
            />
          </RadioGroup>
          {mode === "SAME_FOR_ALL" ? (
            <TextField
              required
              label="Monthly salary for each Teacher (INR)"
              value={rate}
              onChange={(e) => setRate(e.target.value)}
            />
          ) : (
            <Stack
              spacing={1}
              role="group"
              aria-label="Salary of each position"
            >
              {rows.length === 0 && (
                <Typography color="text.secondary">
                  Enter the number of Teachers first.
                </Typography>
              )}
              {rows.map((i) => (
                <Stack
                  key={i}
                  direction={{ xs: "column", sm: "row" }}
                  spacing={1}
                >
                  <TextField
                    size="small"
                    label={`Position ${i + 1} title (optional)`}
                    value={titles[i] ?? ""}
                    onChange={(e) =>
                      setTitles((t) =>
                        Object.assign([...t], { [i]: e.target.value }),
                      )
                    }
                    fullWidth
                  />
                  <TextField
                    size="small"
                    required
                    label={`Position ${i + 1} monthly salary (INR)`}
                    value={salaries[i] ?? ""}
                    onChange={(e) =>
                      setSalaries((s) =>
                        Object.assign([...s], { [i]: e.target.value }),
                      )
                    }
                    fullWidth
                  />
                </Stack>
              ))}
            </Stack>
          )}
          <TextField
            label="Notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => void save()}
          disabled={saving}
        >
          Save revision
        </Button>
      </DialogActions>
    </Dialog>
  );
}
