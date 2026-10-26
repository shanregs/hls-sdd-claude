import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate } from "./format";
import {
  WEEKDAYS,
  createPattern,
  previewPattern,
  updatePattern,
  type PatternBody,
  type PatternView,
  type PreviewRow,
  type RuleKind,
} from "./trainingApi";

interface Props {
  /** The pattern being edited; absent when creating. */
  pattern?: PatternView;
  onSaved: (pattern: PatternView) => void;
  onCancel: () => void;
}

function firstOfMonth(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-01`;
}

/** Create or edit a standing monthly pattern with a live preview of the next months (clamp and holiday notes). */
export function PatternDialog({ pattern, onSaved, onCancel }: Props) {
  const { authFetch } = useAuth();
  const [ruleKind, setRuleKind] = useState<RuleKind>(
    pattern?.ruleKind ?? "NTH_WEEKDAY",
  );
  const [nth, setNth] = useState(String(pattern?.nth ?? 2));
  const [weekday, setWeekday] = useState(String(pattern?.weekday ?? 6));
  const [dayOfMonth, setDayOfMonth] = useState(
    String(pattern?.dayOfMonth ?? 1),
  );
  const [startsAt, setStartsAt] = useState(
    pattern?.startsAt?.slice(0, 5) ?? "10:00",
  );
  const [endsAt, setEndsAt] = useState(pattern?.endsAt?.slice(0, 5) ?? "13:00");
  const [topic, setTopic] = useState(pattern?.topic ?? "");
  const [trainerName, setTrainerName] = useState(pattern?.trainerName ?? "");
  const [venueKind, setVenueKind] = useState(pattern?.venueKind ?? "PHYSICAL");
  const [venue, setVenue] = useState(pattern?.venue ?? "");
  const [startMonth, setStartMonth] = useState(
    pattern?.startMonth ?? firstOfMonth(new Date()),
  );
  const [fromMonth, setFromMonth] = useState(firstOfMonth(new Date()));
  const [confirmed, setConfirmed] = useState(false);
  const [preview, setPreview] = useState<PreviewRow[] | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function body(): PatternBody {
    return {
      ruleKind,
      nth: ruleKind === "NTH_WEEKDAY" ? Number(nth) : null,
      weekday: ruleKind === "NTH_WEEKDAY" ? Number(weekday) : null,
      dayOfMonth: ruleKind === "DAY_OF_MONTH" ? Number(dayOfMonth) : null,
      startsAt,
      endsAt,
      topic: topic.trim(),
      trainerName: trainerName.trim() || undefined,
      venueKind,
      venue: venue.trim() || undefined,
      startMonth,
      fromMonth: pattern ? fromMonth : undefined,
      version: pattern?.version,
    };
  }

  function validate(): Record<string, string> {
    const found: Record<string, string> = {};
    if (!topic.trim()) found.topic = "Enter the topic.";
    if (endsAt <= startsAt)
      found.endsAt = "The end time must be after the start time.";
    if (ruleKind === "DAY_OF_MONTH") {
      const d = Number(dayOfMonth);
      if (!Number.isInteger(d) || d < 1 || d > 31)
        found.dayOfMonth = "Enter a day from 1 to 31.";
    }
    return found;
  }

  async function showPreview() {
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    const result = await previewPattern(authFetch, body());
    if (result.ok) {
      setPreview(result.data);
      setFailure(null);
    } else {
      setFailure(result.reason);
    }
  }

  async function save() {
    const found = validate();
    if (pattern && !confirmed)
      found.confirm = "Confirm that you want to change the coming months.";
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    const result = pattern
      ? await updatePattern(authFetch, pattern.id, body())
      : await createPattern(authFetch, body());
    setBusy(false);
    if (result.ok) onSaved(result.data);
    else setFailure(result.reason);
  }

  return (
    <Dialog
      open
      onClose={onCancel}
      fullWidth
      maxWidth="sm"
      aria-labelledby="pattern-dialog-title"
    >
      <DialogTitle id="pattern-dialog-title">
        {pattern ? "Change this pattern" : "New monthly pattern"}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {failure && <Alert severity="error">{failure}</Alert>}
          <TextField
            select
            label="Rule"
            value={ruleKind}
            onChange={(e) => setRuleKind(e.target.value as RuleKind)}
            slotProps={{ select: { native: true } }}
          >
            <option value="NTH_WEEKDAY">A weekday of the month</option>
            <option value="DAY_OF_MONTH">A fixed day of the month</option>
          </TextField>
          {ruleKind === "NTH_WEEKDAY" ? (
            <Stack direction="row" spacing={2}>
              <TextField
                select
                label="Which"
                value={nth}
                onChange={(e) => setNth(e.target.value)}
                slotProps={{ select: { native: true } }}
              >
                <option value="1">First</option>
                <option value="2">Second</option>
                <option value="3">Third</option>
                <option value="4">Fourth</option>
                <option value="5">Last</option>
              </TextField>
              <TextField
                select
                label="Weekday"
                value={weekday}
                onChange={(e) => setWeekday(e.target.value)}
                slotProps={{ select: { native: true } }}
              >
                {WEEKDAYS.map((d, i) => (
                  <option key={d} value={i + 1}>
                    {d}
                  </option>
                ))}
              </TextField>
            </Stack>
          ) : (
            <TextField
              label="Day of the month"
              value={dayOfMonth}
              onChange={(e) => setDayOfMonth(e.target.value)}
              error={Boolean(errors.dayOfMonth)}
              helperText={
                errors.dayOfMonth ?? "A short month uses its last day."
              }
            />
          )}
          <Stack direction="row" spacing={2}>
            <TextField
              label="Starts at"
              type="time"
              value={startsAt}
              onChange={(e) => setStartsAt(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Ends at"
              type="time"
              value={endsAt}
              onChange={(e) => setEndsAt(e.target.value)}
              error={Boolean(errors.endsAt)}
              helperText={errors.endsAt}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Stack>
          <TextField
            label="Topic"
            value={topic}
            onChange={(e) => setTopic(e.target.value)}
            error={Boolean(errors.topic)}
            helperText={errors.topic}
            required
          />
          <TextField
            label="Trainer"
            value={trainerName}
            onChange={(e) => setTrainerName(e.target.value)}
          />
          <Stack direction="row" spacing={2}>
            <TextField
              select
              label="Venue kind"
              value={venueKind}
              onChange={(e) => setVenueKind(e.target.value)}
              slotProps={{ select: { native: true } }}
            >
              <option value="PHYSICAL">In person</option>
              <option value="VIRTUAL">Online</option>
            </TextField>
            <TextField
              label={venueKind === "VIRTUAL" ? "Meeting link" : "Venue"}
              value={venue}
              onChange={(e) => setVenue(e.target.value)}
              fullWidth
            />
          </Stack>
          {pattern ? (
            <>
              <TextField
                label="Change applies from month"
                type="date"
                value={fromMonth}
                onChange={(e) => setFromMonth(e.target.value)}
                helperText="Only sessions nobody has edited, from this month on, are rewritten."
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <label>
                <input
                  type="checkbox"
                  checked={confirmed}
                  onChange={(e) => setConfirmed(e.target.checked)}
                />{" "}
                I understand the coming sessions will change
              </label>
              {errors.confirm && (
                <Alert severity="warning">{errors.confirm}</Alert>
              )}
            </>
          ) : (
            <TextField
              label="Start month"
              type="date"
              value={startMonth}
              onChange={(e) => setStartMonth(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          )}
          <Button onClick={() => void showPreview()}>
            Preview the next months
          </Button>
          {preview && (
            <ul aria-label="Preview">
              {preview.map((row) => (
                <li key={row.month}>
                  {formatDate(row.date)}
                  {row.note ? ` - ${row.note}` : ""}
                  {row.holidayNotice && (
                    <Typography component="span" color="warning.main">
                      {" "}
                      (holiday or non-working day)
                    </Typography>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button variant="contained" onClick={() => void save()} disabled={busy}>
          {pattern ? "Save changes" : "Create pattern"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
