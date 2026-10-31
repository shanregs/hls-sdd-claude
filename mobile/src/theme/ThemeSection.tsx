import { useState } from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { Button, Dialog, HelperText, Portal, SegmentedButtons, Text, TextInput } from "react-native-paper";
import { findPreset } from "./builtinClassic";
import { accentRatios, adjustAccent, isHex, normaliseHex, passes } from "./contrast";
import { ThemePreview } from "./ThemePreview";
import { useThemePreference, type ThemePreference } from "./ThemeProvider";
import { minTouchTarget, spacingUnit } from "./tokens";

/** A few ready colours for those who do not want to type a code. */
const SWATCHES: [string, string][] = [
  ["#1E5AA8", "Blue"],
  ["#00796B", "Teal"],
  ["#A94E08", "Amber"],
  ["#6A1B9A", "Purple"],
  ["#B3261E", "Red"],
  ["#2E7D32", "Green"],
  ["#AD1457", "Pink"],
  ["#37474F", "Slate"],
];

/** ACCOUNT → Profile → Appearance (spec 039): light or dark, preset, own accent with a readability check, preview, reset. */
export function ThemeSection() {
  const { mode, preference, setPreference, saved, catalogue, saveTheme, resetTheme, waiting } = useThemePreference();
  const [accentText, setAccentText] = useState(saved.customAccent ?? "");
  const [accentError, setAccentError] = useState<string | null>(null);
  const [confirmReset, setConfirmReset] = useState(false);

  const preset = findPreset(catalogue, saved.preset);
  const colours = preset[mode];
  const entered = accentText.trim();
  const valid = isHex(entered);
  const hex = valid ? normaliseHex(entered) : null;
  const readable = hex ? passes(hex, colours.surface, colours.background) : false;
  const suggestion = hex && !readable ? adjustAccent(hex, mode === "dark", colours.surface, colours.background) : null;
  const ratios = hex ? accentRatios(hex, colours.surface, colours.background) : null;
  const previewAccent = hex && readable ? hex : saved.customAccent;

  async function applyAccent(value: string | null) {
    setAccentError(null);
    const outcome = await saveTheme({ customAccent: value });
    if (!outcome.ok) {
      setAccentError(
        outcome.suggested
          ? `That colour is hard to read. Try ${outcome.suggested}.`
          : "That colour is hard to read.",
      );
    }
  }

  return (
    <View style={styles.section}>
      <Text variant="titleMedium" style={styles.heading}>
        Appearance
      </Text>
      <SegmentedButtons
        value={preference}
        onValueChange={(value) => setPreference(value as ThemePreference)}
        buttons={[
          { value: "system", label: "Device", accessibilityLabel: "Follow device theme" },
          { value: "light", label: "Light", accessibilityLabel: "Light theme" },
          { value: "dark", label: "Dark", accessibilityLabel: "Dark theme" },
        ]}
      />
      {waiting ? (
        <HelperText type="info" visible accessibilityLiveRegion="polite">
          Your change will be saved when you are back online.
        </HelperText>
      ) : null}

      <Text variant="titleSmall" accessibilityRole="header">
        Theme
      </Text>
      <View accessibilityRole="radiogroup" style={styles.presets}>
        {catalogue.presets.map((p) => {
          const selected = p.id === saved.preset;
          return (
            <Pressable
              key={p.id}
              accessibilityRole="radio"
              accessibilityState={{ checked: selected }}
              accessibilityLabel={`${p.name} theme`}
              onPress={() => void saveTheme({ preset: p.id })}
              style={[styles.preset, { borderColor: selected ? colours.primary : colours.border, borderWidth: selected ? 2 : 1 }]}
            >
              <Text variant="titleSmall">{p.name}</Text>
              <View style={styles.swatchRow} importantForAccessibility="no-hide-descendants">
                {[p[mode].primary, p[mode].accent, p[mode].background, p[mode].surface].map((colour, index) => (
                  <View key={index} style={[styles.dot, { backgroundColor: colour, borderColor: colours.border }]} />
                ))}
              </View>
            </Pressable>
          );
        })}
      </View>

      <Text variant="titleSmall" accessibilityRole="header">
        Your own accent colour
      </Text>
      <TextInput
        label="Accent colour"
        value={accentText}
        onChangeText={(text) => {
          setAccentText(text);
          setAccentError(null);
        }}
        placeholder="#1E5AA8"
        autoCapitalize="characters"
        autoCorrect={false}
        maxLength={7}
        error={entered !== "" && !valid}
        accessibilityLabel="Accent colour"
      />
      {entered !== "" && !valid ? (
        <HelperText type="error" visible>
          Enter a colour like #1E5AA8.
        </HelperText>
      ) : null}
      <View style={styles.swatchGrid}>
        {SWATCHES.map(([colour, name]) => (
          <Pressable
            key={colour}
            accessibilityRole="button"
            accessibilityLabel={`${name} ${colour}`}
            onPress={() => {
              setAccentText(colour);
              setAccentError(null);
            }}
            style={styles.swatchTarget}
          >
            <View style={[styles.swatch, { backgroundColor: colour, borderColor: colours.border }]} />
          </Pressable>
        ))}
      </View>
      {hex && ratios && readable ? (
        <HelperText type="info" visible accessibilityLiveRegion="polite">
          Easy to read in {mode} mode (text on surface {ratios.accentOnSurface.toFixed(2)} : 1, header text{" "}
          {ratios.headerTextOnAccent.toFixed(2)} : 1). In the other mode the colour is adjusted automatically if it needs to be.
        </HelperText>
      ) : null}
      {hex && ratios && !readable ? (
        <View>
          <HelperText type="error" visible accessibilityLiveRegion="assertive">
            {hex} is hard to read in {mode} mode (text on surface {ratios.accentOnSurface.toFixed(2)} : 1, needs 4.5 : 1).
            {suggestion ? ` A similar colour that works is ${suggestion}.` : " No similar colour works; try another."}
          </HelperText>
          {suggestion ? (
            <Button mode="outlined" onPress={() => setAccentText(suggestion)} contentStyle={styles.buttonContent} accessibilityLabel={`Use ${suggestion}`}>
              Use {suggestion}
            </Button>
          ) : null}
        </View>
      ) : null}
      {accentError ? (
        <HelperText type="error" visible accessibilityLiveRegion="assertive">
          {accentError}
        </HelperText>
      ) : null}
      <View style={styles.actions}>
        <Button
          mode="contained"
          disabled={!hex || !readable}
          onPress={() => hex && void applyAccent(hex)}
          contentStyle={styles.buttonContent}
          accessibilityLabel="Use this colour"
        >
          Use this colour
        </Button>
        <Button
          mode="text"
          disabled={!saved.customAccent && entered === ""}
          onPress={() => {
            setAccentText("");
            setAccentError(null);
            if (saved.customAccent) void applyAccent(null);
          }}
          contentStyle={styles.buttonContent}
          accessibilityLabel="Clear accent colour"
        >
          Clear
        </Button>
      </View>

      <Text variant="titleSmall" accessibilityRole="header">
        Preview
      </Text>
      <ThemePreview mode={mode} preset={preset} catalogue={catalogue} customAccent={previewAccent} />

      <Button mode="outlined" onPress={() => setConfirmReset(true)} contentStyle={styles.buttonContent} accessibilityLabel="Reset to default">
        Reset to default
      </Button>
      <Portal>
        <Dialog visible={confirmReset} onDismiss={() => setConfirmReset(false)}>
          <Dialog.Title>Reset your theme?</Dialog.Title>
          <Dialog.Content>
            <Text>This returns you to the Classic theme, the same as your device, and removes your own accent colour.</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setConfirmReset(false)} accessibilityLabel="Cancel reset">
              Cancel
            </Button>
            <Button
              onPress={() => {
                setConfirmReset(false);
                setAccentText("");
                setAccentError(null);
                void resetTheme();
              }}
              accessibilityLabel="Confirm reset"
            >
              Reset
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { gap: spacingUnit },
  heading: { marginTop: spacingUnit * 2 },
  presets: { gap: spacingUnit },
  preset: { minHeight: minTouchTarget, borderRadius: 12, padding: spacingUnit * 1.5, gap: spacingUnit / 2 },
  swatchRow: { flexDirection: "row", gap: 4 },
  dot: { width: 20, height: 20, borderRadius: 4, borderWidth: 1 },
  swatchGrid: { flexDirection: "row", flexWrap: "wrap" },
  swatchTarget: { width: minTouchTarget, height: minTouchTarget, alignItems: "center", justifyContent: "center" },
  swatch: { width: 32, height: 32, borderRadius: 16, borderWidth: 1 },
  actions: { flexDirection: "row", gap: spacingUnit, flexWrap: "wrap" },
  buttonContent: { minHeight: minTouchTarget },
});
