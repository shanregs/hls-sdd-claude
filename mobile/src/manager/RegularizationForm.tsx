import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, Dialog, HelperText, Portal, RadioButton, TextInput } from "react-native-paper";
import { requestRegularization, type DayType } from "../api/managerAttendanceApi";
import { formatIsoDate } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { useAction } from "./useAction";

/** The longest purpose or reason the server accepts. */
export const MAX_TEXT = 500;

interface Props {
  /** The past day the check-in is asked for, as YYYY-MM-DD. */
  date: string;
  /** Called once the server has taken the request. */
  onDone: () => void;
  onCancel: () => void;
}

/**
 * Asks for a forgotten check-in to be added (spec 033 FR-013): the time the Manager arrived, Office or Field, a purpose
 * for Field, and a reason. Send stays disabled until a time and a reason are given; the server decides everything else
 * and its reason is shown with the entries kept.
 */
export function RegularizationForm({ date, onDone, onCancel }: Props) {
  const [time, setTime] = useState("");
  const [dayType, setDayType] = useState<DayType>("OFFICE");
  const [purpose, setPurpose] = useState("");
  const [reason, setReason] = useState("");
  const send = useAction(async () => {
    await requestRegularization({
      date,
      proposedTime: time.trim(),
      dayType,
      purpose: dayType === "FIELD" && purpose.trim() !== "" ? purpose.trim() : undefined,
      reason: reason.trim(),
    });
    return true;
  });
  const ready = time.trim() !== "" && reason.trim() !== "";

  return (
    <Portal>
      <Dialog visible onDismiss={send.sending ? undefined : onCancel}>
        <Dialog.Title>Ask to add a check-in for {formatIsoDate(date)}</Dialog.Title>
        <Dialog.Content>
          <View style={styles.fields}>
            <TextInput
              label="Time you arrived (HH:mm)"
              value={time}
              onChangeText={setTime}
              keyboardType="numbers-and-punctuation"
              accessibilityLabel="Time you arrived"
            />
            <RadioButton.Group onValueChange={(value) => setDayType(value as DayType)} value={dayType}>
              <RadioButton.Item label="Office" value="OFFICE" accessibilityLabel="Office day" style={styles.radio} />
              <RadioButton.Item label="Field" value="FIELD" accessibilityLabel="Field day" style={styles.radio} />
            </RadioButton.Group>
            {dayType === "FIELD" ? (
              <TextInput
                label="Purpose"
                value={purpose}
                onChangeText={setPurpose}
                maxLength={MAX_TEXT}
                multiline
                accessibilityLabel="Purpose"
              />
            ) : null}
            <TextInput
              label="Reason (required)"
              value={reason}
              onChangeText={setReason}
              maxLength={MAX_TEXT}
              multiline
              accessibilityLabel="Reason"
            />
            <HelperText type="info" visible accessibilityLabel={`${MAX_TEXT - reason.length} characters left`}>
              {MAX_TEXT - reason.length} characters left
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
  radio: { minHeight: minTouchTarget },
  button: { minHeight: minTouchTarget },
});
