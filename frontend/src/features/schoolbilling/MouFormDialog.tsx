import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormLabel,
  IconButton,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import DeleteOutlined from "@mui/icons-material/DeleteOutlined";
import { useAuth } from "../../auth/useAuth";
import { todayIso } from "../teachers/formatters";
import {
  createContract,
  getSignatoryCandidates,
  recordMou,
  type ContractRow,
  type MouInput,
  type SalaryMode,
  type SignatoryCandidate,
} from "./schoolContractsApi";

interface MouFormDialogProps {
  schoolId: string;
  schoolName: string;
  /** Present when the MoU is recorded on a "MoU pending" contract; absent for a new contract. */
  pending?: ContractRow;
  onClose: () => void;
  onSaved: () => void;
}

interface SchoolSigner {
  name: string;
  designation: string;
}

/**
 * Records a School's MoU (spec 012 US1): the number of Teachers, the salary (the same for all or a
 * different one for each), the dates, and the signing details (date signed, the School's signatories, and HLS's
 * Zone Manager and/or Directors). A new contract ends the current one the day before it starts.
 */
export function MouFormDialog({
  schoolId,
  schoolName,
  pending,
  onClose,
  onSaved,
}: MouFormDialogProps) {
  const { authFetch } = useAuth();
  const [teacherCount, setTeacherCount] = useState("1");
  const [mode, setMode] = useState<SalaryMode>("SAME_FOR_ALL");
  const [rate, setRate] = useState("");
  const [salaries, setSalaries] = useState<string[]>([""]);
  const [titles, setTitles] = useState<string[]>([""]);
  const [startsOn, setStartsOn] = useState(todayIso());
  const [endsOn, setEndsOn] = useState("");
  const [signedOn, setSignedOn] = useState("");
  const [schoolSigners, setSchoolSigners] = useState<SchoolSigner[]>([
    { name: "", designation: "" },
  ]);
  const [candidates, setCandidates] = useState<SignatoryCandidate[]>([]);
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await getSignatoryCandidates(authFetch, schoolId);
      if (cancelled) return;
      if (result.ok) {
        setCandidates(result.data);
      } else {
        setError(result.reason);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, schoolId]);

  const count = useMemo(() => {
    const n = Number(teacherCount);
    return Number.isInteger(n) && n >= 1 && n <= 500 ? n : 0;
  }, [teacherCount]);

  // keep one salary row per position while the count changes
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- keeps one row per position as the count changes
    setSalaries((old) => Array.from({ length: count }, (_, i) => old[i] ?? ""));
    setTitles((old) => Array.from({ length: count }, (_, i) => old[i] ?? ""));
  }, [count]);

  const keyOf = (c: SignatoryCandidate) => `${c.userId}|${c.designation}`;

  const save = async () => {
    if (count === 0) {
      setError("The number of Teachers must be between 1 and 500.");
      return;
    }
    const hlsSignatories = candidates
      .filter((c) => chosen.has(keyOf(c)))
      .map((c) => ({ userId: c.userId, designation: c.designation }));
    // the same person twice (a Zone Manager who is also a Director) is one signatory
    const seen = new Set<string>();
    const uniqueHls = hlsSignatories.filter((h) =>
      seen.has(h.userId) ? false : (seen.add(h.userId), true),
    );
    const input: MouInput = {
      teacherCount: count,
      salaryMode: mode,
      signedOn,
      schoolSignatories: schoolSigners.map((s) => ({
        name: s.name,
        designation: s.designation,
      })),
      hlsSignatories: uniqueHls,
      ...(mode === "SAME_FOR_ALL"
        ? { rate }
        : {
            positions: salaries.map((salary, i) => ({
              title: titles[i] || undefined,
              salary,
            })),
          }),
    };
    setSaving(true);
    const result = pending
      ? await recordMou(authFetch, pending.id, input)
      : await createContract(authFetch, schoolId, {
          ...input,
          startsOn,
          endsOn: endsOn || null,
        });
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>
        {pending ? "Record the MoU for" : "Record a new MoU for"} {schoolName}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {!pending && (
            <Typography variant="body2" color="text.secondary">
              A new MoU ends the School&apos;s current contract the day before
              it starts.
            </Typography>
          )}
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}

          <TextField
            label="Number of Teachers"
            type="number"
            required
            value={teacherCount}
            onChange={(e) => {
              setTeacherCount(e.target.value);
              setError(null);
            }}
            slotProps={{ htmlInput: { min: 1, max: 500 } }}
            sx={{ maxWidth: 220 }}
          />

          <FormControl>
            <FormLabel id="salary-mode-label">Salary</FormLabel>
            <RadioGroup
              row
              aria-labelledby="salary-mode-label"
              value={mode}
              onChange={(e) => setMode(e.target.value as SalaryMode)}
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
          </FormControl>

          {mode === "SAME_FOR_ALL" ? (
            <TextField
              label="Monthly salary for each Teacher (INR)"
              required
              value={rate}
              onChange={(e) => setRate(e.target.value)}
              slotProps={{ htmlInput: { inputMode: "decimal" } }}
              sx={{ maxWidth: 320 }}
            />
          ) : (
            <Stack spacing={1} aria-label="Salary of each position">
              {salaries.map((salary, i) => (
                <Stack key={i} direction="row" spacing={1}>
                  <TextField
                    label={`Position ${i + 1} title (optional)`}
                    value={titles[i] ?? ""}
                    onChange={(e) =>
                      setTitles((old) =>
                        old.map((t, j) => (j === i ? e.target.value : t)),
                      )
                    }
                  />
                  <TextField
                    label={`Position ${i + 1} monthly salary (INR)`}
                    required
                    value={salary}
                    onChange={(e) =>
                      setSalaries((old) =>
                        old.map((s, j) => (j === i ? e.target.value : s)),
                      )
                    }
                    slotProps={{ htmlInput: { inputMode: "decimal" } }}
                  />
                </Stack>
              ))}
            </Stack>
          )}

          {!pending && (
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                label="Starts on"
                type="date"
                required
                value={startsOn}
                onChange={(e) => setStartsOn(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                label="Ends on (optional)"
                type="date"
                value={endsOn}
                onChange={(e) => setEndsOn(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Stack>
          )}

          <Typography variant="subtitle2" component="h2">
            Signing details
          </Typography>
          <TextField
            label="Date signed"
            type="date"
            required
            value={signedOn}
            onChange={(e) => setSignedOn(e.target.value)}
            slotProps={{
              inputLabel: { shrink: true },
              htmlInput: { max: todayIso() },
            }}
            sx={{ maxWidth: 220 }}
          />

          <Stack spacing={1} aria-label="Signatories for the School">
            <Typography variant="body2">Signed for the School</Typography>
            {schoolSigners.map((s, i) => (
              <Stack
                key={i}
                direction="row"
                spacing={1}
                sx={{ alignItems: "center" }}
              >
                <TextField
                  label={`School signatory ${i + 1} name`}
                  required
                  value={s.name}
                  onChange={(e) =>
                    setSchoolSigners((old) =>
                      old.map((x, j) =>
                        j === i ? { ...x, name: e.target.value } : x,
                      ),
                    )
                  }
                />
                <TextField
                  label={`School signatory ${i + 1} designation`}
                  required
                  value={s.designation}
                  onChange={(e) =>
                    setSchoolSigners((old) =>
                      old.map((x, j) =>
                        j === i ? { ...x, designation: e.target.value } : x,
                      ),
                    )
                  }
                />
                {schoolSigners.length > 1 && (
                  <IconButton
                    aria-label={`Remove School signatory ${i + 1}`}
                    onClick={() =>
                      setSchoolSigners((old) => old.filter((_, j) => j !== i))
                    }
                  >
                    <DeleteOutlined />
                  </IconButton>
                )}
              </Stack>
            ))}
            <Button
              size="small"
              sx={{ alignSelf: "flex-start" }}
              onClick={() =>
                setSchoolSigners((old) => [
                  ...old,
                  { name: "", designation: "" },
                ])
              }
            >
              Add a School signatory
            </Button>
          </Stack>

          <FormControl component="fieldset">
            <FormLabel component="legend">
              Signed for HLS (the Zone Manager and/or a Director)
            </FormLabel>
            <FormGroup>
              {candidates.map((c) => (
                <FormControlLabel
                  key={keyOf(c)}
                  control={
                    <Checkbox
                      checked={chosen.has(keyOf(c))}
                      onChange={(e) =>
                        setChosen((old) => {
                          const next = new Set(old);
                          if (e.target.checked) next.add(keyOf(c));
                          else next.delete(keyOf(c));
                          return next;
                        })
                      }
                    />
                  }
                  label={`${c.name} (${c.designation})`}
                />
              ))}
              {candidates.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  No Zone Manager or Director is available. Assign a Zone
                  Manager to this School first.
                </Typography>
              )}
            </FormGroup>
          </FormControl>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>
          Save MoU
        </Button>
      </DialogActions>
    </Dialog>
  );
}
