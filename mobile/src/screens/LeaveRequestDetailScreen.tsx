import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { ApiError, NoConnectionError } from "../api/errors";
import {
  approveLeave,
  getLeave,
  rejectLeave,
  revokeLeave,
  type LeaveAction,
  type LeaveDetail,
} from "../api/leaveApi";
import { Problem } from "../attendance/MonthPane";
import { formatLongDate } from "../formats/dates";
import { LeaveRequestCard } from "../leave/LeaveRequestCard";
import { ReasonDialog } from "../leave/ReasonDialog";
import { leaveRefusalText } from "../leave/leaveMessages";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

type Outcome =
  | { id: string; tick: number; state: "ready"; detail: LeaveDetail }
  | { id: string; tick: number; state: "notFound" | "noConnection" | "error" };

type Decision = "APPROVE" | "REJECT" | "REVOKE";

const DIALOG: Record<Decision, { title: string; field: string; required: boolean; confirm: string; message: string }> = {
  APPROVE: {
    title: "Approve this request?",
    field: "Note",
    required: false,
    confirm: "Confirm approve",
    message: "The days it covers will be marked as leave.",
  },
  REJECT: {
    title: "Reject this request?",
    field: "Reason",
    required: true,
    confirm: "Confirm reject",
    message: "The Teacher will see your reason.",
  },
  REVOKE: {
    title: "Revoke this approved leave?",
    field: "Reason",
    required: true,
    confirm: "Confirm revoke",
    message: "The leave days it marked will be removed.",
  },
};

function failureText(error: unknown): string {
  if (error instanceof NoConnectionError) return "No connection. Nothing was changed.";
  if (error instanceof ApiError && (error.status === 409 || error.status === 400 || error.status === 404)) {
    return leaveRefusalText(error.message);
  }
  return "Something went wrong. Nothing was changed. Please try again.";
}

interface Props {
  id: string;
  onBack: () => void;
  /** Tells the list that a decision was made, so it reloads its rows and the Pending count. */
  onChanged: () => void;
}

/**
 * One request in the approver's scope, with the days approving it would mark. Approve, Reject and Revoke
 * are offered exactly when the server lists them in `allowedActions`, each after a confirmation (spec 020
 * FR-008 to FR-011). The server's answer to every action decides what is shown next.
 */
export function LeaveRequestDetailScreen({ id, onBack, onChanged }: Props) {
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [tick, setTick] = useState(0);
  const [action, setAction] = useState<Decision | null>(null);

  useEffect(() => {
    let cancelled = false;
    getLeave(id)
      .then((detail) => {
        if (!cancelled) setOutcome({ id, tick, state: "ready", detail });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof NoConnectionError) setOutcome({ id, tick, state: "noConnection" });
        else if (error instanceof ApiError && error.status === 404) setOutcome({ id, tick, state: "notFound" });
        else setOutcome({ id, tick, state: "error" });
      });
    return () => {
      cancelled = true;
    };
  }, [id, tick]);

  const reload = useCallback(() => setTick((n) => n + 1), []);
  const current = outcome && outcome.id === id ? outcome : null;
  const detail = current?.state === "ready" ? current.detail : null;

  const send = async (decision: Decision, text: string): Promise<string | null> => {
    if (!detail) return null;
    const version = detail.request.version;
    try {
      if (decision === "APPROVE") await approveLeave(id, { note: text === "" ? undefined : text, version });
      else if (decision === "REJECT") await rejectLeave(id, { reason: text, version });
      else await revokeLeave(id, { reason: text, version });
    } catch (error) {
      // The request may have changed (decided, cancelled, edited, out of scope): show it as it now is.
      if (error instanceof ApiError && (error.status === 409 || error.status === 404)) reload();
      return failureText(error);
    }
    setAction(null);
    reload();
    onChanged();
    return null;
  };

  const offered = (a: LeaveAction) => detail?.request.allowedActions.includes(a) ?? false;

  return (
    <Screen>
      <Button icon="arrow-left" onPress={onBack} contentStyle={styles.button} accessibilityLabel="Back to requests">
        Requests
      </Button>

      {!current ? <ActivityIndicator size="large" accessibilityLabel="Loading request" /> : null}
      {current?.state === "notFound" ? <Problem title="Not found" text="" /> : null}
      {current?.state === "noConnection" ? (
        <Problem title="No connection" text="Check your internet connection and try again." onRetry={reload} />
      ) : null}
      {current?.state === "error" ? (
        <Problem title="Something went wrong" text="We couldn't load this request." onRetry={reload} />
      ) : null}

      {detail ? (
        <>
          <LeaveRequestCard request={detail.request} showTeacher expanded />

          <Text variant="titleMedium" accessibilityRole="header">
            Days it would mark
          </Text>
          {detail.days.length === 0 ? <Text variant="bodyMedium">No working days to mark.</Text> : null}
          {detail.days.map((day) => (
            <View
              key={day.date}
              style={styles.day}
              accessible
              accessibilityLabel={`${formatLongDate(day.date)}, ${day.value === 0.5 ? "half day" : "whole day"}`}
            >
              <Text variant="bodyMedium">{formatLongDate(day.date)}</Text>
              <Text variant="bodyMedium">{day.value === 0.5 ? "Half day" : "Whole day"}</Text>
            </View>
          ))}

          {detail.problems.map((problem) => (
            <HelperText key={problem} type="error" visible accessibilityRole="alert">
              {leaveRefusalText(problem)}
            </HelperText>
          ))}

          {offered("APPROVE") ? (
            <Button mode="contained" onPress={() => setAction("APPROVE")} contentStyle={styles.button} accessibilityLabel="Approve">
              Approve
            </Button>
          ) : null}
          {offered("REJECT") ? (
            <Button mode="outlined" onPress={() => setAction("REJECT")} contentStyle={styles.button} accessibilityLabel="Reject">
              Reject
            </Button>
          ) : null}
          {offered("REVOKE") ? (
            <Button mode="outlined" onPress={() => setAction("REVOKE")} contentStyle={styles.button} accessibilityLabel="Revoke">
              Revoke
            </Button>
          ) : null}
        </>
      ) : null}

      {action && detail ? (
        <ReasonDialog
          title={DIALOG[action].title}
          message={DIALOG[action].message}
          fieldLabel={DIALOG[action].field}
          required={DIALOG[action].required}
          confirmLabel={DIALOG[action].confirm}
          onConfirm={(text) => send(action, text)}
          onCancel={() => setAction(null)}
        />
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  button: { minHeight: minTouchTarget },
  day: { flexDirection: "row", justifyContent: "space-between", paddingVertical: spacingUnit / 2 },
});
