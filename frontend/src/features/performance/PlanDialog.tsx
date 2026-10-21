import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { todayIso } from "../teachers/formatters";
import {
  createPlan,
  getOptions,
  updatePlan,
  type Option,
  type PlanView,
} from "./performanceApi";
import { TeacherPicker } from "./TeacherPicker";

/** Plans an observation (a Teacher, a date, a reason), or changes the date and reason of an open plan. */
export function PlanDialog({
  teacherId,
  plan,
  onClose,
  onSaved,
}: {
  /** Fixes the Teacher when the dialog is opened from the Teacher page. */
  teacherId?: string;
  plan?: PlanView;
  onClose: () => void;
  onSaved: (plan: PlanView) => void;
}) {
  const { authFetch } = useAuth();
  const [teacher, setTeacher] = useState(teacherId ?? plan?.teacherId ?? "");
  const [plannedOn, setPlannedOn] = useState(plan?.plannedOn ?? "");
  const [reason, setReason] = useState(plan?.reason ?? "MANAGEMENT_PLAN");
  const [reasonText, setReasonText] = useState(plan?.reasonText ?? "");
  const [reasons, setReasons] = useState<Option[]>([]);
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      const options = await getOptions(authFetch);
      if (options.ok) setReasons(options.data.planReasons);
    })();
  }, [authFetch]);

  async function submit() {
    if (!teacher) return setProblem("Choose the Teacher.");
    if (!plannedOn) return setProblem("Choose the date of the observation.");
    if (plannedOn < todayIso())
      return setProblem("The date of the observation cannot be in the past.");
    if (reason === "OTHER" && !reasonText.trim())
      return setProblem("Say why the observation is planned.");
    setProblem(null);
    setBusy(true);
    const text = reasonText.trim() || null;
    const result = plan
      ? await updatePlan(authFetch, plan.id, {
          plannedOn,
          reason,
          reasonText: text,
          version: plan.version,
        })
      : await createPlan(authFetch, {
          teacherId: teacher,
          plannedOn,
          reason,
          reasonText: text,
        });
    setBusy(false);
    if (result.ok) onSaved(result.data);
    else setProblem(result.reason);
  }

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="plan-title"
    >
      <DialogTitle id="plan-title">
        {plan ? "Change planned observation" : "Plan an observation"}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {problem && <Alert severity="error">{problem}</Alert>}
          {!plan && !teacherId && (
            <TeacherPicker value={teacher} onChange={setTeacher} />
          )}
          <TextField
            label="Date of the observation"
            type="date"
            required
            value={plannedOn}
            onChange={(e) => setPlannedOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            select
            label="Reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          >
            {reasons.map((r) => (
              <MenuItem key={r.value} value={r.value}>
                {r.label}
              </MenuItem>
            ))}
          </TextField>
          {reason === "OTHER" && (
            <TextField
              label="Why"
              required
              value={reasonText}
              onChange={(e) => setReasonText(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          disabled={busy}
          onClick={() => void submit()}
        >
          {plan ? "Save" : "Plan observation"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
