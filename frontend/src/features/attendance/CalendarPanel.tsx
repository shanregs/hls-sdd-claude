import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  FormGroup,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  addNonWorkingDate,
  getCalendar,
  removeNonWorkingDate,
  removeSchoolOverride,
  saveDefaultWeeklyOff,
  saveSchoolOverride,
  type AttendanceCalendar,
} from "./attendanceApi";
import { formatDate, WEEKDAY_CODES, WEEKDAY_LABELS } from "./monthUtils";
import { listSchools, type SchoolSummary } from "../schools/schoolsApi";

function WeekdayChecks({
  label,
  value,
  onChange,
  disabled,
}: {
  label: string;
  value: string[];
  onChange: (next: string[]) => void;
  disabled?: boolean;
}) {
  return (
    <FormGroup row aria-label={label}>
      {WEEKDAY_CODES.map((code, i) => (
        <FormControlLabel
          key={code}
          label={WEEKDAY_LABELS[i]}
          control={
            <Checkbox
              disabled={disabled}
              checked={value.includes(code)}
              onChange={(e) =>
                onChange(
                  e.target.checked
                    ? [...value, code]
                    : value.filter((c) => c !== code),
                )
              }
            />
          }
        />
      ))}
    </FormGroup>
  );
}

/** Default weekly off days, per-School overrides and organization-wide non-working dates (FR-008). */
export function CalendarPanel({ canEdit }: { canEdit: boolean }) {
  const { authFetch } = useAuth();
  const [calendar, setCalendar] = useState<AttendanceCalendar | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [defaultOff, setDefaultOff] = useState<string[]>([]);
  const [schools, setSchools] = useState<SchoolSummary[]>([]);
  const [overrideSchool, setOverrideSchool] = useState("");
  const [overrideOff, setOverrideOff] = useState<string[]>(["SUN"]);
  const [holidayDate, setHolidayDate] = useState("");
  const [holidayText, setHolidayText] = useState("");

  const apply = (result: Awaited<ReturnType<typeof getCalendar>>): boolean => {
    if (!result.ok) {
      setError(result.reason);
      return false;
    }
    setError(null);
    setCalendar(result.data);
    setDefaultOff(result.data.defaultWeeklyOff);
    return true;
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await getCalendar(authFetch);
      if (cancelled) return;
      apply(result);
      const schoolList = await listSchools(authFetch, {
        query: "",
        zoneId: "",
        active: "true",
        page: 0,
        size: 100,
      });
      if (!cancelled && schoolList.ok) setSchools(schoolList.data.content);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  if (calendar === null) {
    return (
      <Box component="section" aria-labelledby="calendar-heading">
        <Typography variant="h6" component="h2" id="calendar-heading">
          Non-working calendar
        </Typography>
        {error ? (
          <Alert severity="error" role="alert">
            {error}
          </Alert>
        ) : (
          <Typography>Loading the calendar…</Typography>
        )}
      </Box>
    );
  }

  return (
    <Box component="section" aria-labelledby="calendar-heading">
      <Typography
        variant="h6"
        component="h2"
        id="calendar-heading"
        sx={{ mb: 1 }}
      >
        Non-working calendar
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mb: 1 }} role="alert">
          {error}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Typography variant="subtitle1" component="h3">
          Default weekly off days
        </Typography>
        <WeekdayChecks
          label="Default weekly off days"
          value={defaultOff}
          onChange={setDefaultOff}
          disabled={!canEdit}
        />
        {canEdit && (
          <Button
            variant="contained"
            onClick={async () =>
              apply(
                await saveDefaultWeeklyOff(
                  authFetch,
                  defaultOff,
                  calendar.defaultVersion,
                ),
              )
            }
          >
            Save default
          </Button>
        )}
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Typography variant="subtitle1" component="h3">
          School overrides
        </Typography>
        {calendar.schoolOverrides.length === 0 && (
          <Typography variant="body2">
            No School has its own weekly off days.
          </Typography>
        )}
        {calendar.schoolOverrides.map((o) => (
          <Stack
            key={o.schoolId}
            direction="row"
            spacing={2}
            sx={{ alignItems: "center", py: 0.5 }}
          >
            <Typography sx={{ minWidth: 200 }}>{o.schoolName}</Typography>
            <Typography variant="body2">
              {o.weeklyOff.length ? o.weeklyOff.join(", ") : "No off days"}
            </Typography>
            {canEdit && (
              <Button
                size="small"
                color="error"
                onClick={async () =>
                  apply(await removeSchoolOverride(authFetch, o.schoolId))
                }
              >
                Remove override for {o.schoolName}
              </Button>
            )}
          </Stack>
        ))}
        {canEdit && (
          <Box sx={{ mt: 2 }}>
            <TextField
              select
              size="small"
              label="School"
              value={overrideSchool}
              onChange={(e) => setOverrideSchool(e.target.value)}
              sx={{ minWidth: 240 }}
            >
              {schools.map((s) => (
                <MenuItem key={s.id} value={s.id}>
                  {s.name}
                </MenuItem>
              ))}
            </TextField>
            <WeekdayChecks
              label="Override weekly off days"
              value={overrideOff}
              onChange={setOverrideOff}
            />
            <Button
              variant="outlined"
              disabled={!overrideSchool}
              onClick={async () =>
                apply(
                  await saveSchoolOverride(
                    authFetch,
                    overrideSchool,
                    overrideOff,
                  ),
                )
              }
            >
              Save override
            </Button>
          </Box>
        )}
      </Paper>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle1" component="h3">
          Non-working dates
        </Typography>
        {calendar.nonWorkingDates.length === 0 && (
          <Typography variant="body2">No non-working dates yet.</Typography>
        )}
        {calendar.nonWorkingDates.map((d) => (
          <Stack
            key={d.date}
            direction="row"
            spacing={2}
            sx={{ alignItems: "center", py: 0.5 }}
          >
            <Typography sx={{ minWidth: 120 }}>{formatDate(d.date)}</Typography>
            <Typography variant="body2" sx={{ flexGrow: 1 }}>
              {d.description}
            </Typography>
            {canEdit && (
              <IconButton
                size="small"
                aria-label={`Remove ${formatDate(d.date)}`}
                onClick={async () =>
                  apply(await removeNonWorkingDate(authFetch, d.date))
                }
              >
                ✕
              </IconButton>
            )}
          </Stack>
        ))}
        {canEdit && (
          <Stack
            direction="row"
            spacing={2}
            sx={{ mt: 2, alignItems: "start" }}
          >
            <TextField
              size="small"
              type="date"
              label="Date"
              value={holidayDate}
              onChange={(e) => setHolidayDate(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              size="small"
              label="Description"
              value={holidayText}
              onChange={(e) => setHolidayText(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 200 } }}
              sx={{ minWidth: 240 }}
            />
            <Button
              variant="outlined"
              disabled={!holidayDate || !holidayText.trim()}
              onClick={async () => {
                if (
                  apply(
                    await addNonWorkingDate(
                      authFetch,
                      holidayDate,
                      holidayText,
                    ),
                  )
                ) {
                  setHolidayDate("");
                  setHolidayText("");
                }
              }}
            >
              Add date
            </Button>
          </Stack>
        )}
      </Paper>
    </Box>
  );
}
