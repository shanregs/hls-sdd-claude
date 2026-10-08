import { Pressable, StyleSheet, View } from "react-native";
import { Text, useTheme } from "react-native-paper";
import type { NotificationRow as Row } from "../api/notificationsApi";
import { formatDateTime } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";

interface Props {
  row: Row;
  onOpen: () => void;
  /** Extra controls under the row, for example Mark as read and Delete. */
  children?: React.ReactNode;
}

/** The spoken form of a row: unread first, then the title and the server's time (spec 021 FR-015). */
export function rowLabel(row: Row): string {
  return `${row.read ? "" : "Unread. "}${row.title}. ${formatDateTime(row.createdAt)}`;
}

/**
 * One notification in the list: title (bold when unread, with a marker that is not colour alone), two lines of the
 * message and the time the server recorded. Tapping it opens the notification.
 */
export function NotificationRow({ row, onOpen, children }: Props) {
  const theme = useTheme();
  const unread = !row.read;
  return (
    <View
      style={[
        styles.card,
        { backgroundColor: unread ? theme.colors.secondaryContainer : theme.colors.surface, borderColor: theme.colors.outlineVariant },
      ]}
    >
      <Pressable onPress={onOpen} accessibilityRole="button" accessibilityLabel={rowLabel(row)} style={styles.content}>
        <View style={styles.titleRow}>
          {unread ? <View style={[styles.dot, { backgroundColor: theme.colors.primary }]} /> : null}
          <Text variant="titleSmall" style={[styles.title, unread ? styles.bold : null]}>
            {row.title}
          </Text>
        </View>
        <Text variant="bodyMedium" numberOfLines={2}>
          {row.message}
        </Text>
        <Text variant="bodySmall" style={{ color: theme.colors.onSurfaceVariant }}>
          {formatDateTime(row.createdAt)}
        </Text>
      </Pressable>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderWidth: 1, borderRadius: 8, overflow: "hidden" },
  content: { padding: spacingUnit * 1.5, gap: spacingUnit / 2, minHeight: minTouchTarget },
  titleRow: { flexDirection: "row", alignItems: "center", gap: spacingUnit },
  title: { flexShrink: 1 },
  bold: { fontWeight: "700" },
  dot: { width: 10, height: 10, borderRadius: 5 },
});
