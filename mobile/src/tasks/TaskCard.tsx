import { Pressable, StyleSheet, View } from "react-native";
import { Text, useTheme } from "react-native-paper";
import type { TaskRow } from "../api/tasksApi";
import { formatIsoDate } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { overdueText, taskStatusText } from "./taskMessages";

/** What a row says aloud: the title, the due date, the status, and, only when the server says so, overdue days and escalation. */
export function taskSummaryText(row: TaskRow): string {
  return [
    row.title,
    `due ${formatIsoDate(row.dueOn)}`,
    taskStatusText(row.status).toLowerCase(),
    overdueText(row),
    row.escalated ? "escalated" : null,
  ]
    .filter(Boolean)
    .join(", ");
}

/** One task in a list: title, type, due date, status, overdue days and the escalated mark, all from the server. */
export function TaskCard({ row, onPress }: { row: TaskRow; onPress: () => void }) {
  const theme = useTheme();
  const late = overdueText(row);
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={taskSummaryText(row)}
      style={[styles.card, { borderColor: row.overdue ? theme.colors.error : theme.colors.outline, backgroundColor: theme.colors.surface }]}
    >
      <Text variant="titleMedium">{row.title}</Text>
      <Text variant="bodyMedium">
        {row.typeLabel} · due {formatIsoDate(row.dueOn)}
      </Text>
      <View style={styles.line}>
        <Text variant="labelLarge">{taskStatusText(row.status)}</Text>
        {late ? (
          <Text variant="labelLarge" style={{ color: theme.colors.error }}>
            {late}
          </Text>
        ) : null}
        {row.escalated ? <Text variant="labelLarge">Escalated</Text> : null}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { minHeight: minTouchTarget, borderWidth: 1, borderRadius: spacingUnit, padding: spacingUnit * 1.5, gap: spacingUnit / 2 },
  line: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: spacingUnit },
});
