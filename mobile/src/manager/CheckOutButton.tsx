import { useState } from "react";
import { Linking, StyleSheet, View } from "react-native";
import { Button, HelperText } from "react-native-paper";
import { checkOut } from "../api/managerAttendanceApi";
import { captureFresh } from "../location/LocationGate";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { locationMessage, type LocationProblem } from "./managerMessages";
import { useAction } from "./useAction";

interface Props {
  /** Called once the server has confirmed the check-out. */
  onDone: () => void;
}

/**
 * Check out (spec 033 FR-006): a fresh position at the tap, sent with the request. With no position nothing is sent
 * and the Manager is told how to turn location on. Nothing shows as checked out before the server confirms.
 */
export function CheckOutButton({ onDone }: Props) {
  const [location, setLocation] = useState<LocationProblem | null>(null);
  const send = useAction(async (): Promise<boolean> => {
    setLocation(null);
    const reading = await captureFresh({ askIfNeeded: true });
    if (reading.kind === "none") {
      setLocation(locationMessage(reading.reason));
      return false;
    }
    await checkOut({
      latitude: reading.reading.latitude,
      longitude: reading.reading.longitude,
      accuracyMeters: reading.reading.accuracyMeters,
    });
    return true;
  });

  return (
    <View style={styles.box}>
      <Button
        mode="contained"
        onPress={async () => {
          if (await send.run()) onDone();
        }}
        loading={send.sending}
        disabled={send.sending}
        contentStyle={styles.button}
        accessibilityLabel="Check out"
      >
        Check out
      </Button>
      {location ? (
        <View style={styles.box}>
          <HelperText type="error" visible accessibilityRole="alert">
            {location.text}
          </HelperText>
          {location.canOpenSettings ? (
            <Button onPress={() => void Linking.openSettings()} contentStyle={styles.button}>
              Open settings
            </Button>
          ) : null}
        </View>
      ) : null}
      {send.error ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {send.error}
        </HelperText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
