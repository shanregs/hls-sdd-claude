import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";
import { AuthProvider } from "./src/auth/AuthProvider";
import { RootNavigator } from "./src/navigation/RootNavigator";
import { installDeviceIntegrityHeader } from "./src/security/deviceIntegrity";
import { installLocationHeaders } from "./src/location/LocationGate";
import { LocationConsentProvider } from "./src/location/LocationConsent";
import { enableSecureWindow } from "./src/security/secureWindow";
import { ThemeProvider } from "./src/theme/ThemeProvider";

export default function App() {
  useEffect(() => installDeviceIntegrityHeader(), []);
  useEffect(() => installLocationHeaders(), []);
  useEffect(() => {
    void enableSecureWindow();
  }, []);

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <ThemeProvider>
          <LocationConsentProvider>
            <AuthProvider>
              <RootNavigator />
              <StatusBar style="auto" />
            </AuthProvider>
          </LocationConsentProvider>
        </ThemeProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
