import { useState } from "react";
import { ActivityIndicator, StyleSheet } from "react-native";
import { Button, Chip, Text } from "react-native-paper";
import { listLeave, type LeaveStatus } from "../api/leaveApi";
import { Problem } from "../attendance/MonthPane";
import { LeaveRequestCard } from "../leave/LeaveRequestCard";
import { MANAGEMENT_FILTERS, StatusFilter, type StatusChoice } from "../leave/StatusFilter";
import { useLeaveList } from "../leave/useLeaveList";
import { useInnerBack } from "../navigation/InnerBack";
import { minTouchTarget } from "../theme/tokens";
import { LeaveRequestDetailScreen } from "./LeaveRequestDetailScreen";
import { Screen } from "./Screen";

const loader = (status: LeaveStatus | undefined, page: number) => listLeave({ status, page });

/**
 * The requests the server returns for the signed-in approver, Pending by default with a filter by status
 * (the server has no "All" here), the Pending count, and paging. A row only opens the request: Approve,
 * Reject and Revoke exist on the detail screen only (spec 020 FR-007, FR-009).
 */
export function LeaveManagementScreen({ initialStatus }: { initialStatus?: LeaveStatus }) {
  const [choice, setChoice] = useState<LeaveStatus>(initialStatus ?? "PENDING");
  const list = useLeaveList(loader, choice);
  const [selected, setSelected] = useState<string | null>(null);

  useInnerBack(selected !== null, () => setSelected(null));

  if (selected) {
    return (
      <LeaveRequestDetailScreen
        id={selected}
        onBack={() => setSelected(null)}
        onChanged={() => list.reload({ quiet: true })}
      />
    );
  }

  return (
    <Screen onRefresh={() => list.reload()}>
      {list.pendingCount !== null ? (
        <Chip icon="clock-outline" accessibilityLabel={`Pending: ${list.pendingCount}`}>
          Pending: {list.pendingCount}
        </Chip>
      ) : null}

      <StatusFilter
        options={MANAGEMENT_FILTERS}
        value={choice}
        onChange={(value: StatusChoice) => value !== "ALL" && setChoice(value)}
      />

      {list.state === "loading" ? <ActivityIndicator size="large" accessibilityLabel="Loading leave requests" /> : null}
      {list.state === "noConnection" ? (
        <Problem title="No connection" text="Check your internet connection and try again." onRetry={() => list.reload()} />
      ) : null}
      {list.state === "error" ? (
        <Problem title="Something went wrong" text="We couldn't load the leave requests." onRetry={() => list.reload()} />
      ) : null}

      {list.state === "ready" && list.items.length === 0 ? <Text variant="bodyLarge">No leave requests</Text> : null}

      {list.state === "ready"
        ? list.items.map((request) => (
            <LeaveRequestCard key={request.id} request={request} showTeacher onPress={() => setSelected(request.id)} />
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
    </Screen>
  );
}

const styles = StyleSheet.create({ button: { minHeight: minTouchTarget } });
