import { useEffect, useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button } from "react-native-paper";
import { ApiError, NoConnectionError } from "../api/errors";
import { getTask, type TaskDetail } from "../api/tasksApi";
import { Problem } from "../attendance/MonthPane";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { EvidenceSection } from "./EvidenceSection";
import { TaskDetailFacts } from "./TaskDetail";
import { TaskSteps } from "./TaskSteps";

type Loaded = { state: "ready"; id: string; detail: TaskDetail } | { state: "gone" | "error"; id: string; message: string };

interface Props {
  id: string;
  /** Called after the server changed the task, so the lists and counts can reload. */
  onChanged: () => void;
  onBack: () => void;
}

/**
 * One task with its steps and evidence (spec 034). It reads the task from the server when it opens; every step replaces what is on
 * screen with the server's answer, and a 409 or 404 from a step reads the task again while keeping what the Manager typed.
 */
export function TaskDetailPane({ id, onChanged, onBack }: Props) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    getTask(id)
      .then((detail) => {
        if (!cancelled) setLoaded({ state: "ready", id, detail });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        const message =
          error instanceof NoConnectionError
            ? "No connection. The task could not be loaded."
            : error instanceof ApiError
              ? error.message
              : "The task could not be loaded.";
        setLoaded({ state: error instanceof ApiError && error.status === 404 ? "gone" : "error", id, message });
      });
    return () => {
      cancelled = true;
    };
  }, [id, attempt]);

  const current = loaded && loaded.id === id ? loaded : null;
  const back = (
    <Button onPress={onBack} contentStyle={styles.button} accessibilityLabel="Back to the tasks">
      Back to the tasks
    </Button>
  );

  if (!current) {
    return (
      <View style={styles.box}>
        <ActivityIndicator accessibilityLabel="Loading the task" />
        {back}
      </View>
    );
  }
  if (current.state !== "ready") {
    return (
      <View style={styles.box}>
        <Problem
          title={current.state === "gone" ? "Task not found" : "Something went wrong"}
          text={current.message}
          onRetry={current.state === "error" ? () => setAttempt((n) => n + 1) : undefined}
        />
        {back}
      </View>
    );
  }

  const replace = (detail: TaskDetail) => {
    setLoaded({ state: "ready", id, detail });
    onChanged();
  };

  return (
    <View style={styles.box}>
      <TaskDetailFacts detail={current.detail} />
      <TaskSteps detail={current.detail} onDetail={replace} onStale={() => setAttempt((n) => n + 1)} />
      <EvidenceSection detail={current.detail} onDetail={replace} />
      {back}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
