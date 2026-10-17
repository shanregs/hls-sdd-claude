import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, Dialog, HelperText, Portal, TextInput } from "react-native-paper";
import { completeCheckOut } from "../api/managerAttendanceApi";
import { formatIsoDate } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { useAction } from "./useAction";

/** The longest reason the server accepts. */
export const MAX_REASON = 500;

interface Props {
  /** The day whose check-out is missing, as YYYY-MM-DD. */
  date: string;
  /** Called once the server has completed the day's check-out. */
  onDone: () => void;
  onCancel: () => void;
}

/**
 * Completes the check-out of an earlier day (spec 033 FR-007): a time on that day and a reason. Send stays disabled
 * until both are given; the server decides whether the time is acceptable and its reason is shown with the entries kept.
 */
export function CompleteCheckOutDialog({ date, onDone, onCancel }: Props) {
  const [time, setTime] = useState("");
  const [reason, setReason] = useState("");
  const send = useAction(async () => {
    await completeCheckOut(date, { time: time.trim(), reason: reason.trim() });
    return true;
  });
  const ready = time.trim() !== "" && reason.trim() !== "";

  return (
    <Portal>
      <Dialog visible onDismiss={send.sending ? undefined : onCancel}>
        <Dialog.Title>Complete check-out for {formatIsoDate(date)}</Dialog.Title>
        <Dialog.Content>
          <View style={styles.fields}>
            <TextInput
              label="Time (HH:mm)"
              value={time}
              onChangeText={setTime}
              keyboardType="numbers-and-punctuation"
              accessibilityLabel="Check-out time"
            />
            <TextInput
              label="Reason"
              value={reason}
              onChangeText={setReason}
              maxLength={MAX_REASON}
              multiline
              accessibilityLabel="Reason"
            />
            <HelperText type="info" visible accessibilityLabel={`${MAX_REASON - reason.length} characters left`}>
              {MAX_REASON - reason.length} characters left
            </HelperText>
            {send.error ? (
              <HelperText type="error" visible accessibilityRole="alert">
                {send.error}
              </HelperText>
            ) : null}
          </View>
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={onCancel} disabled={send.sending} contentStyle={styles.button}>
            Cancel
          </Button>
          <Button
            mode="contained"
            disabled={!ready || send.sending}
            loading={send.sending}
            contentStyle={styles.button}
            onPress={async () => {
              if (await send.run()) onDone();
            }}
          >
            Send
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

const styles = StyleSheet.create({
  fields: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
