import { StyleSheet, View } from "react-native";
import { Text } from "react-native-paper";
import type { DayHistoryEntry, MarkAction } from "../api/attendanceApi";
import { formatDateTime } from "../formats/dates";
import { spacingUnit } from "../theme/tokens";

const ACTION_TEXT: Record<MarkAction, string> = {
  CREATED: "Marked",
  CORRECTED: "Corrected",
  CLEARED: "Cleared",
};

function describe(entry: DayHistoryEntry): string {
  if (entry.action === "CLEARED" || !entry.code) return "Cleared";
  const value = entry.dayValue === 0.5 ? "half day" : "whole day";
  return `${entry.codeName ?? entry.code}, ${value}`;
}

/** Every earlier value of a day, newest first, with who changed it, when, and how. */
export function DayHistoryList({ entries }: { entries: DayHistoryEntry[] }) {
  if (entries.length === 0) {
    return <Text variant="bodyMedium">No earlier changes for this day.</Text>;
  }
  return (
    <View style={styles.list} accessibilityLabel="Day history">
      {entries.map((entry, index) => (
        <View
          key={`${entry.setAt}-${index}`}
          accessible
          accessibilityLabel={`${ACTION_TEXT[entry.action]}: ${describe(entry)}, by ${entry.setByName} on ${formatDateTime(entry.setAt)}`}
        >
          <Text variant="bodyMedium">
            {ACTION_TEXT[entry.action]}: {describe(entry)}
          </Text>
          <Text variant="bodySmall">
            by {entry.setByName} · {formatDateTime(entry.setAt)}
          </Text>
          {entry.note ? <Text variant="bodySmall">Note: {entry.note}</Text> : null}
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({ list: { gap: spacingUnit } });
