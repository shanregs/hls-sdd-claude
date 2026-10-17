import { Pressable, StyleSheet, View } from "react-native";
import { Text, useTheme } from "react-native-paper";
import type { Visit } from "../api/marketingApi";
import { formatIsoDate } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { visitKindText, visitStatusText } from "./visitMessages";

/** The name of the School or prospect a visit is with. */
export function visitTarget(visit: Visit): string {
  return visit.prospect?.name ?? visit.school?.name ?? "Unknown";
}

/** What a row says aloud: the kind, the target, the date and the status the server gave. */
export function visitSummary(visit: Visit): string {
  return `${visitKindText(visit.type)} with ${visitTarget(visit)}, ${formatIsoDate(visit.date)}, ${visitStatusText(visit.effectiveStatus).toLowerCase()}`;
}

/** One visit in the month list: target, date, kind and the server's status (Missed is marked in words, not by colour alone). */
export function VisitCard({ visit, onPress }: { visit: Visit; onPress: () => void }) {
  const theme = useTheme();
  const missed = visit.effectiveStatus === "MISSED";
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${visitSummary(visit)}${visit.rescheduled ? ", moved" : ""}`}
      style={[styles.card, { borderColor: missed ? theme.colors.error : theme.colors.outline, backgroundColor: theme.colors.surface }]}
    >
      <Text variant="titleMedium">{visitTarget(visit)}</Text>
      <View style={styles.line}>
        <Text variant="bodyMedium">
          {formatIsoDate(visit.date)} · {visitKindText(visit.type)}
        </Text>
        <Text variant="labelLarge" style={missed ? { color: theme.colors.error } : undefined}>
          {visitStatusText(visit.effectiveStatus)}
        </Text>
        {visit.rescheduled ? <Text variant="labelMedium">Moved</Text> : null}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { minHeight: minTouchTarget, borderWidth: 1, borderRadius: spacingUnit, padding: spacingUnit * 1.5, gap: spacingUnit / 2 },
  line: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: spacingUnit },
});
