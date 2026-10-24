import { useCallback, useState, type ReactElement } from "react";
import { StyleSheet } from "react-native";
import { Button, Dialog, Portal, Text } from "react-native-paper";
import { signOutWarning } from "../expenses/expenseMessages";
import { useOptionalExpenseOutbox } from "../outbox/ExpenseOutboxProvider";
import { minTouchTarget } from "../theme/tokens";
import { useAuth } from "./AuthProvider";

export interface SignOutGuard {
  /** Signs out at once, or first asks when the signed-in user has claims that are not sent yet. */
  requestLogout: () => void;
  /** The warning dialog to render once in the screen; null when it is not showing. */
  dialog: ReactElement | null;
}

/**
 * Log out with a warning (spec 036 FR-024, research §11). The claims stay on the phone, encrypted, and are sent when the
 * same user signs in again; signing out never deletes them.
 */
export function useSignOutGuard(): SignOutGuard {
  const { logout } = useAuth();
  const outbox = useOptionalExpenseOutbox();
  const [asking, setAsking] = useState(false);
  const unsent = outbox?.unsentCount ?? 0;

  const requestLogout = useCallback(() => {
    if (unsent > 0) setAsking(true);
    else void logout();
  }, [unsent, logout]);

  const dialog = asking ? (
    <Portal>
      <Dialog visible onDismiss={() => setAsking(false)}>
        <Dialog.Title accessibilityRole="header">Claims not sent</Dialog.Title>
        <Dialog.Content>
          <Text variant="bodyMedium">{signOutWarning(unsent)}</Text>
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={() => setAsking(false)} contentStyle={styles.button} accessibilityLabel="Stay">
            Stay
          </Button>
          <Button
            onPress={() => {
              setAsking(false);
              void logout();
            }}
            contentStyle={styles.button}
            accessibilityLabel="Sign out"
          >
            Sign out
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  ) : null;

  return { requestLogout, dialog };
}

const styles = StyleSheet.create({ button: { minHeight: minTouchTarget } });
