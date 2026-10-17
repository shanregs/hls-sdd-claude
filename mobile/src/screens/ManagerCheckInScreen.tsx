import { useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { MANAGER_CHECK_IN_ROUTE } from "../access/screenRegistry";
import type { ManagerDay, Today } from "../api/managerAttendanceApi";
import { formatBusinessTime, formatClock } from "../formats/manager";
import { formatIsoDate } from "../formats/dates";
import { ActivityList } from "../manager/ActivityList";
import { CheckInForm } from "../manager/CheckInForm";
import { CheckOutButton } from "../manager/CheckOutButton";
import { dayTypeText, flagText, kindText } from "../manager/managerMessages";
import { MissingCheckOuts } from "../manager/MissingCheckOuts";
import { PhotoView } from "../manager/PhotoView";
import { useManagerActions } from "../manager/useManagerActions";
import { useToday } from "../manager/useToday";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

/** Why a Manager is not asked to check in today, or null when they should be. */
function noCheckInReason(today: Today): string | null {
  if (!today.workingDay) return "Today is a weekly off or a holiday, so there is nothing to check in.";
  if (today.leave && today.leave.fraction >= 1) return "You are on approved leave today, so there is nothing to check in.";
  return null;
}

function Status({ day }: { day: ManagerDay }) {
  return (
    <View style={styles.block}>
      <Text variant="titleMedium" accessibilityRole="header">
        Checked in at {formatBusinessTime(day.checkInAt ?? "")}, {dayTypeText(day.dayType)}
      </Text>
      {day.flags.map((flag) => (
        <Text key={flag} variant="bodyMedium">
          {flagText(flag)}
        </Text>
      ))}
      {day.purpose ? <Text variant="bodyMedium">Purpose: {day.purpose}</Text> : null}
      {day.checkOutAt ? (
        <Text variant="titleMedium" accessibilityRole="header">
          Checked out at {formatBusinessTime(day.checkOutAt)}
        </Text>
      ) : null}
    </View>
  );
}

/**
 * The Manager's check-in screen (spec 033 US1 and US2): today's status from the server, the check-in form before the
 * check-in, and the status after it. Everything shown comes from the server's answer; nothing is shown as done before
 * the server confirms it.
 */
export function ManagerCheckInScreen() {
  const { canCreate } = useManagerActions(MANAGER_CHECK_IN_ROUTE);
  const state = useToday();
  const [photoProblem, setPhotoProblem] = useState<string | null>(null);

  if (state.status === "loading") {
    return (
      <Screen>
        <ActivityIndicator accessibilityLabel="Loading today" />
      </Screen>
    );
  }
  if (state.status !== "ready" || !state.today) {
    return (
      <Screen onRefresh={state.reload}>
        <HelperText type="error" visible accessibilityRole="alert">
          {state.status === "noConnection"
            ? "No connection. Today could not be loaded."
            : (state.message ?? "Today could not be loaded.")}
        </HelperText>
        <Button mode="contained" onPress={() => state.reload()} contentStyle={styles.button}>
          Try again
        </Button>
      </Screen>
    );
  }

  const today = state.today;
  const day = today.day;
  const reload = () => state.reload({ quiet: true });
  const reason = noCheckInReason(today);

  return (
    <Screen onRefresh={reload}>
      <Text variant="bodyLarge">{formatIsoDate(today.date)}</Text>
      {day?.checkInAt ? (
        <View style={styles.block}>
          <Status day={day} />
          {day.activities.length > 0 ? (
            <Text variant="bodyMedium">
              Linked activities:{" "}
              {day.activities
                .map((ref) => today.plannedActivities.find((a) => a.id === ref.id))
                .filter((a): a is NonNullable<typeof a> => Boolean(a))
                .map((a) => `${kindText(a.kind)}${a.place ? ` at ${a.place}` : ""}`)
                .join("; ") || `${day.activities.length} linked`}
            </Text>
          ) : null}
          <PhotoView date={day.date} hasPhoto={day.hasPhoto} canAdd={canCreate} onChanged={reload} />
          {!day.checkOutAt && canCreate ? <CheckOutButton onDone={reload} /> : null}
          {photoProblem ? (
            <HelperText type="error" visible accessibilityRole="alert">
              {photoProblem}
            </HelperText>
          ) : null}
        </View>
      ) : reason ? (
        <Text variant="bodyLarge">{reason}</Text>
      ) : canCreate ? (
        <View style={styles.block}>
          <Text variant="bodyMedium">
            Check in before {formatClock(today.missedCutoff)}. A check-in after {formatClock(today.lateAfter)} is marked
            late.
          </Text>
          <CheckInForm
            activities={today.plannedActivities}
            onDone={(_checkedIn, problem) => {
              setPhotoProblem(problem);
              reload();
            }}
          />
        </View>
      ) : (
        <Text variant="bodyLarge">You are not allowed to check in.</Text>
      )}
      <MissingCheckOuts dates={today.missingCheckOutDates} canComplete={canCreate} onDone={reload} />
      {day?.checkInAt ? (
        <View style={styles.block}>
          <Text variant="titleSmall">Today's planned activities</Text>
          <ActivityList activities={today.plannedActivities} />
        </View>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  block: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
