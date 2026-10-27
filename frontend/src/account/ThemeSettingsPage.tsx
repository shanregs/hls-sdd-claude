import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  ButtonBase,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from "@mui/material";
import {
  accentRatios,
  adjustAccent,
  isHex,
  normaliseHex,
  passes,
} from "../theme/contrast";
import { useThemeMode } from "../theme/ThemeModeProvider";
import type { PreferredMode } from "../theme/themeApi";
import { findPreset } from "../theme/tokens";
import { ThemePreview } from "./ThemePreview";

const MODE_LABELS: [PreferredMode, string][] = [
  ["light", "Light"],
  ["dark", "Dark"],
  ["device", "Same as my device"],
];

/** Settings, Theme tab (spec 039): preset, mode, own accent with a readability check, preview and reset. */
export function ThemeSettingsPage() {
  const {
    mode,
    preference,
    catalogue,
    savePreference,
    resetPreference,
    unsaved,
    retrySave,
  } = useThemeMode();
  const [accentText, setAccentText] = useState(preference.customAccent ?? "");
  const [accentError, setAccentError] = useState<string | null>(null);
  const [confirmReset, setConfirmReset] = useState(false);

  const preset = findPreset(catalogue, preference.preset);
  const colours = preset[mode];
  const entered = accentText.trim();
  const valid = isHex(entered);
  const hex = valid ? normaliseHex(entered) : null;
  const readable = hex
    ? passes(hex, colours.surface, colours.background)
    : false;
  const suggestion =
    hex && !readable
      ? adjustAccent(hex, mode === "dark", colours.surface, colours.background)
      : null;
  const ratios = hex
    ? accentRatios(hex, colours.surface, colours.background)
    : null;
  // The preview follows what the user is typing as soon as it is a colour that passes.
  const previewAccent = hex && readable ? hex : preference.customAccent;

  const applyAccent = async (value: string | null) => {
    setAccentError(null);
    const result = await savePreference({ customAccent: value });
    if (result && !result.ok && result.code === "ACCENT_UNREADABLE") {
      setAccentError(
        result.suggested
          ? `That colour is hard to read. Try ${result.suggested}.`
          : "That colour is hard to read.",
      );
    } else if (result && !result.ok && result.code !== "NETWORK") {
      setAccentError(result.reason);
    }
  };

  return (
    <Stack spacing={3} sx={{ maxWidth: 720 }}>
      <Typography color="text.secondary">
        Your theme is saved to your account and follows you to the web and the
        phone app.
      </Typography>
      {unsaved && (
        <Alert
          severity="warning"
          role="alert"
          action={
            <Button
              color="inherit"
              size="small"
              onClick={() => void retrySave()}
            >
              Retry
            </Button>
          }
        >
          Your theme change is applied here but could not be saved yet.
        </Alert>
      )}

      <Box component="section" aria-labelledby="theme-mode-heading">
        <Typography id="theme-mode-heading" variant="subtitle1" gutterBottom>
          Light or dark
        </Typography>
        <ToggleButtonGroup
          exclusive
          size="small"
          value={preference.mode}
          aria-label="Colour mode"
          onChange={(_, next: PreferredMode | null) => {
            if (next) void savePreference({ mode: next });
          }}
        >
          {MODE_LABELS.map(([value, label]) => (
            <ToggleButton key={value} value={value}>
              {label}
            </ToggleButton>
          ))}
        </ToggleButtonGroup>
      </Box>

      <Box component="section" aria-labelledby="theme-preset-heading">
        <Typography id="theme-preset-heading" variant="subtitle1" gutterBottom>
          Theme
        </Typography>
        <Box
          role="radiogroup"
          aria-labelledby="theme-preset-heading"
          sx={{
            display: "grid",
            gap: 1.5,
            gridTemplateColumns: {
              xs: "minmax(0, 1fr)",
              sm: "repeat(2, minmax(0, 1fr))",
            },
          }}
        >
          {catalogue.presets.map((p) => {
            const selected = p.id === preference.preset;
            const swatch = p[mode];
            return (
              <ButtonBase
                key={p.id}
                role="radio"
                aria-checked={selected}
                onClick={() => void savePreference({ preset: p.id })}
                sx={{
                  display: "block",
                  textAlign: "left",
                  p: 1.5,
                  borderRadius: 2,
                  border: 2,
                  borderColor: selected ? "primary.main" : "divider",
                  bgcolor: "background.paper",
                  minHeight: 48,
                }}
              >
                <Typography variant="subtitle2">{p.name}</Typography>
                <Stack direction="row" spacing={0.5} sx={{ mt: 1 }} aria-hidden>
                  {[
                    swatch.primary,
                    swatch.accent,
                    swatch.background,
                    swatch.surface,
                  ].map((colour, index) => (
                    <Box
                      key={index}
                      sx={{
                        width: 24,
                        height: 24,
                        borderRadius: 1,
                        bgcolor: colour,
                        border: 1,
                        borderColor: "divider",
                      }}
                    />
                  ))}
                </Stack>
              </ButtonBase>
            );
          })}
        </Box>
      </Box>

      <Box component="section" aria-labelledby="theme-sidebar-heading">
        <Typography id="theme-sidebar-heading" variant="subtitle1" gutterBottom>
          Sidebar
        </Typography>
        <FormControlLabel
          control={
            <Switch
              checked={preference.sidebarDark}
              onChange={(_, checked) =>
                void savePreference({ sidebarDark: checked })
              }
            />
          }
          label="Darker sidebar (web only)"
        />
      </Box>

      <Box component="section" aria-labelledby="theme-accent-heading">
        <Typography id="theme-accent-heading" variant="subtitle1" gutterBottom>
          Your own accent colour
        </Typography>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={1.5}
          sx={{ alignItems: { sm: "flex-start" } }}
        >
          <TextField
            label="Accent colour"
            size="small"
            value={accentText}
            placeholder="#1E5AA8"
            onChange={(e) => {
              setAccentText(e.target.value);
              setAccentError(null);
            }}
            error={entered !== "" && !valid}
            helperText={
              entered !== "" && !valid
                ? "Enter a colour like #1E5AA8."
                : undefined
            }
            slotProps={{ htmlInput: { maxLength: 7 } }}
            sx={{ width: { sm: 180 } }}
          />
          <input
            type="color"
            aria-label="Pick a colour"
            value={hex ? hex.toLowerCase() : "#1e5aa8"}
            onChange={(e) => {
              setAccentText(e.target.value.toUpperCase());
              setAccentError(null);
            }}
            style={{ width: 48, height: 40, padding: 0, border: 0 }}
          />
          <Button
            variant="contained"
            disabled={!hex || !readable}
            onClick={() => hex && void applyAccent(hex)}
          >
            Use this colour
          </Button>
          <Button
            disabled={!preference.customAccent && entered === ""}
            onClick={() => {
              setAccentText("");
              setAccentError(null);
              if (preference.customAccent) void applyAccent(null);
            }}
          >
            Clear
          </Button>
        </Stack>
        {accentError && (
          <Alert severity="error" role="alert" sx={{ mt: 1.5 }}>
            {accentError}
          </Alert>
        )}
        {hex && ratios && readable && (
          <Alert severity="success" sx={{ mt: 1.5 }}>
            Easy to read in {mode} mode (text on surface{" "}
            {ratios.accentOnSurface.toFixed(2)} : 1, header text{" "}
            {ratios.headerTextOnAccent.toFixed(2)} : 1). In the other mode the
            colour is adjusted automatically if it needs to be.
          </Alert>
        )}
        {hex && ratios && !readable && (
          <Alert
            severity="warning"
            role="alert"
            sx={{ mt: 1.5 }}
            action={
              suggestion ? (
                <Button
                  color="inherit"
                  size="small"
                  onClick={() => setAccentText(suggestion)}
                >
                  Use {suggestion}
                </Button>
              ) : undefined
            }
          >
            {hex} is hard to read in {mode} mode (text on surface{" "}
            {ratios.accentOnSurface.toFixed(2)} : 1, needs 4.5 : 1).
            {suggestion
              ? ` A similar colour that works is ${suggestion}.`
              : " No similar colour works; try another."}
          </Alert>
        )}
      </Box>

      <Box component="section" aria-labelledby="theme-preview-heading">
        <Typography id="theme-preview-heading" variant="subtitle1" gutterBottom>
          Preview
        </Typography>
        <ThemePreview
          mode={mode}
          preset={preset}
          catalogue={catalogue}
          customAccent={previewAccent}
        />
      </Box>

      <Box>
        <Button
          color="inherit"
          variant="outlined"
          onClick={() => setConfirmReset(true)}
        >
          Reset to default
        </Button>
      </Box>
      <Dialog open={confirmReset} onClose={() => setConfirmReset(false)}>
        <DialogTitle>Reset your theme?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            This returns you to the Classic theme, the same as your device, and
            removes your own accent colour.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmReset(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={async () => {
              setConfirmReset(false);
              setAccentText("");
              setAccentError(null);
              await resetPreference();
            }}
          >
            Reset
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
