import { useRef, useState } from "react";
import { ActivityIndicator, ScrollView, StyleSheet, View } from "react-native";
import { Button, Chip, Text, useTheme } from "react-native-paper";
import {
  clearRead,
  deleteNotification,
  markAllRead,
  markRead,
  type NotificationRow as Row,
} from "../api/notificationsApi";
import { Problem } from "../attendance/MonthPane";
import { ReasonDialog } from "../leave/ReasonDialog";
import type { OpenRoute } from "../navigation/AppShell";
import { NotificationDetailDialog } from "../notifications/NotificationDetailDialog";
import { NotificationRow } from "../notifications/NotificationRow";
import { useNotifications } from "../notifications/NotificationsProvider";
import { linkTarget } from "../notifications/linkTarget";
import { isGone, NOTIFICATION_GONE_TEXT, notificationFailureText } from "../notifications/notificationMessages";
import { useNotificationList } from "../notifications/useNotificationList";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

interface Props {
  openRoute: OpenRoute;
  /** True when the user's menu offers the route and the app has the screen (the shell's own check). */
  canOpen: (route: string) => boolean;
}

/**
 * The signed-in user's own notifications, newest first, in pages, with an Unread only filter. Opening one marks it read
 * on the server and follows its link when the app has the screen and the user's menu offers it; otherwise the full text
 * is shown (spec 021 FR-004 to FR-007). Nothing is shown as read before the server confirms it (FR-011).
 */
export function NotificationsScreen({ openRoute, canOpen }: Props) {
  const theme = useTheme();
  const { count, canDelete, refreshCount } = useNotifications();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const list = useNotificationList(unreadOnly);
  const [detail, setDetail] = useState<Row | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [confirmClear, setConfirmClear] = useState(false);
  // A ref as well as the list: two quick taps before the next render must send only one request per notification.
  const busy = useRef(new Set<string>());

  const unread = count ?? list.unread;

  const open = async (row: Row) => {
    if (busy.current.has(row.id)) return;
    busy.current.add(row.id);
    setNotice(null);
    try {
      if (!row.read) {
        try {
          await markRead(row.id);
          list.markLocalRead(row.id);
          refreshCount();
        } catch (error) {
          if (isGone(error)) {
            list.remove(row.id);
            setNotice(NOTIFICATION_GONE_TEXT);
            refreshCount();
            return;
          }
          // The link is still followed; the row stays unread until the server says otherwise.
          setNotice(notificationFailureText("read", error));
        }
      }
      const target = linkTarget(row.link);
      if (target && canOpen(target.route)) {
        openRoute(target.route, target.month ? { month: target.month } : undefined);
      } else {
        setDetail(row);
      }
    } finally {
      busy.current.delete(row.id);
    }
  };

  // Mark one read without following its link. Nothing changes on screen until the server confirms.
  const markOne = async (row: Row) => {
    if (busy.current.has(row.id)) return;
    busy.current.add(row.id);
    setNotice(null);
    try {
      await markRead(row.id);
      list.markLocalRead(row.id);
      refreshCount();
    } catch (error) {
      if (isGone(error)) {
        list.remove(row.id);
        setNotice(NOTIFICATION_GONE_TEXT);
        refreshCount();
      } else {
        setNotice(notificationFailureText("read", error));
      }
    } finally {
      busy.current.delete(row.id);
    }
  };

  const markAll = async () => {
    setNotice(null);
    try {
      await markAllRead();
      list.reload({ quiet: true });
      refreshCount();
    } catch (error) {
      setNotice(notificationFailureText("readAll", error));
    }
  };

  const deleteOne = async (row: Row) => {
    if (busy.current.has(row.id)) return;
    busy.current.add(row.id);
    setNotice(null);
    try {
      await deleteNotification(row.id);
      list.remove(row.id);
      refreshCount();
    } catch (error) {
      if (isGone(error)) {
        list.remove(row.id);
        setNotice(NOTIFICATION_GONE_TEXT);
        refreshCount();
      } else {
        setNotice(notificationFailureText("delete", error));
      }
    } finally {
      busy.current.delete(row.id);
    }
  };

  // Resolves with the text to show in the dialog, or null once the server cleared the read ones.
  const clear = async (): Promise<string | null> => {
    try {
      await clearRead();
    } catch (error) {
      return notificationFailureText("clear", error);
    }
    setConfirmClear(false);
    list.reload({ quiet: true });
    refreshCount();
    return null;
  };

  const refresh = () => {
    list.reload();
    refreshCount();
  };

  return (
    <Screen onRefresh={refresh}>
      <Text variant="titleMedium" accessibilityRole="header" accessibilityLiveRegion="polite">
        {unread === null ? "Notifications" : `${unread} unread`}
      </Text>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips} accessibilityLabel="Filter notifications">
        {[
          { value: false, label: "All" },
          { value: true, label: "Unread only" },
        ].map((option) => (
          <Chip
            key={option.label}
            selected={option.value === unreadOnly}
            showSelectedCheck
            onPress={() => setUnreadOnly(option.value)}
            accessibilityLabel={option.label}
            style={styles.chip}
          >
            {option.label}
          </Chip>
        ))}
      </ScrollView>

      <View style={styles.actions}>
        {(unread ?? 0) > 0 ? (
          <Button mode="outlined" onPress={() => void markAll()} contentStyle={styles.button} accessibilityLabel="Mark all as read">
            Mark all as read
          </Button>
        ) : null}
        {canDelete ? (
          <Button
            mode="outlined"
            onPress={() => setConfirmClear(true)}
            disabled={!list.items.some((n) => n.read)}
            contentStyle={styles.button}
            accessibilityLabel="Clear read"
          >
            Clear read
          </Button>
        ) : null}
      </View>

      {notice ? (
        <View
          style={[styles.notice, { backgroundColor: theme.colors.secondaryContainer }]}
          accessible
          accessibilityRole="alert"
          accessibilityLiveRegion="polite"
        >
          <Text variant="bodyLarge">{notice}</Text>
        </View>
      ) : null}

      {list.state === "loading" ? <ActivityIndicator size="large" accessibilityLabel="Loading notifications" /> : null}
      {list.state === "noConnection" ? (
        <Problem title="No connection" text="Check your internet connection and try again." onRetry={() => list.reload()} />
      ) : null}
      {list.state === "error" ? (
        <Problem title="Something went wrong" text="We couldn't load your notifications." onRetry={() => list.reload()} />
      ) : null}

      {list.state === "ready" && list.items.length === 0 ? (
        <Text variant="bodyLarge">{unreadOnly ? "No unread notifications" : "No notifications"}</Text>
      ) : null}

      {list.state === "ready"
        ? list.items.map((row) => (
            <NotificationRow key={row.id} row={row} onOpen={() => void open(row)}>
              <View style={styles.rowActions}>
                {!row.read ? (
                  <Button
                    compact
                    onPress={() => void markOne(row)}
                    contentStyle={styles.button}
                    accessibilityLabel={`Mark as read: ${row.title}`}
                  >
                    Mark as read
                  </Button>
                ) : null}
                {canDelete ? (
                  <Button
                    compact
                    onPress={() => void deleteOne(row)}
                    contentStyle={styles.button}
                    accessibilityLabel={`Delete: ${row.title}`}
                  >
                    Delete
                  </Button>
                ) : null}
              </View>
            </NotificationRow>
          ))
        : null}

      {list.state === "ready" && list.hasMore ? (
        <Button
          mode="outlined"
          onPress={list.loadMore}
          loading={list.loadingMore}
          disabled={list.loadingMore}
          contentStyle={styles.button}
          accessibilityLabel="Load more"
        >
          Load more
        </Button>
      ) : null}

      {detail ? <NotificationDetailDialog row={detail} onClose={() => setDetail(null)} /> : null}

      {confirmClear ? (
        <ReasonDialog
          title="Clear read notifications?"
          message="Read notifications will be removed from your list. Unread ones stay. Your leave and attendance records are not affected."
          confirmLabel="Clear"
          dismissLabel="Cancel"
          onConfirm={clear}
          onCancel={() => setConfirmClear(false)}
        />
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  chips: { gap: spacingUnit, paddingVertical: spacingUnit / 2 },
  chip: { minHeight: minTouchTarget, justifyContent: "center" },
  notice: { padding: spacingUnit * 1.5, borderRadius: 8 },
  actions: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit },
  rowActions: { flexDirection: "row", flexWrap: "wrap", justifyContent: "flex-end", paddingHorizontal: spacingUnit },
  button: { minHeight: minTouchTarget },
});
