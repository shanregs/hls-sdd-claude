import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate } from "../attendance/monthUtils";
import {
  getLeaveTypes,
  previewLeave,
  submitLeave,
  type LeaveDraft,
  type LeavePreview,
  type LeaveType,
} from "./leaveApi";

const MAX_REASON = 500;
const PREVIEW_DELAY_MS = 300;

const EMPTY: LeaveDraft = {
  leaveTypeId: "",
  firstDate: "",
  lastDate: "",
  halfDayStart: false,
  halfDayEnd: false,
  reason: "",
};

/** LEAVE -> Apply Leave (spec 009 US1): choose type and dates, see the working-day count, submit. */
export function ApplyLeavePage() {
  const { authFetch } = useAuth();
  const [types, setTypes] = useState<LeaveType[]>([]);
  const [draft, setDraft] = useState<LeaveDraft>(EMPTY);
  const [preview, setPreview] = useState<LeavePreview | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await getLeaveTypes(authFetch);
      if (cancelled) return;
      if (result.ok) setTypes(result.data);
      else setLoadError(result.reason);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  const datesReady = draft.firstDate !== "" && draft.lastDate !== "";
  useEffect(() => {
    if (!datesReady) return;
    let cancelled = false;
    const timer = setTimeout(async () => {
      const result = await previewLeave(authFetch, draft);
      if (cancelled) return;
      setPreview(
        result.ok
          ? result.data
          : { workingDays: 0, days: [], problems: [result.reason] },
      );
    }, PREVIEW_DELAY_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [authFetch, draft, datesReady]);

  const update = (patch: Partial<LeaveDraft>) => {
    setSubmitted(false);
    setSubmitError(null);
    setDraft((d) => ({ ...d, ...patch }));
  };

  const reasonOk =
    draft.reason.trim().length > 0 && draft.reason.length <= MAX_REASON;
  const canSubmit =
    !busy &&
    draft.leaveTypeId !== "" &&
    datesReady &&
    reasonOk &&
    preview !== null &&
    preview.problems.length === 0;

  const submit = async () => {
    setBusy(true);
    setSubmitError(null);
    const result = await submitLeave(authFetch, draft);
    setBusy(false);
    if (!result.ok) {
      setSubmitError(result.reason);
      return;
    }
    setSubmitted(true);
    setDraft(EMPTY);
    setPreview(null);
  };

  const shownPreview = datesReady ? preview : null;

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        Apply Leave
      </Typography>
      {loadError && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {loadError}
        </Alert>
      )}
      {submitted && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Your leave request was sent. You can follow it under My Leave History.
        </Alert>
      )}
      <Paper variant="outlined" sx={{ p: 2, maxWidth: 560 }}>
        <Stack
          spacing={2}
          component="form"
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit) void submit();
          }}
        >
          {submitError && (
            <Alert severity="error" role="alert">
              {submitError}
            </Alert>
          )}
          <TextField
            select
            required
            label="Leave type"
            value={draft.leaveTypeId}
            onChange={(e) => update({ leaveTypeId: e.target.value })}
          >
            {types.map((t) => (
              <MenuItem key={t.id} value={t.id}>
                {t.name}
              </MenuItem>
            ))}
          </TextField>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              required
              type="date"
              label="First day"
              value={draft.firstDate}
              onChange={(e) => update({ firstDate: e.target.value })}
              slotProps={{ inputLabel: { shrink: true } }}
              sx={{ flex: 1 }}
            />
            <TextField
              required
              type="date"
              label="Last day"
              value={draft.lastDate}
              onChange={(e) => update({ lastDate: e.target.value })}
              slotProps={{
                inputLabel: { shrink: true },
                htmlInput: { min: draft.firstDate || undefined },
              }}
              sx={{ flex: 1 }}
            />
          </Stack>
          <Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
            <FormControlLabel
              control={
                <Checkbox
                  checked={draft.halfDayStart}
                  onChange={(e) => update({ halfDayStart: e.target.checked })}
                />
              }
              label="Half day on the first working day"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={draft.halfDayEnd}
                  onChange={(e) => update({ halfDayEnd: e.target.checked })}
                />
              }
              label="Half day on the last working day"
            />
          </Stack>
          <TextField
            required
            multiline
            minRows={2}
            label="Reason"
            value={draft.reason}
            onChange={(e) => update({ reason: e.target.value })}
            error={draft.reason.length > MAX_REASON}
            helperText={`${draft.reason.length}/${MAX_REASON}`}
          />
          {shownPreview && (
            <Box aria-live="polite" data-testid="leave-preview">
              {shownPreview.problems.map((p) => (
                <Alert key={p} severity="warning" sx={{ mb: 1 }}>
                  {p}
                </Alert>
              ))}
              {shownPreview.problems.length === 0 && (
                <Typography>
                  This covers {shownPreview.workingDays} working{" "}
                  {shownPreview.workingDays === 1 ? "day" : "days"}
                  {shownPreview.days.length > 0 &&
                    `: ${shownPreview.days
                      .map(
                        (d) =>
                          formatDate(d.date) + (d.value < 1 ? " (half)" : ""),
                      )
                      .join(", ")}`}
                  . Sundays, weekly off days and holidays are not counted.
                </Typography>
              )}
            </Box>
          )}
          <Box>
            <Button type="submit" variant="contained" disabled={!canSubmit}>
              Send request
            </Button>
          </Box>
        </Stack>
      </Paper>
    </Box>
  );
}
