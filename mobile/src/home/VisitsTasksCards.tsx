import { StyleSheet } from "react-native";
import { ActivityIndicator, Button, Card, Text } from "react-native-paper";
import { minTouchTarget } from "../theme/tokens";
import type { Counted } from "./useHomeCounts";

const plural = (n: number, one: string, many: string) => (n === 1 ? `1 ${one}` : `${n} ${many}`);

/** "N visits today", from the server's planned visits for today; opens the visits screen. */
export function VisitsCard({ counted, onOpen }: { counted: Counted<number>; onOpen: () => void }) {
  if (counted.status === "error") {
    return (
      <Card accessibilityLabel="Visits, could not be loaded">
        <Card.Title title="Visits" subtitle="Could not be loaded" />
        <Card.Actions>
          <Button onPress={counted.reload} contentStyle={styles.button} accessibilityLabel="Try again for visits">
            Try again
          </Button>
        </Card.Actions>
      </Card>
    );
  }
  const text = counted.value === null ? "Loading visits" : plural(counted.value, "visit today", "visits today");
  return (
    <Card onPress={onOpen} accessibilityRole="button" accessibilityLabel={text}>
      <Card.Title title="Visits" subtitle={text} />
      {counted.value === null ? <ActivityIndicator accessibilityLabel="Loading visits" /> : null}
    </Card>
  );
}

/** "N overdue, M due today", from the server's task counts; opens the tasks screen on Overdue. */
export function TasksCard({ counted, onOpen }: { counted: Counted<{ overdue: number; today: number }>; onOpen: () => void }) {
  if (counted.status === "error") {
    return (
      <Card accessibilityLabel="Tasks, could not be loaded">
        <Card.Title title="Tasks" subtitle="Could not be loaded" />
        <Card.Actions>
          <Button onPress={counted.reload} contentStyle={styles.button} accessibilityLabel="Try again for tasks">
            Try again
          </Button>
        </Card.Actions>
      </Card>
    );
  }
  const text = counted.value === null ? "Loading tasks" : `${counted.value.overdue} overdue, ${counted.value.today} due today`;
  return (
    <Card onPress={onOpen} accessibilityRole="button" accessibilityLabel={text}>
      <Card.Title title="Tasks" subtitle={text} />
      {counted.value === null ? <ActivityIndicator accessibilityLabel="Loading tasks" /> : null}
      {counted.value !== null && counted.value.overdue > 0 ? (
        <Card.Content>
          <Text variant="bodySmall">Tap to see the overdue tasks.</Text>
        </Card.Content>
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  button: { minHeight: minTouchTarget },
});
