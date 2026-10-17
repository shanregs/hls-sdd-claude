import { useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, Chip, Text } from "react-native-paper";
import { cancelRegularization, getMonth, type ManagerMonth, type MonthDay, type Regularization } from "../api/managerAttendanceApi";
import { Problem } from "../attendance/MonthPane";
import { MonthGrid } from "../attendance/MonthGrid";
import { MonthPicker } from "../attendance/MonthPicker";
import { presentManagerDay } from "../attendance/managerDayPresentation";
import { currentMonth } from "../attendance/monthRange";
import { serverNow } from "../attendance/serverClock";
import { useMonthView } from "../attendance/useMonthView";
import { formatIsoDate } from "../formats/dates";
import { formatBusinessDateTime, formatClock } from "../formats/manager";
import { ReasonDialog } from "../leave/ReasonDialog";
import { ManagerDaySheet } from "../manager/ManagerDaySheet";
import { managerRefusalText } from "../manager/managerMessages";
import { RegularizationForm } from "../manager/RegularizationForm";
import { useRegularizations } from "../manager/useRegularizations";
import { useManagerActions } from "../manager/useManagerActions";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

const MONTH_ROUTE = "/manager-attendance/me/month";

/** The month's figures exactly as the server sent them, or its message when it has none; the app adds nothing up. */
function Figures({ month }: { month: ManagerMonth }) {
  const figures = month.figures;
  if (month.figuresUnavailable) {
    return <Text variant="bodyMedium">{month.figuresUnavailable}</Text>;
  }
  if (!figures) return null;
  const stats: { label: string; value: number }[] = [
    { label: "Working days", value: figures.workingDays },
    { label: "Days worked", value: figures.daysWorked },
    { label: "Leave (paid)", value: figures.leavePaid },
    { label: "Leave (unpaid)", value: figures.leaveUnpaid },
    { label: "Unpaid", value: figures.unpaid },
    { label: "Field days", value: figures.fieldDays },
  ];
  return (
    <View style={styles.figures} accessibilityLabel="Month figures">
      {stats.map((stat) => (
        <View key={stat.label} style={styles.stat} accessible accessibilityLabel={`${stat.label} ${stat.value}`}>
          <Text variant="titleMedium">{stat.value}</Text>
          <Text variant="labelSmall">{stat.label}</Text>
        </View>
      ))}
    </View>
  );
}

const REQUEST_STATUS_TEXT: Record<Regularization["status"], string> = {
  PENDING: "Pending",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

/** One forgotten check-in request: the day, the time asked for, the status and the decision. */
function RequestRow({ request, canCancel, onCancel }: { request: Regularization; canCancel: boolean; onCancel: () => void }) {
  const decided = request.status === "APPROVED" || request.status === "REJECTED";
  const spoken = [
    `Check-in for ${formatIsoDate(request.date)} at ${formatClock(request.proposedTime)}`,
    REQUEST_STATUS_TEXT[request.status],
    decided && request.decidedAt ? `on ${formatBusinessDateTime(request.decidedAt)}` : null,
    request.decisionReason,
  ]
    .filter(Boolean)
    .join(", ");
  return (
    <View style={styles.request} accessible accessibilityLabel={spoken}>
      <Text variant="bodyLarge">
        {formatIsoDate(request.date)} at {formatClock(request.proposedTime)}
      </Text>
      <Chip compact style={styles.chip}>
        {REQUEST_STATUS_TEXT[request.status]}
      </Chip>
      <Text variant="bodyMedium">Reason: {request.reason}</Text>
      {request.decisionReason ? <Text variant="bodyMedium">Reason given: {request.decisionReason}</Text> : null}
      {canCancel && request.status === "PENDING" ? (
        <Button
          mode="outlined"
          onPress={onCancel}
          contentStyle={styles.button}
          accessibilityLabel={`Cancel check-in request for ${formatIsoDate(request.date)}`}
        >
          Cancel request
        </Button>
      ) : null}
    </View>
  );
}

/**
 * The Manager's own month (spec 033 US4): the month picker and grid of spec 019 with the server's status per day, the
 * server's figures, and a day sheet. Everything shown comes from the server's answer.
 */
export function ManagerMonthScreen() {
  const { canCreate } = useManagerActions(MONTH_ROUTE);
  const [month, setMonth] = useState(() => currentMonth(serverNow()));
  const { status, view, message, reload } = useMonthView(getMonth, month);
  const [selected, setSelected] = useState<string | null>(null);
  const day: MonthDay | undefined = view?.days.find((d) => d.date === selected);
  const requests = useRegularizations();
  const [asking, setAsking] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<Regularization | null>(null);
  const { canDelete } = useManagerActions(MONTH_ROUTE);

  return (
    <Screen onRefresh={() => reload({ quiet: true })}>
      <MonthPicker
        value={month}
        kind="history"
        onChange={(next) => {
          setSelected(null);
          setMonth(next);
        }}
      />

      {status === "loading" ? <ActivityIndicator size="large" accessibilityLabel="Loading your month" /> : null}
      {status === "noConnection" ? (
        <Problem title="No connection" text="Check your internet connection and try again." onRetry={() => reload()} />
      ) : null}
      {status === "error" || status === "notFound" ? (
        <Problem title="Something went wrong" text={message ?? "We couldn't load this month."} onRetry={() => reload()} />
      ) : null}

      {status === "ready" && view ? (
        <>
          <Figures month={view} />
          {view.locked ? (
            <Chip icon="lock" compact accessibilityLabel="Locked">
              Locked
            </Chip>
          ) : null}
          {view.days.length === 0 ? (
            <Text variant="bodyMedium">No days to show for this month.</Text>
          ) : (
            <MonthGrid days={view.days} present={presentManagerDay} onSelect={(d) => setSelected(d.date)} />
          )}
        </>
      ) : null}

      {day && view ? (
        <ManagerDaySheet
          key={day.date}
          day={day}
          locked={view.locked}
          canCreate={canCreate}
          onChanged={() => reload({ quiet: true })}
          onAskCheckIn={(date) => {
            setSelected(null);
            setAsking(date);
          }}
          onClose={() => setSelected(null)}
        />
      ) : null}

      {status === "ready" && view ? (
        <View style={styles.requests}>
          <Text variant="titleMedium" accessibilityRole="header">
            Check-in requests
          </Text>
          {requests.state === "ready" && requests.items.length === 0 ? (
            <Text variant="bodyMedium">You have no requests to add a forgotten check-in.</Text>
          ) : null}
          {requests.state === "error" || requests.state === "noConnection" ? (
            <Text variant="bodyMedium">
              {requests.state === "noConnection" ? "No connection. Your requests could not be loaded." : (requests.message ?? "Your requests could not be loaded.")}
            </Text>
          ) : null}
          {requests.items.map((request) => (
            <RequestRow key={request.id} request={request} canCancel={canDelete} onCancel={() => setCancelling(request)} />
          ))}
        </View>
      ) : null}

      {asking ? (
        <RegularizationForm
          date={asking}
          onCancel={() => setAsking(null)}
          onDone={() => {
            setAsking(null);
            requests.reload();
          }}
        />
      ) : null}
      {cancelling ? (
        <ReasonDialog
          title="Cancel this request?"
          message={`Check-in for ${formatIsoDate(cancelling.date)}`}
          confirmLabel="Cancel request"
          dismissLabel="Keep request"
          onConfirm={async () => {
            try {
              await cancelRegularization(cancelling.id);
            } catch (error) {
              return managerRefusalText(error);
            }
            setCancelling(null);
            requests.reload();
            reload({ quiet: true });
            return null;
          }}
          onCancel={() => setCancelling(null)}
        />
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  figures: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit * 2 },
  stat: { alignItems: "center", minWidth: 72 },
  requests: { gap: spacingUnit },
  request: { gap: spacingUnit / 2, paddingVertical: spacingUnit },
  chip: { alignSelf: "flex-start" },
  button: { minHeight: minTouchTarget },
});
