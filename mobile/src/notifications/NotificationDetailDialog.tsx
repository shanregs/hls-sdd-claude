import { StyleSheet } from "react-native";
import { Button, Dialog, Portal, Text } from "react-native-paper";
import type { NotificationRow } from "../api/notificationsApi";
import { formatDateTime } from "../formats/dates";
import { minTouchTarget } from "../theme/tokens";

/** The full text of a notification whose link the app cannot follow, or that has none (spec 021 FR-005). */
export function NotificationDetailDialog({ row, onClose }: { row: NotificationRow; onClose: () => void }) {
  return (
    <Portal>
      <Dialog visible onDismiss={onClose}>
        <Dialog.Title accessibilityRole="header">{row.title}</Dialog.Title>
        <Dialog.Content>
          <Text variant="bodyLarge">{row.message}</Text>
          <Text variant="bodySmall" style={styles.time}>
            {formatDateTime(row.createdAt)}
          </Text>
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
  button: { minHeight: minTouchTarget },
  time: { marginTop: 8 },
});
