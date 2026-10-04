import { StyleSheet } from "react-native";
import { Button, Text } from "react-native-paper";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

/** Shown for a destination that is not in the user's server-provided navigation (spec FR-013). */
export function NotAuthorizedScreen({ onGoHome }: { onGoHome: () => void }) {
  return (
    <Screen>
      <Text variant="headlineMedium" accessibilityRole="header">
        Not authorized
      </Text>
      <Text variant="bodyLarge">You do not have access to that page.</Text>
      <Button
        mode="contained"
        onPress={onGoHome}
        style={styles.button}
        contentStyle={styles.content}
        accessibilityLabel="Go to home"
      >
        Go to home
      </Button>
    </Screen>
  );
}

const styles = StyleSheet.create({
  button: { marginTop: spacingUnit * 2 },
  content: { minHeight: minTouchTarget },
});
