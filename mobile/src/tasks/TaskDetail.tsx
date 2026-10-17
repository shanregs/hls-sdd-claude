import { StyleSheet, View } from "react-native";
import { Text } from "react-native-paper";
import type { TaskDetail as Detail } from "../api/tasksApi";
import { formatIsoDate } from "../formats/dates";
import { formatBusinessDateTime } from "../formats/manager";
import { spacingUnit } from "../theme/tokens";
import { historyEventText, overdueText, taskStatusText } from "./taskMessages";

/** A task as the server holds it (spec 034 FR-011): who assigned it, when it is due, what was done, and the history. */
export function TaskDetailFacts({ detail }: { detail: Detail }) {
  const { task } = detail;
  const late = overdueText(task);
  return (
    <View style={styles.box}>
      <Text variant="titleLarge" accessibilityRole="header">
        {task.title}
      </Text>
      <Text variant="bodyLarge">
        {task.typeLabel}, due {formatIsoDate(task.dueOn)}
      </Text>
      <Text variant="titleMedium">{taskStatusText(task.status)}</Text>
      {late ? <Text variant="bodyMedium">{late}</Text> : null}
      {task.escalated ? <Text variant="bodyMedium">Escalated to the Director</Text> : null}
      {detail.createdByName ? <Text variant="bodyMedium">Assigned by {detail.createdByName}</Text> : null}
      {detail.description ? <Text variant="bodyMedium">{detail.description}</Text> : null}
      {task.place ? <Text variant="bodyMedium">Place: {task.place}</Text> : null}
      {task.linkedLabel ? <Text variant="bodyMedium">About: {task.linkedLabel}</Text> : null}
      {detail.overdueReason ? <Text variant="bodyMedium">Reason for being overdue: {detail.overdueReason}</Text> : null}
      {detail.completionNote ? <Text variant="bodyMedium">Completion note: {detail.completionNote}</Text> : null}
      {detail.verifiedByName ? (
        <Text variant="bodyMedium">
          Verified by {detail.verifiedByName}
          {detail.verifiedAt ? ` on ${formatBusinessDateTime(detail.verifiedAt)}` : ""}
        </Text>
      ) : null}
      {detail.followUpTaskIds.length > 0 ? (
        <Text variant="bodyMedium">
          {detail.followUpTaskIds.length === 1 ? "1 follow-up task was created" : `${detail.followUpTaskIds.length} follow-up tasks were created`}
        </Text>
      ) : null}

      <Text variant="titleMedium" accessibilityRole="header">
        History
      </Text>
      {detail.history.map((line, index) => (
        <Text key={`${line.event}-${line.at}-${index}`} variant="bodyMedium">
          {historyEventText(line.event)} by {line.actorName ?? "the system"} on {formatBusinessDateTime(line.at)}
          {line.note ? `: ${line.note}` : ""}
        </Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
});
