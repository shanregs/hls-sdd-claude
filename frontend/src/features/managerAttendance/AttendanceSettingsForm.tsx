import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { validateSettings } from "./formRules";
import {
  getSettings,
  saveSettings,
  type AttendanceSettings,
} from "./managerAttendanceApi";

/** The late time and the cut-off after which a missing check-in is reported (spec 032 US4); shown with EDIT only. */
export function AttendanceSettingsForm() {
  const { authFetch } = useAuth();
  const [saved, setSaved] = useState<AttendanceSettings | null>(null);
  const [values, setValues] = useState({ missedCutoff: "", lateAfter: "" });
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  useEffect(() => {
    void (async () => {
      const result = await getSettings(authFetch);
      if (!result.ok) {
        setMessage(result.reason);
        return;
      }
      setSaved(result.data);
      setValues({
        missedCutoff: result.data.missedCutoff,
        lateAfter: result.data.lateAfter,
      });
    })();
  }, [authFetch]);

  const problem = validateSettings(values);

  const save = async () => {
    setTouched(true);
    setMessage(null);
    setDone(false);
    if (problem || !saved) return;
    setBusy(true);
    const result = await saveSettings(authFetch, {
      ...values,
      version: saved.version,
    });
    setBusy(false);
    if (!result.ok) {
      setMessage(result.reason);
      return;
    }
    setSaved(result.data);
    setDone(true);
  };

  return (
    <Paper
      variant="outlined"
      sx={{ p: 2, mb: 2 }}
      component="section"
      aria-labelledby="settings-title"
    >
      <Typography id="settings-title" variant="h6" component="h2" gutterBottom>
        Check-in times
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        A check-in after the late time is marked late. A Manager with no
        check-in by the cut-off is reported as missed.
      </Typography>
      <Stack
        spacing={2}
        component="form"
        noValidate
        onSubmit={(e) => e.preventDefault()}
        aria-label="Check-in times"
      >
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
        {done && <Alert severity="success">The times were saved.</Alert>}
        <Stack direction="row" spacing={2}>
          <TextField
            label="Late after"
            type="time"
            value={values.lateAfter}
            onChange={(e) =>
              setValues((v) => ({ ...v, lateAfter: e.target.value }))
            }
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Missed cut-off"
            type="time"
            value={values.missedCutoff}
            onChange={(e) =>
              setValues((v) => ({ ...v, missedCutoff: e.target.value }))
            }
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Stack>
        <Box>
          <Button
            variant="contained"
            disabled={busy || !saved}
            onClick={() => void save()}
          >
            Save times
          </Button>
        </Box>
      </Stack>
    </Paper>
  );
}
