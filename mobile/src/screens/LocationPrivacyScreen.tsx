import * as Location from "expo-location";
import { useCallback, useEffect, useState } from "react";
import { Linking, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { LOCATION_EXPLANATION } from "../location/LocationConsent";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

type State = "checking" | "allowed" | "askable" | "blocked" | "servicesOff";

const STATE_TEXT: Record<State, string> = {
  checking: "Checking…",
  allowed: "Allowed while you use the app.",
  askable: "Not allowed yet.",
  blocked: "Not allowed. You can change this in your phone's settings.",
  servicesOff: "Location is switched off on your phone.",
};

/**
 * ACCOUNT → Profile → Location and privacy (spec FR-019): the explanation again, the current state
 * of the permission, and a way to change it. Reading the permission does not read the location.
 */
export function LocationPrivacyScreen({ onBack }: { onBack: () => void }) {
  const [state, setState] = useState<State>("checking");
  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey((k) => k + 1), []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const permission = await Location.getForegroundPermissionsAsync();
        let next: State;
        if (permission.granted) {
          next = (await Location.hasServicesEnabledAsync()) ? "allowed" : "servicesOff";
        } else {
          next = permission.canAskAgain ? "askable" : "blocked";
        }
        if (!cancelled) setState(next);
      } catch {
        if (!cancelled) setState("blocked");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  async function allow() {
    try {
      await Location.requestForegroundPermissionsAsync();
    } finally {
      reload();
    }
  }

  return (
    <Screen>
      <Text variant="headlineMedium" accessibilityRole="header">
        Location and privacy
      </Text>
      <Text variant="bodyLarge">{LOCATION_EXPLANATION}</Text>
      <View accessibilityLiveRegion="polite">
        <HelperText type={state === "allowed" ? "info" : "error"} visible>
          {STATE_TEXT[state]}
        </HelperText>
      </View>
      {state === "askable" ? (
        <Button mode="contained" onPress={() => void allow()} contentStyle={styles.buttonContent} accessibilityLabel="Allow location">
          Allow location
        </Button>
      ) : null}
      {state === "blocked" || state === "servicesOff" ? (
        <Button
          mode="outlined"
          onPress={() => void Linking.openSettings()}
          contentStyle={styles.buttonContent}
          accessibilityLabel="Open phone settings"
        >
          Open phone settings
        </Button>
      ) : null}
      <Button mode="text" onPress={onBack} contentStyle={styles.buttonContent} accessibilityLabel="Back" style={styles.back}>
        Back
      </Button>
    </Screen>
  );
}

const styles = StyleSheet.create({
  buttonContent: { minHeight: minTouchTarget },
  back: { marginTop: spacingUnit },
});
