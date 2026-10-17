import { useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, Checkbox, Chip, HelperText, RadioButton, Text, TextInput } from "react-native-paper";
import { cancelManagerLeave, requestManagerLeave, type ManagerLeave, type ManagerLeaveStatus } from "../api/managerLeaveApi";
import { formatBusinessDateTime } from "../formats/manager";
import { formatIsoDate } from "../formats/dates";
import { DateChooser } from "../leave/DateChooser";
import { ReasonDialog } from "../leave/ReasonDialog";
import { managerRefusalText } from "../manager/managerMessages";
import { useAction } from "../manager/useAction";
import { useManagerActions } from "../manager/useManagerActions";
import { useManagerLeave } from "../manager/useManagerLeave";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

const LEAVE_ROUTE = "/manager-attendance/me/leave";
const MAX_REASON = 500;

const STATUS_TEXT: Record<ManagerLeaveStatus, string> = {
  PENDING: "Pending",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
  REVOKED: "Revoked",
};

interface Draft {
  leaveTypeId: string | null;
  firstDate: string | null;
  lastDate: string | null;
  firstHalf: boolean;
  lastHalf: boolean;
  reason: string;
}

const EMPTY: Draft = { leaveTypeId: null, firstDate: null, lastDate: null, firstHalf: false, lastHalf: false, reason: "" };

/** Whether the form can be sent: only what needs no rules (a type, both dates in order, a reason). The server decides the rest. */
function ready(draft: Draft): boolean {
  return (
    draft.leaveTypeId !== null &&
    draft.firstDate !== null &&
    draft.lastDate !== null &&
    draft.lastDate >= draft.firstDate &&
    draft.reason.trim() !== ""
  );
}

function Request({ leave, canCancel, onCancel }: { leave: ManagerLeave; canCancel: boolean; onCancel: () => void }) {
  const dates = `${formatIsoDate(leave.firstDate)} – ${formatIsoDate(leave.lastDate)}`;
  const halves = [leave.firstHalf ? "half day at the start" : null, leave.lastHalf ? "half day at the end" : null]
    .filter((part): part is string => Boolean(part))
    .join(", ");
  const decision =
    leave.status === "PENDING" || leave.status === "CANCELLED"
      ? null
      : `${STATUS_TEXT[leave.status]}${leave.decidedAt ? ` on ${formatBusinessDateTime(leave.decidedAt)}` : ""}`;
  const spoken = [leave.leaveTypeName, dates, halves || null, STATUS_TEXT[leave.status], decision, leave.decisionReason]
    .filter(Boolean)
    .join(", ");
  return (
    <View style={styles.card} accessible accessibilityLabel={spoken}>
      <Text variant="titleMedium">{leave.leaveTypeName}{leave.lossOfPay ? " (unpaid)" : ""}</Text>
      <Text variant="bodyLarge">{dates}</Text>
      {halves ? <Text variant="bodyMedium">{halves}</Text> : null}
      <Chip compact style={styles.chip}>
        {STATUS_TEXT[leave.status]}
      </Chip>
      <Text variant="bodyMedium">Reason: {leave.reason}</Text>
      {decision ? <Text variant="bodyMedium">{decision}</Text> : null}
      {leave.decisionReason ? <Text variant="bodyMedium">Reason given: {leave.decisionReason}</Text> : null}
      {canCancel && leave.status === "PENDING" ? (
        <Button mode="outlined" onPress={onCancel} contentStyle={styles.button} accessibilityLabel={`Cancel request for ${dates}`}>
          Cancel request
        </Button>
      ) : null}
    </View>
  );
}

/**
 * The Manager's own leave (spec 033 US5): a request form and the list of requests with the decision. The server's list of
 * types, its refusals and its decisions are shown as they are; only a missing type, date or reason is caught before sending.
 */
export function ManagerLeaveScreen() {
  const { canCreate, canDelete } = useManagerActions(LEAVE_ROUTE);
  const list = useManagerLeave();
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [notice, setNotice] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<ManagerLeave | null>(null);

  const submit = useAction(async (): Promise<boolean> => {
    setNotice(null);
    await requestManagerLeave({
      leaveTypeId: draft.leaveTypeId ?? "",
      firstDate: draft.firstDate ?? "",
      lastDate: draft.lastDate ?? "",
      firstHalf: draft.firstHalf,
      lastHalf: draft.lastHalf,
      reason: draft.reason.trim(),
    });
    return true;
  });

  const change = (patch: Partial<Draft>) => {
    setDraft({ ...draft, ...patch });
    setNotice(null);
  };

  if (list.state === "loading") {
    return (
      <Screen>
        <ActivityIndicator accessibilityLabel="Loading your leave" />
      </Screen>
    );
  }
  if (list.state !== "ready") {
    return (
      <Screen onRefresh={() => list.reload()}>
        <HelperText type="error" visible accessibilityRole="alert">
          {list.state === "noConnection" ? "No connection. Your leave could not be loaded." : (list.message ?? "Your leave could not be loaded.")}
        </HelperText>
        <Button mode="contained" onPress={() => list.reload()} contentStyle={styles.button}>
          Try again
        </Button>
      </Screen>
    );
  }

  return (
    <Screen onRefresh={() => list.reload({ quiet: true })}>
      {canCreate ? (
        <View style={styles.form}>
          <Text variant="titleMedium" accessibilityRole="header">
            Request leave
          </Text>
          <RadioButton.Group onValueChange={(id) => change({ leaveTypeId: id })} value={draft.leaveTypeId ?? ""}>
            {list.types.map((type) => (
              <RadioButton.Item
                key={type.id}
                label={type.lossOfPay ? `${type.name} (unpaid)` : type.name}
                value={type.id}
                accessibilityLabel={type.lossOfPay ? `${type.name} (unpaid)` : type.name}
                style={styles.item}
              />
            ))}
          </RadioButton.Group>
          <DateChooser
            label="First date"
            value={draft.firstDate}
            onChange={(date) => change({ firstDate: date, lastDate: draft.lastDate && draft.lastDate < date ? date : draft.lastDate })}
          />
          <DateChooser label="Last date" value={draft.lastDate} onChange={(date) => change({ lastDate: date })} />
          {draft.firstDate && draft.lastDate && draft.lastDate < draft.firstDate ? (
            <HelperText type="error" visible accessibilityRole="alert">
              The last date cannot be before the first date.
            </HelperText>
          ) : null}
          <Checkbox.Item
            label="Half day on the first day"
            status={draft.firstHalf ? "checked" : "unchecked"}
            onPress={() => change({ firstHalf: !draft.firstHalf })}
            accessibilityLabel="Half day on the first day"
            style={styles.item}
          />
          <Checkbox.Item
            label="Half day on the last day"
            status={draft.lastHalf ? "checked" : "unchecked"}
            onPress={() => change({ lastHalf: !draft.lastHalf })}
            accessibilityLabel="Half day on the last day"
            style={styles.item}
          />
          <TextInput
            label="Reason (required)"
            value={draft.reason}
            onChangeText={(reason) => change({ reason: reason.slice(0, MAX_REASON) })}
            multiline
            maxLength={MAX_REASON}
            accessibilityLabel="Reason"
          />
          <HelperText type="info" visible accessibilityLabel={`${MAX_REASON - draft.reason.length} characters left`}>
            {MAX_REASON - draft.reason.length} characters left
          </HelperText>
          {submit.error ? (
            <HelperText type="error" visible accessibilityRole="alert">
              {submit.error}
            </HelperText>
          ) : null}
          <Button
            mode="contained"
            onPress={async () => {
              if (await submit.run()) {
                setDraft(EMPTY);
                setNotice("Request submitted");
                list.reload({ quiet: true });
              }
            }}
            disabled={!ready(draft) || submit.sending}
            loading={submit.sending}
            contentStyle={styles.button}
            accessibilityLabel="Submit"
          >
            Submit
          </Button>
          {notice ? (
            <HelperText type="info" visible accessibilityRole="alert">
              {notice}
            </HelperText>
          ) : null}
        </View>
      ) : null}

      <Text variant="titleMedium" accessibilityRole="header">
        Your requests
      </Text>
      {list.items.length === 0 ? <Text variant="bodyMedium">You have not asked for leave yet.</Text> : null}
      {list.items.map((leave) => (
        <Request key={leave.id} leave={leave} canCancel={canDelete} onCancel={() => setCancelling(leave)} />
      ))}

      {cancelling ? (
        <ReasonDialog
          title="Cancel this request?"
          message={`${cancelling.leaveTypeName}, ${formatIsoDate(cancelling.firstDate)} – ${formatIsoDate(cancelling.lastDate)}`}
          confirmLabel="Cancel request"
          dismissLabel="Keep request"
          onConfirm={async () => {
            try {
              await cancelManagerLeave(cancelling.id);
            } catch (error) {
              return managerRefusalText(error);
            }
            setCancelling(null);
            list.reload({ quiet: true });
            return null;
          }}
          onCancel={() => setCancelling(null)}
        />
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacingUnit },
  card: { gap: spacingUnit / 2, paddingVertical: spacingUnit },
  chip: { alignSelf: "flex-start" },
  item: { minHeight: minTouchTarget },
  button: { minHeight: minTouchTarget },
});
