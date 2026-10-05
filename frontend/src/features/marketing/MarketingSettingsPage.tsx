import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { getSettings, saveSettings, type Settings } from "./marketingApi";

const ROUTE = "/marketing/settings";

/** MARKETING -> Settings: how many days a won prospect may wait for its MoU before it is flagged (1 to 90). */
export function MarketingSettingsPage() {
  const { authFetch } = useAuth();
  const canEdit = useGrantedActions(ROUTE).has("EDIT");
  const [settings, setSettings] = useState<Settings | null>(null);
  const [days, setDays] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    void (async () => {
      const result = await getSettings(authFetch);
      if (result.ok) {
        setSettings(result.data);
        setDays(String(result.data.mouOverdueDays));
      } else {
        setError(result.reason);
      }
    })();
  }, [authFetch]);

  const save = async () => {
    const value = Number(days);
    if (!Number.isInteger(value) || value < 1 || value > 90) {
      setError("Enter a whole number of days from 1 to 90.");
      return;
    }
    const result = await saveSettings(authFetch, {
      mouOverdueDays: value,
      version: settings!.version,
    });
    if (!result.ok) {
      setError(result.reason);
      setSaved(false);
      return;
    }
    setError(null);
    setSaved(true);
    setSettings(result.data);
  };

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2} sx={{ maxWidth: 480 }}>
        <Typography variant="h5" component="h1">
          Marketing settings
        </Typography>
        {error && (
          <Alert severity="error" role="alert">
            {error}
          </Alert>
        )}
        {saved && (
          <Alert severity="success" role="status">
            Saved.
          </Alert>
        )}
        {!settings && !error && (
          <Typography role="status">Loading the settings...</Typography>
        )}
        {settings && (
          <>
            <TextField
              label="Days until a won prospect without an MoU is flagged"
              type="number"
              value={days}
              onChange={(e) => {
                setDays(e.target.value);
                setSaved(false);
              }}
              disabled={!canEdit}
              slotProps={{ htmlInput: { min: 1, max: 90 } }}
              helperText="The owner, the Zone Manager, the Admin and the Director are told once when the limit passes."
            />
            {canEdit && (
              <Button
                variant="contained"
                onClick={() => void save()}
                sx={{ alignSelf: "flex-start" }}
              >
                Save
              </Button>
            )}
          </>
        )}
      </Stack>
    </Paper>
  );
}
