import { StyleSheet, View } from "react-native";
import { Text } from "react-native-paper";
import type { ChipKind, ThemeCatalogue, ThemeMode, ThemePreset } from "./builtinClassic";
import { ratio } from "./contrast";
import { resolveColours, spacingUnit } from "./tokens";

interface Props {
  mode: ThemeMode;
  preset: ThemePreset;
  catalogue: ThemeCatalogue;
  /** The accent being edited (or already saved); not applied to the app until the user applies it. */
  customAccent: string | null;
}

const CHIPS: [ChipKind, string][] = [
  ["approved", "Approved"],
  ["pending", "Pending"],
  ["rejected", "Rejected"],
];

/** A small sample of the app (header, button, card, table row, status chips) in the theme being chosen, with its contrast ratios. */
export function ThemePreview({ mode, preset, catalogue, customAccent }: Props) {
  const c = resolveColours(mode, { colours: preset[mode], customAccent });
  const chips = catalogue.statusChips[mode];
  const rows: [string, number][] = [
    ["Text on surface", ratio(c.text, c.surface)],
    ["Accent colour as text on surface", ratio(c.primary, c.surface)],
    ["Header text on the header colour", ratio(c.onPrimary, c.primary)],
  ];
  return (
    <View accessibilityLabel="Theme preview" style={[styles.frame, { backgroundColor: c.background, borderColor: c.border }]}>
      <View style={[styles.header, { backgroundColor: c.primary }]}>
        <Text style={{ color: c.onPrimary, fontWeight: "700" }}>HLS Teacher Management System</Text>
      </View>
      <View style={styles.body}>
        <View style={[styles.button, { backgroundColor: c.primary }]}>
          <Text style={{ color: c.onPrimary }}>Primary button</Text>
        </View>
        <View style={[styles.card, { backgroundColor: c.surface, borderColor: c.border }]}>
          <Text style={{ color: c.text, fontWeight: "600" }}>Sample card</Text>
          <Text style={{ color: c.textMuted }}>Secondary text on the card surface.</Text>
        </View>
        <View style={[styles.row, { borderColor: c.border }]}>
          <Text style={{ color: c.text }}>Asha Admin</Text>
          <Text style={{ color: c.text }}>School One</Text>
        </View>
        <View style={styles.chips}>
          {CHIPS.map(([kind, label]) => (
            <View key={kind} style={[styles.chip, { backgroundColor: chips[kind].background }]}>
              <Text style={{ color: chips[kind].text, fontWeight: "600" }}>{label}</Text>
            </View>
          ))}
        </View>
        <View accessibilityLabel="Contrast ratios" style={styles.ratios}>
          {rows.map(([label, value]) => (
            <Text key={label} style={{ color: c.text }}>
              {label}: {value.toFixed(2)} : 1 {value >= 4.5 ? "(passes)" : "(below 4.5)"}
            </Text>
          ))}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  frame: { borderWidth: 1, borderRadius: 12, overflow: "hidden" },
  header: { paddingHorizontal: spacingUnit * 2, paddingVertical: spacingUnit * 1.5 },
  body: { padding: spacingUnit * 2, gap: spacingUnit },
  button: { alignSelf: "flex-start", borderRadius: 12, paddingHorizontal: spacingUnit * 2, paddingVertical: spacingUnit },
  card: { borderWidth: 1, borderRadius: 12, padding: spacingUnit * 1.5, gap: 2 },
  row: { flexDirection: "row", justifyContent: "space-between", borderTopWidth: 1, borderBottomWidth: 1, paddingVertical: spacingUnit },
  chips: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit },
  chip: { borderRadius: 12, paddingHorizontal: spacingUnit * 1.5, paddingVertical: 4 },
  ratios: { gap: 2 },
});
