import type { ReactNode } from "react";
import { StyleSheet, View } from "react-native";
import { Button, Text } from "react-native-paper";
import { openVisitFile, type Visit } from "../api/marketingApi";
import { formatIsoDate } from "../formats/dates";
import { AttachmentList } from "../media/AttachmentList";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { MAX_VISIT_FILES } from "../tasks/taskMessages";
import { visitTarget } from "./VisitCard";
import { visitKindText, visitStatusText } from "./visitMessages";

export interface VisitActions {
  onComplete?: () => void;
  onReschedule?: () => void;
  onCancel?: () => void;
  onAddPhoto?: () => void;
}

interface Props {
  visit: Visit;
  /** The menu grants Create (adding a photo). */
  canCreate: boolean;
  /** The menu grants Edit (complete, move, cancel). */
  canEdit: boolean;
  actions?: VisitActions;
  /** Anything to show under the facts, such as photos that failed to upload. */
  children?: ReactNode;
}

/**
 * One visit as the server holds it (spec 034 FR-003). Complete, Reschedule and Cancel are offered when the menu grants Edit and
 * the visit's stored status is planned, which is the rule the server applies; a Missed visit is still stored as planned.
 */
export function VisitDetail({ visit, canCreate, canEdit, actions = {}, children }: Props) {
  const planned = visit.status === "PLANNED";
  const mayChange = canEdit && planned;
  return (
    <View style={styles.box}>
      <Text variant="titleLarge" accessibilityRole="header">
        {visitTarget(visit)}
      </Text>
      <Text variant="bodyLarge">
        {visitKindText(visit.type)}, {formatIsoDate(visit.date)}
      </Text>
      <Text variant="titleMedium">{visitStatusText(visit.effectiveStatus)}</Text>

      {visit.attendees.length > 0 ? (
        <Text variant="bodyMedium">Attending: {visit.attendees.map((a) => a.name).join(", ")}</Text>
      ) : null}
      {visit.notes ? <Text variant="bodyMedium">Notes: {visit.notes}</Text> : null}
      {visit.dateHistory.map((change, index) => (
        <Text key={`${change.from}-${index}`} variant="bodyMedium">
          Moved from {formatIsoDate(change.from)} to {formatIsoDate(change.to)}
        </Text>
      ))}

      {visit.status === "COMPLETED" ? (
        <View style={styles.box}>
          <Text variant="titleSmall">Outcome</Text>
          <Text variant="bodyMedium">{visit.outcome}</Text>
          {visit.followUpOn ? (
            <Text variant="bodyMedium">
              Follow-up on {formatIsoDate(visit.followUpOn)}
              {visit.followUpOverdue ? ". The follow-up date has passed." : ""}
            </Text>
          ) : null}
        </View>
      ) : null}
      {visit.status === "CANCELLED" ? <Text variant="bodyMedium">Cancelled because: {visit.cancelReason}</Text> : null}

      <AttachmentList files={visit.attachments} open={openVisitFile} />
      {children}

      <View style={styles.actions}>
        {mayChange && actions.onComplete ? (
          <Button mode="contained" onPress={actions.onComplete} contentStyle={styles.button}>
            Complete visit
          </Button>
        ) : null}
        {mayChange && actions.onReschedule ? (
          <Button mode="outlined" onPress={actions.onReschedule} contentStyle={styles.button}>
            Reschedule
          </Button>
        ) : null}
        {mayChange && actions.onCancel ? (
          <Button mode="outlined" onPress={actions.onCancel} contentStyle={styles.button}>
            Cancel visit
          </Button>
        ) : null}
        {canCreate && actions.onAddPhoto && visit.attachments.length < MAX_VISIT_FILES ? (
          <Button onPress={actions.onAddPhoto} contentStyle={styles.button}>
            Add photo
          </Button>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  actions: { gap: spacingUnit, marginTop: spacingUnit },
  button: { minHeight: minTouchTarget },
});
