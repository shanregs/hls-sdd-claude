import { StyleSheet } from "react-native";
import { Button, Text } from "react-native-paper";
import { useAuth } from "../auth/AuthProvider";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

/**
 * Shown when a signed-in user opens the app with no internet (spec clarification Q3): nothing cached
 * is presented as current, only a way to try again.
 */
export function NoConnectionScreen({ onRetry }: { onRetry?: () => void } = {}) {
  const { retry } = useAuth();
  return (
    <Screen>
      <Text variant="headlineMedium" accessibilityRole="header">
        No connection
      </Text>
      <Text variant="bodyLarge">
        HLS needs an internet connection to show your information. Check your connection and try again.
      </Text>
      <Button
        mode="contained"
        onPress={() => (onRetry ? onRetry() : void retry())}
        style={styles.button}
        contentStyle={styles.content}
        accessibilityLabel="Retry"
      >
        Retry
      </Button>
    </Screen>
  );
}

const styles = StyleSheet.create({
  button: { marginTop: spacingUnit * 2 },
  content: { minHeight: minTouchTarget },
});
