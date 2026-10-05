import { useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, Text, useTheme } from "react-native-paper";
import { ApiError, NoConnectionError } from "../api/errors";
import { cancelMyLeave, listMyLeave, type LeaveRequest, type LeaveStatus } from "../api/leaveApi";
import { Problem } from "../attendance/MonthPane";
import { LeaveRequestCard } from "../leave/LeaveRequestCard";
import { ReasonDialog } from "../leave/ReasonDialog";
import { HISTORY_FILTERS, StatusFilter, type StatusChoice } from "../leave/StatusFilter";
import { isStaleLeaveRefusal, leaveRefusalText } from "../leave/leaveMessages";
import { useLeaveList } from "../leave/useLeaveList";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

/** Plain text for a failed cancel; the server's refusal wording comes from `leaveRefusalText`. */
function cancelFailure(error: unknown): string {
  if (error instanceof NoConnectionError) return "No connection. The request was not cancelled.";
  if (error instanceof ApiError && (error.status === 409 || error.status === 400 || error.status === 404)) {
    return leaveRefusalText(error.message);
  }
  return "Something went wrong. The request was not cancelled. Please try again.";
}

const loader = (status: LeaveStatus | undefined, page: number) => listMyLeave({ status, page });

/**
 * The Teacher's own requests, newest first, with a status filter and paging. Cancel is offered on a request
 * only when the server lists CANCEL among its allowed actions (spec 020 FR-006); the list is reloaded from
 * the server after every cancel.
 */
export function MyLeaveHistoryScreen({ notice }: { notice?: string }) {
  const theme = useTheme();
  const [choice, setChoice] = useState<StatusChoice>("ALL");
  const status = choice === "ALL" ? undefined : choice;
  const list = useLeaveList(loader, status);
  const [target, setTarget] = useState<LeaveRequest | null>(null);

  const cancel = async (): Promise<string | null> => {
    if (!target) return null;
    try {
      await cancelMyLeave(target.id);
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        // The request changed meanwhile (decided, cancelled, started): show it as it now is.
        list.reload({ quiet: true });
        if (isStaleLeaveRefusal(error.message)) return leaveRefusalText(error.message);
      }
      return cancelFailure(error);
    }
    setTarget(null);
    list.reload({ quiet: true });
    return null;
  };

  return (
    <Screen onRefresh={() => list.reload()}>
      {notice ? (
        <View
          style={[styles.notice, { backgroundColor: theme.colors.secondaryContainer }]}
          accessibilityRole="alert"
          accessibilityLiveRegion="polite"
        >
          <Text variant="bodyLarge">{notice}</Text>
        </View>
      ) : null}

      <StatusFilter options={HISTORY_FILTERS} value={choice} onChange={setChoice} />

      {list.state === "loading" ? <ActivityIndicator size="large" accessibilityLabel="Loading leave requests" /> : null}
      {list.state === "noConnection" ? (
        <Problem title="No connection" text="Check your internet connection and try again." onRetry={() => list.reload()} />
      ) : null}
      {list.state === "error" ? (
        <Problem title="Something went wrong" text="We couldn't load your leave requests." onRetry={() => list.reload()} />
      ) : null}

      {list.state === "ready" && list.items.length === 0 ? <Text variant="bodyLarge">No leave requests</Text> : null}

      {list.state === "ready"
        ? list.items.map((request) => (
            <LeaveRequestCard key={request.id} request={request} expandable>
              {request.allowedActions.includes("CANCEL") ? (
                <Button
                  mode="outlined"
                  onPress={() => setTarget(request)}
                  contentStyle={styles.button}
                  style={styles.action}
                  accessibilityLabel="Cancel request"
                >
                  Cancel request
                </Button>
              ) : null}
            </LeaveRequestCard>
          ))
        : null}

      {list.state === "ready" && list.items.length < list.total ? (
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

      {target ? (
        <ReasonDialog
          title="Cancel this request?"
          message={`${target.leaveType}, ${target.workingDays} working days. This cannot be undone.`}
          confirmLabel="Yes, cancel it"
          dismissLabel="Keep request"
          onConfirm={cancel}
          onCancel={() => setTarget(null)}
        />
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  notice: { padding: spacingUnit * 1.5, borderRadius: 8 },
  button: { minHeight: minTouchTarget },
  action: { margin: spacingUnit },
});
