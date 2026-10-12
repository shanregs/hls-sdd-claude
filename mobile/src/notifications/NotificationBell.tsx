import { StyleSheet, View } from "react-native";
import { Appbar, Badge } from "react-native-paper";
import { useNotifications } from "./NotificationsProvider";

/** What the badge shows: nothing at zero or while unknown, the number up to 99, "99+" above (spec 021 FR-001). */
export function badgeText(count: number | null): string | null {
  if (count === null || count <= 0) return null;
  return count > 99 ? "99+" : String(count);
}

/** The header bell with the unread count. It renders nothing when the server's navigation does not offer Notifications. */
export function NotificationBell({ onOpen }: { onOpen: () => void }) {
  const { enabled, count } = useNotifications();
  if (!enabled) return null;
  const text = badgeText(count);
  // The label is never the bare "Notifications", which is the drawer item's name; an unknown count says so.
  const label = count === null ? "Notifications, count unavailable" : `Notifications, ${count} unread`;
  return (
    <View style={styles.wrap}>
      <Appbar.Action icon="bell-outline" onPress={onOpen} accessibilityLabel={label} />
      {text !== null ? (
        <Badge style={styles.badge} size={18} importantForAccessibility="no">
          {text}
        </Badge>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { justifyContent: "center" },
  badge: { position: "absolute", top: 6, right: 4 },
});
