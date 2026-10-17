import type { TaskView } from "../api/tasksApi";
import { Text } from "react-native-paper";
import { Screen } from "./Screen";

/** Placeholder until the screen is built (spec 034). */
export function TasksScreen({ initialView: _initialView }: { initialView?: TaskView }) {
  return (
    <Screen>
      <Text variant="bodyLarge">My Tasks</Text>
    </Screen>
  );
}
