import { StyleSheet, View } from "react-native";
import { Checkbox, Text } from "react-native-paper";
import type { PlannedActivity } from "../api/managerAttendanceApi";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { kindText } from "./managerMessages";

interface Props {
  activities: PlannedActivity[];
  /** Ids of the selected activities. Rows are read-only when {@link onToggle} is not given. */
  selected?: ReadonlySet<string>;
  onToggle?: (activity: PlannedActivity) => void;
  /** Opens one activity's details (read-only lists). */
  onOpen?: (activity: PlannedActivity) => void;
}

/** What one row says, for the eye and for TalkBack: the kind, the place and the status, as the server sent them. */
export function activitySummary(activity: PlannedActivity): string {
  return [kindText(activity.kind), activity.place, activity.status ? activity.status.toLowerCase() : null]
    .filter((part): part is string => Boolean(part))
    .join(", ");
}

/**
 * Today's planned activities exactly as the server returned them, in its order (spec 033 FR-003, FR-008). Selectable
 * for a Field day, read-only on Home. The server's record has no title, so a row shows kind, place and status.
 */
export function ActivityList({ activities, selected, onToggle, onOpen }: Props) {
  if (activities.length === 0) {
    return <Text variant="bodyMedium">No planned activities for today.</Text>;
  }
  return (
    <View accessibilityLabel="Planned activities for today">
      {activities.map((activity) => {
        const summary = activitySummary(activity);
        if (onToggle) {
          const isSelected = selected?.has(activity.id) ?? false;
          return (
            <Checkbox.Item
              key={activity.id}
              label={summary}
              status={isSelected ? "checked" : "unchecked"}
              onPress={() => onToggle(activity)}
              accessibilityLabel={`${summary}, ${isSelected ? "selected" : "not selected"}`}
              style={styles.row}
              position="leading"
              labelStyle={styles.label}
            />
          );
        }
        return (
          <Text
            key={activity.id}
            variant="bodyLarge"
            style={styles.row}
            onPress={onOpen ? () => onOpen(activity) : undefined}
            accessibilityRole={onOpen ? "button" : "text"}
            accessibilityLabel={onOpen ? `${summary}. Show details` : summary}
          >
            {summary}
          </Text>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: minTouchTarget, paddingVertical: spacingUnit },
  label: { textAlign: "left" },
});
