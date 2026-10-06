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
import { useGrantedActions } from "../common/useGrantedActions";
import { listInterviewers } from "../recruitment/recruitmentApi";
import { ProspectDialog } from "./ProspectDialog";
import {
  ACTIVITY_LABEL,
  cancelActivity,
  completeActivity,
  getProspect,
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
  const canAddProspect = useGrantedActions("/marketing/prospects").has(
    "CREATE",
  );
  const [addingProspect, setAddingProspect] = useState(false);
  const [prospects, setProspects] = useState<ProspectRow[]>([]);
  const [search, setSearch] = useState("");
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
      const a = await listInterviewers(authFetch);
      if (a.ok) setPeople(a.data);
    })();
  }, [authFetch, mode]);

  // The prospect list follows what is typed, so a School beyond the first page is still found.
  useEffect(() => {
    if (mode !== "plan" || prospect) return;
    const timer = setTimeout(() => {
      void (async () => {
        const p = await listProspects(authFetch, {
          size: 50,
          query: search.trim() || undefined,
        });
        if (p.ok) {
          setProspects((current) => {
            const chosen = current.find((c) => c.id === prospectId);
            const found = p.data.content;
            return chosen && !found.some((f) => f.id === chosen.id)
              ? [chosen, ...found]
              : found;
          });
        }
      })();
    }, 250);
    return () => clearTimeout(timer);
    // prospectId is read only to keep the chosen row listed while the search changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authFetch, mode, prospect, search]);

  const prospectAdded = async (id: string) => {
    setAddingProspect(false);
    const added = await getProspect(authFetch, id);
    if (added.ok) {
      setProspects((current) => [added.data.row, ...current]);
      setProspectId(id);
      setError(null);
    } else {
      setError(
        "The prospect was added, but could not be loaded. Search for it by name.",
      );
    }
  };

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
                <>
                  <Autocomplete
                    options={prospects}
                    value={prospects.find((p) => p.id === prospectId) ?? null}
                    getOptionLabel={(p) =>
                      p.zoneName ? `${p.name} (${p.zoneName})` : p.name
                    }
                    isOptionEqualToValue={(a, b) => a.id === b.id}
                    filterOptions={(options) => options}
                    onInputChange={(_e, value, reason) => {
                      if (reason === "input") setSearch(value);
                    }}
                    onChange={(_e, value) => setProspectId(value?.id ?? "")}
                    noOptionsText={
                      canAddProspect
                        ? "No prospect found. Use Add a new prospect."
                        : "No prospect found."
                    }
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        required
                        label="Prospect"
                        helperText="A School you are still approaching. It becomes a School in Master data when it is won."
                      />
                    )}
                  />
                  {canAddProspect && (
                    <Button
                      variant="text"
                      sx={{ alignSelf: "flex-start" }}
                      onClick={() => setAddingProspect(true)}
                    >
                      Add a new prospect
                    </Button>
                  )}
                </>
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
      {addingProspect && (
        <ProspectDialog
          onClose={() => setAddingProspect(false)}
          onSaved={(id) => void prospectAdded(id)}
        />
      )}
    </Dialog>
  );
}
