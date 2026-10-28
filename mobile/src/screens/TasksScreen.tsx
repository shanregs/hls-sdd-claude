import { useState } from "react";
import { ActivityIndicator, ScrollView, StyleSheet, View } from "react-native";
import { Button, Chip, HelperText, Text } from "react-native-paper";
import { TASKS_ROUTE } from "../access/screenRegistry";
import { useMenuActions } from "../access/useMenuActions";
import type { TaskView } from "../api/tasksApi";
import { Problem } from "../attendance/MonthPane";
import { useAuth } from "../auth/AuthProvider";
import { useInnerBack } from "../navigation/InnerBack";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { AddTaskForm } from "../tasks/AddTaskForm";
import { LogTaskTodayForm } from "../tasks/LogTaskTodayForm";
import { useSameDayLog } from "../sameDay/useSameDayLog";
import { TaskCard } from "../tasks/TaskCard";
import { TaskDetailPane } from "../tasks/TaskDetailPane";
import { TASK_VIEWS } from "../tasks/taskMessages";
import { useCanAddTask } from "../tasks/useCanAddTask";
import { useTasks, useTaskSummary } from "../tasks/useTasks";
import { Screen } from "./Screen";


/**
 * The Manager's tasks (spec 034 US3): the server's views with their counts, a page of 25 at a time, and a detail inside the
 * screen (Back closes it). Overdue days, escalation and every status are the server's.
 */
export function TasksScreen({ initialView }: { initialView?: TaskView }) {
  const [view, setView] = useState<TaskView>(initialView ?? "today");
  const tasks = useTasks(view);
  const counts = useTaskSummary();
  const [openId, setOpenId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [logging, setLogging] = useState(false);
  const [saved, setSaved] = useState<string | null>(null);
  const { canCreate } = useMenuActions(TASKS_ROUTE);
  const { state } = useAuth();
  const myUserId = state.status === "signedIn" ? state.user.id : null;
  const canAdd = useCanAddTask(canCreate, myUserId);
  const sameDay = useSameDayLog(myUserId);

  const reloadAll = () => {
    tasks.reload({ quiet: true });
    counts.reload();
  };
  // Leaving a detail reloads the list and the counts quietly, since a step may have moved the task to another view.
  const closeDetail = () => {
    setOpenId(null);
    reloadAll();
  };
  useInnerBack(openId !== null || adding || logging, () =>
    logging ? setLogging(false) : adding ? setAdding(false) : closeDetail(),
  );

  if (logging && myUserId) {
    return (
      <Screen>
        <LogTaskTodayForm
          myUserId={myUserId}
          onCancel={() => setLogging(false)}
          onDone={(result) => {
            setLogging(false);
            setSaved(
              result === "QUEUED"
                ? "Saved on this phone. It will be sent when you are back online."
                : "Logged for today.",
            );
            reloadAll();
          }}
        />
      </Screen>
    );
  }

  if (adding && myUserId) {
    return (
      <Screen>
        <AddTaskForm
          myUserId={myUserId}
          onCancel={() => setAdding(false)}
          onDone={(created) => {
            setAdding(false);
            setOpenId(created.task.id);
            reloadAll();
          }}
        />
      </Screen>
    );
  }

  if (openId) {
    return (
      <Screen>
        <TaskDetailPane id={openId} onChanged={reloadAll} onBack={closeDetail} />
      </Screen>
    );
  }

  const countOf = (value: TaskView): number | null => (counts.summary && value !== "all" ? counts.summary[value] : null);

  return (
    <Screen onRefresh={reloadAll}>
      {saved ? (
        <HelperText type="info" visible accessibilityLiveRegion="polite">
          {saved}
        </HelperText>
      ) : null}
      {sameDay.waiting.length > 0 ? (
        <HelperText type="info" visible accessibilityLiveRegion="polite">
          {sameDay.waiting.length} logged for today and waiting to be sent.
        </HelperText>
      ) : null}
      {canAdd ? (
        <Button mode="outlined" onPress={() => setLogging(true)} contentStyle={styles.button} accessibilityLabel="Log a task for today">
          Log a task for today
        </Button>
      ) : null}
      {canAdd ? (
        <Button mode="contained" onPress={() => setAdding(true)} contentStyle={styles.button} accessibilityLabel="Add a task for me">
          Add a task for me
        </Button>
      ) : null}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips} accessibilityLabel="Choose a view">
        {TASK_VIEWS.map((option) => {
          const count = countOf(option.value);
          const label = count === null ? option.label : `${option.label} (${count})`;
          return (
            <Chip
              key={option.value}
              selected={option.value === view}
              showSelectedCheck
              onPress={() => setView(option.value)}
              accessibilityLabel={label}
            >
              {label}
            </Chip>
          );
        })}
      </ScrollView>

      {tasks.status === "loading" ? <ActivityIndicator size="large" accessibilityLabel="Loading tasks" /> : null}
      {tasks.status === "noConnection" ? (
        <Problem title="No connection" text="Check your internet connection and try again." onRetry={() => tasks.reload()} />
      ) : null}
      {tasks.status === "error" ? (
        <Problem title="Something went wrong" text={tasks.message ?? "The tasks could not be loaded."} onRetry={() => tasks.reload()} />
      ) : null}

      {tasks.status === "ready" && tasks.rows.length === 0 ? <Text variant="bodyMedium">Nothing here.</Text> : null}
      {tasks.status === "ready" ? (
        <View style={styles.list}>
          {tasks.rows.map((row) => (
            <TaskCard key={row.id} row={row} onPress={() => setOpenId(row.id)} />
          ))}
          {tasks.moreProblem ? (
            <HelperText type="error" visible accessibilityRole="alert">
              {tasks.moreProblem}
            </HelperText>
          ) : null}
          {tasks.rows.length < tasks.total ? (
            <Button onPress={tasks.loadMore} loading={tasks.loadingMore} disabled={tasks.loadingMore} contentStyle={styles.button}>
              Load more
            </Button>
          ) : null}
        </View>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  chips: { gap: spacingUnit, paddingVertical: spacingUnit / 2 },
  list: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
