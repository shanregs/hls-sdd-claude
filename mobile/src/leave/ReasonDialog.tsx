import { useRef, useState } from "react";
import { StyleSheet } from "react-native";
import { Button, Dialog, HelperText, Portal, Text, TextInput } from "react-native-paper";
import { minTouchTarget } from "../theme/tokens";

export const MAX_LEAVE_TEXT = 500;

interface Props {
  title: string;
  /** Explains what will happen when confirmed. */
  message?: string;
  /** Label of the text field; leave out for a plain confirmation with no text. */
  fieldLabel?: string;
  /** The text must not be empty (a reason for Reject and Revoke). */
  required?: boolean;
  maxLength?: number;
  confirmLabel: string;
  /** Label of the button that closes the dialog without sending (default "Cancel"). */
  dismissLabel?: string;
  /** Sends the action; resolves with the refusal text to show, or null once the server accepted it. */
  onConfirm: (text: string) => Promise<string | null>;
  onCancel: () => void;
}

/**
 * One confirmation for Cancel, Approve, Reject and Revoke. It sends nothing until Confirm, disables itself
 * while sending, and keeps the text and shows the reason when the server refuses (spec 020 FR-009).
 */
export function ReasonDialog({
  title,
  message,
  fieldLabel,
  required = false,
  maxLength = MAX_LEAVE_TEXT,
  confirmLabel,
  dismissLabel = "Cancel",
  onConfirm,
  onCancel,
}: Props) {
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  // A ref as well as state: two taps before the next render must still send only once.
  const sending = useRef(false);
  const [problem, setProblem] = useState<string | null>(null);
  const blocked = required && text.trim() === "";

  const confirm = async () => {
    if (sending.current || blocked) return;
    sending.current = true;
    setBusy(true);
    setProblem(null);
    try {
      const result = await onConfirm(text.trim());
      if (result !== null) setProblem(result);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  };

  return (
    <Portal>
      <Dialog visible onDismiss={busy ? undefined : onCancel}>
        <Dialog.Title accessibilityRole="header">{title}</Dialog.Title>
        <Dialog.Content>
          {message ? <Text variant="bodyMedium">{message}</Text> : null}
          {fieldLabel ? (
            <>
              <TextInput
                label={required ? `${fieldLabel} (required)` : `${fieldLabel} (optional)`}
                value={text}
                onChangeText={(value) => setText(value.slice(0, maxLength))}
                multiline
                maxLength={maxLength}
                accessibilityLabel={fieldLabel}
              />
              <HelperText type="info" visible accessibilityLabel={`${maxLength - text.length} characters left`}>
                {maxLength - text.length} characters left
              </HelperText>
            </>
          ) : null}
          {problem ? (
            <HelperText type="error" visible accessibilityRole="alert">
              {problem}
            </HelperText>
          ) : null}
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={onCancel} disabled={busy} contentStyle={styles.button} accessibilityLabel={dismissLabel}>
            {dismissLabel}
          </Button>
          <Button
            mode="contained"
            onPress={() => void confirm()}
            disabled={busy || blocked}
            loading={busy}
            contentStyle={styles.button}
            accessibilityLabel={confirmLabel}
          >
            {confirmLabel}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

const styles = StyleSheet.create({ button: { minHeight: minTouchTarget } });
