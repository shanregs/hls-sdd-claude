import { Text } from "react-native-paper";
import { currentAppVersion } from "../config/version";
import { Screen } from "./Screen";

/** The app is older than the server's minimum version and cannot continue (spec FR-029). */
export function UpdateRequiredScreen({ minimumVersion }: { minimumVersion?: string }) {
  return (
    <Screen>
      <Text variant="headlineMedium" accessibilityRole="header">
        Please update the HLS app
      </Text>
      <Text variant="bodyLarge">
        This version of the app ({currentAppVersion()}) is no longer supported
        {minimumVersion ? `. Version ${minimumVersion} or later is required` : ""}. Update the app to
        continue.
      </Text>
    </Screen>
  );
}
