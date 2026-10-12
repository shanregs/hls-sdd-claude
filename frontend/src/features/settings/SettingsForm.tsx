import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDateTime } from "./format";
import {
  getSettings,
  problemWith,
  saveSettings,
  type SettingsGroup,
  type SettingView,
} from "./settingsApi";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; settings: SettingView[] };

interface Props {
  group: SettingsGroup;
  route: string;
  title: string;
  intro: string;
}

/** One editable screen of runtime settings, driven by the server's catalog (spec 011 FR-003, FR-014). */
export function SettingsForm({ group, route, title, intro }: Props) {
  const { authFetch } = useAuth();
  const actions = useGrantedActions(route);
  const canEdit = actions.has("EDIT");
  const [state, setState] = useState<State>({ kind: "loading" });
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<{
    severity: "success" | "error";
    text: string;
  } | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void (async () => {
      const result = await getSettings(authFetch, group);
      if (!result.ok) {
        setState({ kind: "error", reason: result.reason });
        return;
      }
      setState({ kind: "ready", settings: result.data.settings });
      setDraft(
        Object.fromEntries(result.data.settings.map((s) => [s.key, s.value])),
      );
    })();
  }, [authFetch, group]);

  if (state.kind === "loading") {
    return <CircularProgress aria-label="Loading settings" />;
  }
  if (state.kind === "error") {
    return <Alert severity="error">{state.reason}</Alert>;
  }

  const dirty = state.settings.filter((s) => draft[s.key] !== s.value);

  async function onSave() {
    if (state.kind !== "ready") return;
    const problems: Record<string, string> = {};
    for (const setting of dirty) {
      const problem = problemWith(setting, draft[setting.key] ?? "");
      if (problem) problems[setting.key] = problem;
    }
    setErrors(problems);
    setMessage(null);
    if (Object.keys(problems).length > 0) return;
    setSaving(true);
    const values = Object.fromEntries(
      dirty.map((s) => [s.key, (draft[s.key] ?? "").trim()]),
    );
    const result = await saveSettings(authFetch, group, values);
    setSaving(false);
    if (!result.ok) {
      setErrors(result.fields);
      setMessage({ severity: "error", text: result.reason });
      return;
    }
    setState({ kind: "ready", settings: result.data.settings });
    setDraft(
      Object.fromEntries(result.data.settings.map((s) => [s.key, s.value])),
    );
    setMessage({ severity: "success", text: "Settings saved." });
  }

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        {title}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {intro}
      </Typography>
      {message && (
        <Alert severity={message.severity} sx={{ mb: 2 }}>
          {message.text}
        </Alert>
      )}
      <Stack spacing={2} sx={{ maxWidth: 640 }}>
        {state.settings.map((s) => (
          <Paper key={s.key} variant="outlined" sx={{ p: 2 }}>
            <TextField
              label={s.label}
              value={draft[s.key] ?? ""}
              onChange={(e) =>
                setDraft((d) => ({ ...d, [s.key]: e.target.value }))
              }
              disabled={!canEdit}
              error={Boolean(errors[s.key])}
              helperText={
                errors[s.key] ??
                (s.type === "VERSION"
                  ? "Format 1.2.0"
                  : `${s.min} to ${s.max} ${s.unit}`)
              }
              fullWidth
              slotProps={{ htmlInput: { inputMode: "numeric" } }}
            />
            <Typography variant="caption" color="text.secondary">
              {s.isDefault ? "Default" : `Default is ${s.default}`}
              {s.updatedBy && s.updatedAt
                ? ` · Changed by ${s.updatedBy} on ${formatDateTime(s.updatedAt)}`
                : ""}
            </Typography>
          </Paper>
        ))}
        {canEdit && (
          <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
            <Button
              variant="contained"
              onClick={() => void onSave()}
              disabled={dirty.length === 0 || saving}
            >
              Save changes
            </Button>
            <Button
              onClick={() => {
                setDraft(
                  Object.fromEntries(
                    state.settings.map((s) => [s.key, s.value]),
                  ),
                );
                setErrors({});
                setMessage(null);
              }}
              disabled={dirty.length === 0 || saving}
            >
              Discard
            </Button>
            {dirty.length > 0 && (
              <Typography variant="body2" color="text.secondary">
                {dirty.length} unsaved {dirty.length === 1 ? "change" : "changes"}
              </Typography>
            )}
          </Stack>
        )}
      </Stack>
    </Box>
  );
}
