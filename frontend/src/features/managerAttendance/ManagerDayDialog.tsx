import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormLabel,
  MenuItem,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate, todayIso } from "../teachers/formatters";
import {
  MAX_PURPOSE,
  MAX_REASON,
  correctionFor,
  validateCorrection,
  validateDecisionReason,
  type CorrectionValues,
} from "./formRules";
import {
  correctDay,
  getManagerMonth,
  lockMonth,
  reopenMonth,
  type PendingRequest,
  type DayType,
  type MonthDay,
  type MonthView,
  type Place,
} from "./managerAttendanceApi";
import { PendingRequests } from "./PendingRequests";
import { flagLabel, statusStyle, timeOf } from "./statusDisplay";

function placeText(p: Place | null): string {
  return p
    ? `${p.latitude.toFixed(5)}, ${p.longitude.toFixed(5)}${
        p.accuracyMeters != null
          ? ` (within ${Math.round(p.accuracyMeters)} m)`
          : ""
      }`
    : "Not recorded";
}

interface Props {
  managerId: string;
  managerName: string;
  month: string;
  /** The date to show first; empty to show the first date with a record. */
  initialDate: string;
  canEdit: boolean;
  /** May lock and reopen the month (PROCESS). */
  canProcess?: boolean;
  onClose: () => void;
  onChanged: () => void;
}

/** One Manager's month in detail: the day's locations, flags and history, and the correction form (spec 032 US5). */
export function ManagerDayDialog({
  managerId,
  managerName,
  month,
  initialDate,
  canEdit,
  canProcess = false,
  onClose,
  onChanged,
}: Props) {
  const { authFetch } = useAuth();
  const [view, setView] = useState<MonthView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [date, setDate] = useState(initialDate);

  const load = useCallback(async () => {
    const result = await getManagerMonth(authFetch, managerId, month);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    setView(result.data);
  }, [authFetch, managerId, month]);

  useEffect(() => {
    void (async () => {
      await load();
    })();
  }, [load]);

  const shown = date || view?.days.find((d) => d.id)?.date || "";
  const day = view?.days.find((d) => d.date === shown) ?? null;
  const locked = view?.locked ?? false;

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="md"
      aria-labelledby="manager-day-title"
    >
      <DialogTitle id="manager-day-title">
        {managerName}, {month}
      </DialogTitle>
      <DialogContent dividers>
        {error && (
          <Alert severity="error" role="alert">
            {error}
          </Alert>
        )}
        {!view && !error && (
          <Box sx={{ p: 3, textAlign: "center" }}>
            <CircularProgress aria-label="Loading the month" />
          </Box>
        )}
        {view && (
          <Stack spacing={2}>
            {locked && (
              <Alert severity="info">
                This month is locked for payroll. Days cannot be corrected.
              </Alert>
            )}
            {view.figuresUnavailable && (
              <Alert severity="warning">{view.figuresUnavailable}</Alert>
            )}
            <TextField
              select
              size="small"
              label="Day"
              value={day?.date ?? ""}
              onChange={(e) => setDate(e.target.value)}
            >
              {view.days.map((d) => (
                <MenuItem key={d.date} value={d.date}>
                  {formatDate(d.date)}: {statusStyle(d.status).label}
                </MenuItem>
              ))}
            </TextField>

            {day && <DayDetail day={day} />}

            {canEdit && !locked && day && (
              <CorrectionForm
                key={`${day.date}|${day.id}|${day.checkInAt}|${day.checkOutAt}|${day.dayType}`}
                day={day}
                onSave={async (body) => {
                  const result = await correctDay(
                    authFetch,
                    managerId,
                    day.date,
                    body,
                  );
                  if (!result.ok) return result.reason;
                  await load();
                  onChanged();
                  return null;
                }}
              />
            )}

            {canProcess && (
              <MonthLockSection
                locked={locked}
                month={month}
                managerId={managerId}
                onChanged={async () => {
                  await load();
                  onChanged();
                }}
              />
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

function DayDetail({ day }: { day: MonthDay }) {
  const style = statusStyle(day.status);
  return (
    <Stack spacing={1}>
      <Stack direction="row" sx={{ gap: 1, flexWrap: "wrap" }}>
        <Chip size="small" color={style.color} label={style.label} />
        {day.flags.map((f) => (
          <Chip key={f} size="small" variant="outlined" label={flagLabel(f)} />
        ))}
        {day.hasPhoto && <Chip size="small" label="Photo added" />}
      </Stack>
      {day.checkInAt && (
        <Typography>
          Checked in at {timeOf(day.checkInAt)} (
          {day.dayType === "FIELD" ? "Field" : "Office"})
          {day.checkOutAt ? `, out at ${timeOf(day.checkOutAt)}` : ""}
        </Typography>
      )}
      {day.purpose && <Typography>Purpose: {day.purpose}</Typography>}
      {day.checkInAt && (
        <Typography variant="body2" color="text.secondary">
          Check-in place: {placeText(day.checkInPlace)}. Check-out place:{" "}
          {placeText(day.checkOutPlace)}.
        </Typography>
      )}

      <Typography variant="subtitle2" component="h3">
        History
      </Typography>
      {day.history && day.history.length > 0 ? (
        <Stack component="ul" spacing={0.5} sx={{ m: 0, pl: 2 }}>
          {day.history.map((h) => (
            <Typography key={h.id} component="li" variant="body2">
              {new Date(h.changedAt).toLocaleString("en-IN")}:{" "}
              {h.action.replace(/_/g, " ").toLowerCase()} by {h.changedByName}
              {h.reason ? `. Reason: ${h.reason}` : ""}
            </Typography>
          ))}
        </Stack>
      ) : (
        <Typography variant="body2" color="text.secondary">
          No changes recorded.
        </Typography>
      )}
    </Stack>
  );
}

function CorrectionForm({
  day,
  onSave,
}: {
  day: MonthDay;
  /** Resolves to the reason the server gave, or null when saved. */
  onSave: (body: Record<string, unknown>) => Promise<string | null>;
}) {
  const [form, setForm] = useState<CorrectionValues>(() => correctionFor(day));
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const set = <K extends keyof CorrectionValues>(
    key: K,
    value: CorrectionValues[K],
  ) => setForm((prev) => ({ ...prev, [key]: value }));
  const problem = validateCorrection(form);

  const save = async () => {
    setTouched(true);
    setMessage(null);
    if (problem) return;
    setBusy(true);
    const body: Record<string, unknown> = { reason: form.reason.trim() };
    if (form.clear) {
      body.clear = true;
    } else {
      body.dayType = form.dayType;
      body.purpose = form.dayType === "FIELD" ? form.purpose.trim() : null;
      body.checkInTime = form.checkIn;
      if (form.checkOut !== "") body.checkOutTime = form.checkOut;
    }
    const failure = await onSave(body);
    setBusy(false);
    if (failure) setMessage(failure);
  };

  return (
    <Stack
      spacing={2}
      component="form"
      noValidate
      onSubmit={(e) => e.preventDefault()}
      aria-label="Correct the day"
    >
      <Typography variant="subtitle2" component="h3">
        Correct this day
      </Typography>
      {message && (
        <Alert severity="error" role="alert">
          {message}
        </Alert>
      )}
      {touched && problem && (
        <Alert severity="error" role="alert">
          {problem}
        </Alert>
      )}
      <FormControlLabel
        control={
          <Checkbox
            checked={form.clear}
            onChange={(e) => set("clear", e.target.checked)}
          />
        }
        label="Remove this day's check-in and check-out"
      />
      {!form.clear && (
        <>
          <FormControl>
            <FormLabel id="correct-day-type">Day type</FormLabel>
            <RadioGroup
              row
              aria-labelledby="correct-day-type"
              value={form.dayType}
              onChange={(e) => set("dayType", e.target.value as DayType)}
            >
              <FormControlLabel
                value="OFFICE"
                control={<Radio />}
                label="Office"
              />
              <FormControlLabel
                value="FIELD"
                control={<Radio />}
                label="Field"
              />
            </RadioGroup>
          </FormControl>
          {form.dayType === "FIELD" && (
            <TextField
              label="Purpose"
              multiline
              minRows={2}
              value={form.purpose}
              onChange={(e) => set("purpose", e.target.value)}
              helperText={`${form.purpose.length}/${MAX_PURPOSE}`}
            />
          )}
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              label="Check-in time"
              type="time"
              value={form.checkIn}
              onChange={(e) => set("checkIn", e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Check-out time"
              type="time"
              value={form.checkOut}
              onChange={(e) => set("checkOut", e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Stack>
        </>
      )}
      <TextField
        label="Reason"
        multiline
        minRows={2}
        value={form.reason}
        onChange={(e) => set("reason", e.target.value)}
        helperText={`${form.reason.length}/${MAX_REASON}`}
      />
      <Box>
        <Button variant="contained" onClick={() => void save()} disabled={busy}>
          Save correction
        </Button>
      </Box>
    </Stack>
  );
}

/** Lock or reopen this Manager's month (spec 032 US6). A lock is refused while requests are waiting, and says which. */
function MonthLockSection({
  locked,
  month,
  managerId,
  onChanged,
}: {
  locked: boolean;
  month: string;
  managerId: string;
  onChanged: () => Promise<void>;
}) {
  const { authFetch } = useAuth();
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [pending, setPending] = useState<PendingRequest[]>([]);
  // a month can be locked once it has ended
  const ended = month < todayIso().slice(0, 7);
  const reasonProblem = touched ? validateDecisionReason(reason) : null;

  const lock = async () => {
    setBusy(true);
    setMessage(null);
    setPending([]);
    const outcome = await lockMonth(authFetch, month, managerId);
    setBusy(false);
    if (!outcome.ok) {
      setMessage(outcome.reason);
      setPending(outcome.pending);
      return;
    }
    if (outcome.data.refused.length > 0) {
      setMessage(outcome.data.refused[0].reason);
      setPending(outcome.data.refused[0].pending);
      return;
    }
    await onChanged();
  };

  const reopen = async () => {
    setTouched(true);
    setMessage(null);
    if (validateDecisionReason(reason)) return;
    setBusy(true);
    const result = await reopenMonth(
      authFetch,
      month,
      managerId,
      reason.trim(),
    );
    setBusy(false);
    if (!result.ok) {
      setMessage(result.reason);
      return;
    }
    setReason("");
    setTouched(false);
    await onChanged();
  };

  if (!locked && !ended) return null;
  return (
    <Stack spacing={1.5} component="section" aria-label="Month lock">
      <Typography variant="subtitle2" component="h3">
        Month lock
      </Typography>
      {message && (
        <Alert severity="error" role="alert">
          {message}
          <PendingRequests items={pending} showManager={false} />
        </Alert>
      )}
      {locked ? (
        <>
          <TextField
            label="Reason for reopening"
            multiline
            minRows={2}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            error={reasonProblem !== null}
            helperText={reasonProblem ?? `${reason.length}/${MAX_REASON}`}
          />
          <Box>
            <Button disabled={busy} onClick={() => void reopen()}>
              Reopen month
            </Button>
          </Box>
        </>
      ) : (
        <Box>
          <Button
            variant="outlined"
            disabled={busy}
            onClick={() => void lock()}
          >
            Lock this month
          </Button>
        </Box>
      )}
    </Stack>
  );
}
