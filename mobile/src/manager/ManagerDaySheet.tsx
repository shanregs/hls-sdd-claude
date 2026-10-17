import { useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import { Button, Modal, Portal, Text, useTheme } from "react-native-paper";
import type { MonthDay } from "../api/managerAttendanceApi";
import { formatLongDate } from "../formats/dates";
import { formatBusinessTime } from "../formats/manager";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { CompleteCheckOutDialog } from "./CompleteCheckOutDialog";
import { dayTypeText, flagText, statusText } from "./managerMessages";
import { PhotoView } from "./PhotoView";

interface Props {
  day: MonthDay;
  /** The month is locked: no action that would change it is offered. */
  locked: boolean;
  /** The menu grants Create. */
  canCreate: boolean;
  /** Offered for a past day with no check-in (spec 033 US6); left out until that story is built. */
  onAskCheckIn?: (date: string) => void;
  /** Called after the server completed the day's check-out, so the month can reload. */
  onChanged: () => void;
  onClose: () => void;
}

/**
 * A Manager's day: status, times, kind of day, purpose, flags and photo as the server holds them, with the one thing the
 * Manager can do about it, completing a missing check-out (spec 033 US4). Actions are hidden in a locked month.
 */
export function ManagerDaySheet({ day, locked, canCreate, onAskCheckIn, onChanged, onClose }: Props) {
  const theme = useTheme();
  const [completing, setCompleting] = useState(false);
  const mayChange = canCreate && !locked;
  const canComplete = mayChange && (day.status === "NO_CHECK_OUT" || day.flags.includes("NO_CHECK_OUT"));
  // a past working day with no check-in and no leave is ABSENT; today after the cut-off is MISSED and can still be checked in
  const canAsk = mayChange && Boolean(onAskCheckIn) && !day.checkInAt && day.status === "ABSENT";

  return (
    <Portal>
      <Modal
        visible
        onDismiss={onClose}
        style={styles.wrapper}
        contentContainerStyle={[styles.sheet, { backgroundColor: theme.colors.surface }]}
      >
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
          <Text variant="titleLarge" accessibilityRole="header">
            {formatLongDate(day.date)}
          </Text>
          <Text variant="bodyLarge">{statusText(day.status)}</Text>
          {day.checkInAt ? (
            <Text variant="bodyLarge">
              Checked in at {formatBusinessTime(day.checkInAt)}
              {day.dayType ? `, ${dayTypeText(day.dayType)}` : ""}
            </Text>
          ) : null}
          {day.checkOutAt ? <Text variant="bodyLarge">Checked out at {formatBusinessTime(day.checkOutAt)}</Text> : null}
          {day.purpose ? <Text variant="bodyMedium">Purpose: {day.purpose}</Text> : null}
          {day.activities.length > 0 ? (
            <Text variant="bodyMedium">Linked planned activities: {day.activities.length}</Text>
          ) : null}
          {day.leave ? (
            <Text variant="bodyMedium">
              {day.leave.fraction === 0.5 ? "Half day" : "Full day"} leave{day.leave.unpaid ? " (unpaid)" : ""}
            </Text>
          ) : null}
          {day.flags.map((flag) => (
            <Text key={flag} variant="bodyMedium">
              {flagText(flag)}
            </Text>
          ))}
          {locked ? <Text variant="bodyMedium">This month is locked, so this day cannot change.</Text> : null}
          {day.checkInAt ? (
            <PhotoView date={day.date} hasPhoto={day.hasPhoto} canAdd={false} onChanged={onChanged} />
          ) : null}

          <View style={styles.actions}>
            {canComplete ? (
              <Button mode="contained" onPress={() => setCompleting(true)} contentStyle={styles.button}>
                Complete check-out
              </Button>
            ) : null}
            {canAsk ? (
              <Button mode="outlined" onPress={() => onAskCheckIn?.(day.date)} contentStyle={styles.button}>
                Ask to add a check-in
              </Button>
            ) : null}
            <Button onPress={onClose} contentStyle={styles.button} accessibilityLabel="Close">
              Close
            </Button>
          </View>
        </ScrollView>
        {completing ? (
          <CompleteCheckOutDialog
            date={day.date}
            onCancel={() => setCompleting(false)}
            onDone={() => {
              setCompleting(false);
              onChanged();
              onClose();
            }}
          />
        ) : null}
      </Modal>
    </Portal>
  );
}

const styles = StyleSheet.create({
  wrapper: { justifyContent: "flex-end" },
  sheet: { borderTopLeftRadius: spacingUnit * 2, borderTopRightRadius: spacingUnit * 2, maxHeight: "85%" },
  content: { padding: spacingUnit * 3, gap: spacingUnit },
  actions: { gap: spacingUnit, marginTop: spacingUnit },
  button: { minHeight: minTouchTarget },
});
