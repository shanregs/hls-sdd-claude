import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormLabel,
  Paper,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate } from "../teachers/formatters";
import {
  checkIn,
  checkOut,
  completeOwnCheckOut,
  getToday,
  uploadPhoto,
  type DayType,
  type ManagerLocation,
  type TodayStatus,
} from "./managerAttendanceApi";

const MAX_PURPOSE = 500;
const ROUTE = "/manager-attendance/me";

export const LOCATION_BLOCKED =
  "Your location is blocked. Allow location access for this site in your browser settings, then try again.";
const LOCATION_UNAVAILABLE =
  "Your location could not be found. Check that location is switched on and try again.";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; today: TodayStatus };

function readLocation(): Promise<ManagerLocation> {
  return new Promise((resolve, reject) => {
    if (!("geolocation" in navigator)) {
      reject(new Error(LOCATION_UNAVAILABLE));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (p) =>
        resolve({
          latitude: p.coords.latitude,
          longitude: p.coords.longitude,
          accuracyMeters: p.coords.accuracy,
        }),
      (e) =>
        reject(
          new Error(
            e.code === 1 /* PERMISSION_DENIED */
              ? LOCATION_BLOCKED
              : LOCATION_UNAVAILABLE,
          ),
        ),
      { enableHighAccuracy: true, timeout: 15000 },
    );
  });
}

function timeOf(iso: string | null): string {
  return iso
    ? new Date(iso).toLocaleTimeString("en-IN", {
        hour: "2-digit",
        minute: "2-digit",
      })
    : "";
}

/** A Manager's day (spec 032 US1): check in with a location, check out, add a photo, complete a missing check-out. */
export function ManagerCheckInPage() {
  const { authFetch } = useAuth();
  const canCreate = useGrantedActions(ROUTE).has("CREATE");

  const [state, setState] = useState<State>({ kind: "loading" });
  const [dayType, setDayType] = useState<DayType>("OFFICE");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [purpose, setPurpose] = useState("");
  const [linked, setLinked] = useState<Set<string>>(new Set());
  const [purposeTouched, setPurposeTouched] = useState(false);
  const [completeTime, setCompleteTime] = useState("18:00");
  const [completeReason, setCompleteReason] = useState("");

  const load = useCallback(async () => {
    const result = await getToday(authFetch);
    setState(
      result.ok
        ? { kind: "ready", today: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch]);

  useEffect(() => {
    void (async () => {
      await load();
    })();
  }, [load]);

  const run = async (
    action: (where: ManagerLocation) => Promise<string | null>,
  ) => {
    setMessage(null);
    setBusy(true);
    try {
      const where = await readLocation();
      const problem = await action(where);
      if (problem) setMessage(problem);
      else await load();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : LOCATION_UNAVAILABLE);
    } finally {
      setBusy(false);
    }
  };

  const planned = state.kind === "ready" ? state.today.plannedActivities : [];
  const activityKey = (a: { kind: string; id: string }) => `${a.kind}:${a.id}`;
  const purposeError =
    dayType !== "FIELD"
      ? null
      : purpose.length > MAX_PURPOSE
        ? `The purpose can be at most ${MAX_PURPOSE} characters.`
        : purpose.trim() === "" && linked.size === 0
          ? "Write a purpose or link a planned activity."
          : null;

  const toggleLink = (key: string) =>
    setLinked((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const doCheckIn = () => {
    if (purposeError) {
      setPurposeTouched(true);
      return Promise.resolve();
    }
    return run(async (where) => {
      const body =
        dayType === "FIELD"
          ? {
              dayType,
              purpose: purpose.trim() === "" ? null : purpose.trim(),
              activities: planned
                .filter((a) => linked.has(activityKey(a)))
                .map((a) => ({ kind: a.kind, id: a.id })),
              ...where,
            }
          : { dayType, ...where };
      const result = await checkIn(authFetch, body);
      return result.ok ? null : result.reason;
    });
  };

  const doCheckOut = () =>
    run(async (where) => {
      const result = await checkOut(authFetch, where);
      return result.ok ? null : result.reason;
    });

  const doPhoto = async (file: File | undefined, date: string) => {
    if (!file) return;
    setMessage(null);
    setBusy(true);
    const result = await uploadPhoto(authFetch, date, file);
    setBusy(false);
    if (!result.ok) setMessage(result.reason);
    else await load();
  };

  const doComplete = async (date: string) => {
    setMessage(null);
    if (completeReason.trim() === "") {
      setMessage("A reason is required to complete a check-out.");
      return;
    }
    setBusy(true);
    const result = await completeOwnCheckOut(authFetch, date, {
      time: completeTime,
      reason: completeReason.trim(),
    });
    setBusy(false);
    if (!result.ok) setMessage(result.reason);
    else {
      setCompleteReason("");
      await load();
    }
  };

  if (state.kind === "loading") {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <CircularProgress aria-label="Loading today" />
      </Box>
    );
  }
  if (state.kind === "error") {
    return (
      <Alert severity="error" role="alert">
        {state.reason}
      </Alert>
    );
  }

  const { today } = state;
  const day = today.day;
  const checkedIn = day?.checkInAt != null;
  const checkedOut = day?.checkOutAt != null;

  return (
    <Box sx={{ maxWidth: 560 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        Check-in
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {formatDate(today.date)}
        {today.workingDay ? "" : " (not a working day)"}
      </Typography>

      {message && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {message}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          Today
        </Typography>
        {today.leave && (
          <Alert severity="info" sx={{ mb: 1 }}>
            {today.leave.fraction >= 1
              ? "You are on leave today."
              : "You are on half-day leave today."}
          </Alert>
        )}
        {!checkedIn && (
          <Typography sx={{ mb: 2 }}>
            You have not checked in. A check-in after{" "}
            {today.lateAfter.slice(0, 5)} is marked late.
          </Typography>
        )}
        {checkedIn && day && (
          <Stack spacing={0.5} sx={{ mb: 2 }}>
            <Typography>
              Checked in at {timeOf(day.checkInAt)} (
              {day.dayType === "FIELD" ? "Field" : "Office"})
            </Typography>
            {day.purpose && <Typography>Purpose: {day.purpose}</Typography>}
            {checkedOut && (
              <Typography>Checked out at {timeOf(day.checkOutAt)}</Typography>
            )}
            <Stack direction="row" spacing={1}>
              {day.late && <Chip size="small" color="warning" label="Late" />}
              {day.hasPhoto && <Chip size="small" label="Photo added" />}
              {day.flags.includes("UNLINKED") && (
                <Chip size="small" label="Not linked to a planned activity" />
              )}
            </Stack>
          </Stack>
        )}

        {!checkedIn && canCreate && (
          <Stack spacing={2}>
            <FormControl>
              <FormLabel id="day-type-label">Day type</FormLabel>
              <RadioGroup
                row
                aria-labelledby="day-type-label"
                value={dayType}
                onChange={(e) => setDayType(e.target.value as DayType)}
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
            {dayType === "FIELD" && (
              <>
                <TextField
                  label="Purpose"
                  multiline
                  minRows={2}
                  value={purpose}
                  onChange={(e) => setPurpose(e.target.value)}
                  onBlur={() => setPurposeTouched(true)}
                  error={purposeTouched && purposeError !== null}
                  helperText={
                    (purposeTouched && purposeError ? `${purposeError} ` : "") +
                    `${purpose.length}/${MAX_PURPOSE}`
                  }
                />
                {planned.length > 0 && (
                  <FormControl component="fieldset">
                    <FormLabel component="legend">
                      Planned activities today
                    </FormLabel>
                    <FormGroup>
                      {planned.map((a) => (
                        <FormControlLabel
                          key={activityKey(a)}
                          control={
                            <Checkbox
                              checked={linked.has(activityKey(a))}
                              onChange={() => toggleLink(activityKey(a))}
                            />
                          }
                          label={`${a.kind.replace(/_/g, " ").toLowerCase()}${
                            a.place ? `: ${a.place}` : ""
                          }`}
                        />
                      ))}
                    </FormGroup>
                  </FormControl>
                )}
              </>
            )}
            <Button variant="contained" onClick={doCheckIn} disabled={busy}>
              Check in
            </Button>
          </Stack>
        )}

        {checkedIn && !checkedOut && canCreate && (
          <Button variant="contained" onClick={doCheckOut} disabled={busy}>
            Check out
          </Button>
        )}

        {checkedIn && day && !day.hasPhoto && canCreate && (
          <Box sx={{ mt: 2 }}>
            <Button variant="outlined" component="label" disabled={busy}>
              Add a photo (optional)
              <input
                hidden
                type="file"
                accept="image/jpeg,image/png"
                aria-label="Photo file"
                onChange={(e) => void doPhoto(e.target.files?.[0], today.date)}
              />
            </Button>
          </Box>
        )}
      </Paper>

      {today.missingCheckOutDates.length > 0 && canCreate && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="h6" component="h2" gutterBottom>
            No check-out recorded
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            These days count as worked. Add the time you left and a reason.
          </Typography>
          <Stack spacing={2}>
            <TextField
              label="Time you left"
              type="time"
              value={completeTime}
              onChange={(e) => setCompleteTime(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Reason"
              value={completeReason}
              onChange={(e) => setCompleteReason(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
            {today.missingCheckOutDates.map((date) => (
              <Button
                key={date}
                variant="outlined"
                disabled={busy}
                onClick={() => void doComplete(date)}
              >
                Complete check-out for {formatDate(date)}
              </Button>
            ))}
          </Stack>
        </Paper>
      )}
    </Box>
  );
}
