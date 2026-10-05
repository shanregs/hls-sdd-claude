import { useEffect, useState } from "react";
import {
  Alert,
  Autocomplete,
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
import { listInterviewers } from "../recruitment/recruitmentApi";
import {
  ACTIVITY_LABEL,
  cancelActivity,
  completeActivity,
  listProspects,
  planActivity,
  rescheduleActivity,
  type Activity,
  type ActivityType,
  type PersonRef,
  type ProspectRow,
} from "./marketingApi";

export type ActivityMode = "plan" | "complete" | "reschedule" | "cancel";

interface ActivityDialogProps {
  mode: ActivityMode;
  /** The activity being completed, rescheduled or cancelled. */
  activity?: Activity;
  /** A prospect already chosen (planning from a prospect's own screen). */
  prospect?: { id: string; name: string };
  onClose: () => void;
  onSaved: () => void;
}

const TITLES: Record<ActivityMode, string> = {
  plan: "Plan an activity",
  complete: "Complete the activity",
  reschedule: "Reschedule the activity",
  cancel: "Cancel the activity",
};

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

/** Plan, complete (an outcome is required), reschedule (the earlier date stays) or cancel (a reason is required). */
export function ActivityDialog({
  mode,
  activity,
  prospect,
  onClose,
  onSaved,
}: ActivityDialogProps) {
  const { authFetch } = useAuth();
  const [prospects, setProspects] = useState<ProspectRow[]>([]);
  const [people, setPeople] = useState<PersonRef[]>([]);
  const [prospectId, setProspectId] = useState(prospect?.id ?? "");
  const [type, setType] = useState<ActivityType>("VISIT");
  const [date, setDate] = useState(mode === "reschedule" ? "" : todayIso());
  const [attendees, setAttendees] = useState<PersonRef[]>([]);
  const [notes, setNotes] = useState("");
  const [outcome, setOutcome] = useState("");
  const [followUpOn, setFollowUpOn] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (mode !== "plan") return;
    void (async () => {
      const [p, a] = await Promise.all([
        prospect
          ? Promise.resolve(null)
          : listProspects(authFetch, { size: 100 }),
        listInterviewers(authFetch),
      ]);
      if (p && p.ok) setProspects(p.data.content);
      if (a.ok) setPeople(a.data);
    })();
  }, [authFetch, mode, prospect]);

  const save = async () => {
    setSaving(true);
    let result;
    if (mode === "plan") {
      if (!prospectId) {
        setSaving(false);
        setError("Choose the prospect.");
        return;
      }
      if (!date) {
        setSaving(false);
        setError("Choose the date.");
        return;
      }
      result = await planActivity(authFetch, {
        prospectId,
        type,
        date,
        attendeeUserIds: attendees.map((a) => a.userId),
        notes: notes || undefined,
      });
    } else if (mode === "complete") {
      if (!outcome.trim()) {
        setSaving(false);
        setError(
          "An outcome is required: who was met, what was discussed and the next action.",
        );
        return;
      }
      result = await completeActivity(
        authFetch,
        activity!.id,
        outcome,
        followUpOn || undefined,
        notes || undefined,
      );
    } else if (mode === "reschedule") {
      if (!date) {
        setSaving(false);
        setError("Choose the new date.");
        return;
      }
      result = await rescheduleActivity(authFetch, activity!.id, date);
    } else {
      if (!reason.trim()) {
        setSaving(false);
        setError("A reason is required to cancel.");
        return;
      }
      result = await cancelActivity(authFetch, activity!.id, reason);
    }
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
      aria-labelledby="activity-title"
    >
      <DialogTitle id="activity-title">{TITLES[mode]}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          {mode === "plan" && (
            <>
              {prospect ? (
                <TextField label="Prospect" value={prospect.name} disabled />
              ) : (
                <TextField
                  select
                  required
                  label="Prospect"
                  value={prospectId}
                  onChange={(e) => setProspectId(e.target.value)}
                >
                  {prospects.map((p) => (
                    <MenuItem key={p.id} value={p.id}>
                      {p.name}
                    </MenuItem>
                  ))}
                </TextField>
              )}
              <TextField
                select
                label="Type"
                value={type}
                onChange={(e) => setType(e.target.value as ActivityType)}
              >
                {(Object.keys(ACTIVITY_LABEL) as ActivityType[]).map((t) => (
                  <MenuItem key={t} value={t}>
                    {ACTIVITY_LABEL[t]}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                required
                type="date"
                label="Date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <Autocomplete
                multiple
                options={people}
                value={attendees}
                getOptionLabel={(p) => p.name}
                isOptionEqualToValue={(a, b) => a.userId === b.userId}
                onChange={(_e, value) => setAttendees(value)}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Who is going"
                    helperText="Leave empty to go yourself."
                  />
                )}
              />
              <TextField
                label="Notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                multiline
                minRows={2}
              />
            </>
          )}
          {mode === "complete" && (
            <>
              <TextField
                required
                label="Outcome"
                helperText="Who was met, what was discussed and the next action."
                value={outcome}
                onChange={(e) => setOutcome(e.target.value)}
                multiline
                minRows={3}
              />
              <TextField
                type="date"
                label="Follow up on"
                value={followUpOn}
                onChange={(e) => setFollowUpOn(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                label="Notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </>
          )}
          {mode === "reschedule" && (
            <TextField
              required
              type="date"
              label="New date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
              helperText="The earlier date stays in the history."
            />
          )}
          {mode === "cancel" && (
            <TextField
              required
              label="Reason for cancelling"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Back</Button>
        <Button
          variant="contained"
          color={mode === "cancel" ? "error" : "primary"}
          onClick={() => void save()}
          disabled={saving}
        >
          {mode === "plan"
            ? "Plan"
            : mode === "complete"
              ? "Complete"
              : mode === "reschedule"
                ? "Reschedule"
                : "Cancel activity"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
