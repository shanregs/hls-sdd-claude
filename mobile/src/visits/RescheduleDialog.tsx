import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { ApiError } from "../api/errors";
import { rescheduleVisit, type Visit } from "../api/marketingApi";
import { formatIsoDate } from "../formats/dates";
import { DateChooser } from "../leave/DateChooser";
import { useAction } from "../manager/useAction";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { visitTarget } from "./VisitCard";

interface Props {
  visit: Visit;
  /** Called once the server has moved the visit, with the server's visit. */
  onDone: (visit: Visit) => void;
  /** The server said the visit changed or is gone: the caller reads it again. */
  onStale: () => void;
  onCancel: () => void;
}

/** Moves a planned visit to another date (spec 034 FR-007). The earlier date stays in the visit's history on the server. */
export function RescheduleDialog({ visit, onDone, onStale, onCancel }: Props) {
  const [date, setDate] = useState<string | null>(null);
  const send = useAction(async () => {
    try {
      onDone(await rescheduleVisit(visit.id, { date: date ?? "", version: visit.version }));
    } catch (error) {
      if (error instanceof ApiError && (error.status === 409 || error.status === 404)) onStale();
      throw error;
    }
    return true;
  });

  return (
    <View style={styles.form}>
      <Text variant="titleLarge" accessibilityRole="header">
        Reschedule
      </Text>
      <Text variant="bodyMedium">
        {visitTarget(visit)}, now on {formatIsoDate(visit.date)}
      </Text>
      <DateChooser label="New date" value={date} onChange={setDate} />
      {send.error ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {send.error}
        </HelperText>
      ) : null}
      <Button
        mode="contained"
        onPress={() => void send.run()}
        disabled={!date || send.sending}
        loading={send.sending}
        contentStyle={styles.button}
        accessibilityLabel="Send"
      >
        Send
      </Button>
      <Button onPress={onCancel} disabled={send.sending} contentStyle={styles.button} accessibilityLabel="Cancel">
        Cancel
      </Button>
    </View>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
