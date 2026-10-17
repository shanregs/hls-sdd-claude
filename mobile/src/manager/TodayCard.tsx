import { useState } from "react";
import { StyleSheet } from "react-native";
import { ActivityIndicator, Button, Card, HelperText, Text } from "react-native-paper";
import { MANAGER_CHECK_IN_ROUTE } from "../access/screenRegistry";
import type { PlannedActivity, Today } from "../api/managerAttendanceApi";
import { formatBusinessTime, formatClock } from "../formats/manager";
import type { OpenRoute } from "../navigation/AppShell";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { ActivityDetailsSheet } from "./ActivityDetailsSheet";
import { ActivityList } from "./ActivityList";
import { CheckOutButton } from "./CheckOutButton";
import { dayTypeText, flagText } from "./managerMessages";
import { MissingCheckOuts } from "./MissingCheckOuts";
import { PhotoView } from "./PhotoView";
import type { TodayState } from "./useToday";

interface Props {
  state: TodayState;
  canCreate: boolean;
  openRoute: OpenRoute;
}

/** The headline of today's state, from the server's day: not checked in, checked in, or checked out. */
function headline(today: Today): string {
  const day = today.day;
  if (day?.checkOutAt) return `Checked out at ${formatBusinessTime(day.checkOutAt)}`;
  if (day?.checkInAt) return `Checked in at ${formatBusinessTime(day.checkInAt)}, ${dayTypeText(day.dayType)}`;
  if (!today.workingDay) return "Today is a weekly off or a holiday";
  if (today.leave && today.leave.fraction >= 1) return "You are on approved leave today";
  return "Not checked in";
}

/**
 * Today on the Manager's Home (spec 033 US3): the state, the planned activities and the days still missing a check-out,
 * all as the server returns them. Check in opens the Check-in screen; Check out is done here. Buttons appear only when
 * the menu grants Create and the server's day allows them.
 */
export function TodayCard({ state, canCreate, openRoute }: Props) {
  const [details, setDetails] = useState<PlannedActivity | null>(null);
  const reload = () => state.reload({ quiet: true });

  if (state.status === "loading") {
    return (
      <Card accessibilityLabel="Today">
        <Card.Title title="Today" />
        <Card.Content>
          <ActivityIndicator accessibilityLabel="Loading today" />
        </Card.Content>
      </Card>
    );
  }
  if (state.status !== "ready" || !state.today) {
    return (
      <Card accessibilityLabel="Today">
        <Card.Title title="Today" />
        <Card.Content style={styles.content}>
          <HelperText type="error" visible accessibilityRole="alert">
            {state.status === "noConnection"
              ? "No connection. Today could not be loaded."
              : (state.message ?? "Today could not be loaded.")}
          </HelperText>
          <Button onPress={() => state.reload()} contentStyle={styles.button}>
            Try again
          </Button>
        </Card.Content>
      </Card>
    );
  }

  const today = state.today;
  const day = today.day;
  const checkedIn = Boolean(day?.checkInAt);
  const mayCheckIn = !checkedIn && today.workingDay && !(today.leave && today.leave.fraction >= 1);

  return (
    <Card accessibilityLabel="Today">
      <Card.Title title="Today" subtitle={headline(today)} />
      <Card.Content style={styles.content}>
        {day?.flags.map((flag) => (
          <Text key={flag} variant="bodyMedium">
            {flagText(flag)}
          </Text>
        ))}
        {mayCheckIn ? (
          <Text variant="bodyMedium">
            Check in before {formatClock(today.missedCutoff)}. A check-in after {formatClock(today.lateAfter)} is marked
            late.
          </Text>
        ) : null}
        {mayCheckIn && canCreate ? (
          <Button
            mode="contained"
            onPress={() => openRoute(MANAGER_CHECK_IN_ROUTE)}
            contentStyle={styles.button}
            accessibilityLabel="Check in"
          >
            Check in
          </Button>
        ) : null}
        {checkedIn && !day?.checkOutAt && canCreate ? <CheckOutButton onDone={reload} /> : null}
        {day?.checkInAt ? <PhotoView date={day.date} hasPhoto={day.hasPhoto} canAdd={canCreate} onChanged={reload} /> : null}

        <Text variant="titleSmall">Today's planned activities</Text>
        <ActivityList activities={today.plannedActivities} onOpen={setDetails} />
        <MissingCheckOuts dates={today.missingCheckOutDates} canComplete={canCreate} onDone={reload} />
      </Card.Content>
      <ActivityDetailsSheet activity={details} onClose={() => setDetails(null)} />
    </Card>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacingUnit, paddingBottom: spacingUnit },
  button: { minHeight: minTouchTarget },
});

