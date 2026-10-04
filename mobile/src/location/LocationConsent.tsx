import AsyncStorage from "@react-native-async-storage/async-storage";
import * as Location from "expo-location";
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { Button, Dialog, Portal, Text } from "react-native-paper";

/** The plain-language explanation shown before the system permission prompt (spec FR-019). */
export const LOCATION_EXPLANATION =
  "HLS records where your phone is only when the app talks to the HLS server, for example when you " +
  "sign in or open a screen. It does not track you in the background or while the app is idle. " +
  "Your location is used only to show Admins where an action came from, as an audit record. " +
  "If you choose not to allow it, the app still works.";

const EXPLAINED_KEY = "hls.locationExplained";

interface ConsentContextValue {
  /**
   * Explains location use and asks for the permission, but only the first time, while the permission
   * has never been decided. It resolves when the user has answered, and never rejects, so a "no"
   * simply lets the call go ahead without a location.
   */
  ensureConsent: () => Promise<void>;
}

const ConsentContext = createContext<ConsentContextValue | null>(null);

export function LocationConsentProvider({ children }: { children: ReactNode }) {
  const [visible, setVisible] = useState(false);
  const pending = useRef<{ resolve: () => void } | null>(null);
  const asking = useRef<Promise<void> | null>(null);

  const finish = useCallback(() => {
    setVisible(false);
    pending.current?.resolve();
    pending.current = null;
  }, []);

  const ensureConsent = useCallback(async () => {
    if (asking.current) return asking.current;
    const run = (async () => {
      try {
        const permission = await Location.getForegroundPermissionsAsync();
        const explained = (await AsyncStorage.getItem(EXPLAINED_KEY)) === "yes";
        // Already decided on this device, or the user already saw the explanation and said "not now".
        if (permission.status !== "undetermined" || explained) return;
        await new Promise<void>((resolve) => {
          pending.current = { resolve };
          setVisible(true);
        });
      } catch {
        // Consent is best effort; sign-in must never be blocked by it (spec FR-021).
      }
    })().finally(() => {
      asking.current = null;
    });
    asking.current = run;
    return run;
  }, []);

  async function allow() {
    await AsyncStorage.setItem(EXPLAINED_KEY, "yes").catch(() => undefined);
    try {
      await Location.requestForegroundPermissionsAsync();
    } catch {
      // A failed prompt leaves the permission undecided; the call simply goes ahead without location.
    }
    finish();
  }

  async function notNow() {
    await AsyncStorage.setItem(EXPLAINED_KEY, "yes").catch(() => undefined);
    finish();
  }

  const value = useMemo(() => ({ ensureConsent }), [ensureConsent]);

  return (
    <ConsentContext.Provider value={value}>
      {children}
      <Portal>
        <Dialog visible={visible} onDismiss={notNow} dismissable={false}>
          <Dialog.Title accessibilityRole="header">Location and privacy</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium">{LOCATION_EXPLANATION}</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => void notNow()} accessibilityLabel="Not now">
              Not now
            </Button>
            <Button onPress={() => void allow()} accessibilityLabel="Continue">
              Continue
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </ConsentContext.Provider>
  );
}

export function useLocationConsent(): ConsentContextValue {
  const context = useContext(ConsentContext);
  if (!context) throw new Error("useLocationConsent must be used inside LocationConsentProvider");
  return context;
}
