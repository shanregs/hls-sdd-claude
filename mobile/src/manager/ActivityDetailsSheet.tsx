import { StyleSheet, View } from "react-native";
import { Button, Dialog, Portal, Text } from "react-native-paper";
import type { PlannedActivity } from "../api/managerAttendanceApi";
import { formatIsoDate } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { kindText } from "./managerMessages";

/**
 * One planned activity's details: its kind, place, date and status as the server sent them (the server's record has no
 * title). No kind has a record screen in the app yet (spec 034), so this is where a tap on an activity leads.
 */
export function ActivityDetailsSheet({ activity, onClose }: { activity: PlannedActivity | null; onClose: () => void }) {
  if (!activity) return null;
  return (
    <Portal>
      <Dialog visible onDismiss={onClose}>
        <Dialog.Title>{kindText(activity.kind)}</Dialog.Title>
        <Dialog.Content>
          <View style={styles.rows}>
            <Text variant="bodyLarge">Place: {activity.place ?? "not given"}</Text>
            <Text variant="bodyLarge">Date: {formatIsoDate(activity.date)}</Text>
            <Text variant="bodyLarge">Status: {activity.status ? activity.status.toLowerCase() : "not given"}</Text>
          </View>
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={onClose} contentStyle={styles.button} accessibilityLabel="Close">
            Close
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

const styles = StyleSheet.create({
  rows: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
