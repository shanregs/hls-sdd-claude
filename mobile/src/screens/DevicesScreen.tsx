import { useCallback, useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { ActivityIndicator, Button, Card, Dialog, HelperText, Portal, Text } from "react-native-paper";
import { endSession, listSessions, type SessionInfo } from "../api/sessionsApi";
import { NoConnectionError } from "../api/errors";
import { formatDateTime } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

function describe(session: SessionInfo): string {
  if (session.clientType === "ANDROID") {
    return session.appVersion ? `HLS Android app ${session.appVersion}` : "HLS Android app";
  }
  return "Web browser";
}

/**
 * "Signed-in devices": the caller's active sessions from the app and the web, the current one
 * marked, with a way to end any other (spec FR-007).
 */
export function DevicesScreen({ onBack }: { onBack: () => void }) {
  const [sessions, setSessions] = useState<SessionInfo[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState<SessionInfo | null>(null);
  const [busy, setBusy] = useState(false);

  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey((k) => k + 1), []);

  useEffect(() => {
    let cancelled = false;
    listSessions()
      .then((list) => {
        if (cancelled) return;
        setError(null);
        setSessions(list);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(
          e instanceof NoConnectionError
            ? "No connection. Check your internet and try again."
            : "We couldn't load your devices. Please try again.",
        );
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  async function end(session: SessionInfo) {
    setBusy(true);
    try {
      await endSession(session.id);
      setConfirming(null);
      reload();
    } catch {
      setConfirming(null);
      setError("We couldn't end that session. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen>
      <Text variant="headlineMedium" accessibilityRole="header">
        Signed-in devices
      </Text>
      <Text variant="bodyMedium">These are the places you are signed in to HLS.</Text>

      {error ? (
        <View>
          <HelperText type="error" visible accessibilityLiveRegion="assertive">
            {error}
          </HelperText>
          <Button mode="outlined" onPress={reload} contentStyle={styles.buttonContent} accessibilityLabel="Try again">
            Try again
          </Button>
        </View>
      ) : null}

      {sessions === null && !error ? <ActivityIndicator accessibilityLabel="Loading devices" /> : null}

      {sessions?.map((session) => (
        <Card key={session.id} style={styles.card} accessibilityLabel={`${describe(session)}${session.current ? ", this device" : ""}`}>
          <Card.Content style={styles.cardContent}>
            <Text variant="titleMedium">{describe(session)}</Text>
            {session.current ? <Text variant="labelLarge">This device</Text> : null}
            <Text variant="bodySmall">Signed in {formatDateTime(session.signedInAt)}</Text>
            <Text variant="bodySmall">Last used {formatDateTime(session.lastActivityAt)}</Text>
          </Card.Content>
          {session.current ? null : (
            <Card.Actions>
              <Button
                onPress={() => setConfirming(session)}
                contentStyle={styles.buttonContent}
                accessibilityLabel={`End session on ${describe(session)}`}
              >
                End session
              </Button>
            </Card.Actions>
          )}
        </Card>
      ))}

      <Button mode="text" onPress={onBack} contentStyle={styles.buttonContent} accessibilityLabel="Back">
        Back
      </Button>

      <Portal>
        <Dialog visible={confirming !== null} onDismiss={() => setConfirming(null)}>
          <Dialog.Title>End this session?</Dialog.Title>
          <Dialog.Content>
            <Text>{confirming ? `${describe(confirming)} will be signed out.` : ""}</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setConfirming(null)} accessibilityLabel="Cancel">Cancel</Button>
            <Button onPress={() => confirming && void end(confirming)} loading={busy} disabled={busy} accessibilityLabel="Confirm end session">
              End session
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { marginTop: spacingUnit },
  cardContent: { gap: spacingUnit / 2 },
  buttonContent: { minHeight: minTouchTarget },
});
