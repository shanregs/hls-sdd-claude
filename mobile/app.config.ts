import type { ExpoConfig } from "expo/config";

/**
 * Android-first build settings (spec 018, research.md §4, §6, §13):
 *  - minSdk 29 (Android 10 and later)
 *  - foreground location only: no ACCESS_BACKGROUND_LOCATION (FR-018)
 *  - allowBackup false so credentials never reach a device backup (FR-004)
 */
const config: ExpoConfig = {
  name: "HLS",
  slug: "hls-mobile",
  version: "1.0.0",
  orientation: "portrait",
  icon: "./assets/icon.png",
  userInterfaceStyle: "automatic",
  android: {
    // Application id to be confirmed with the team before the first store build.
    package: "com.hls.mobile",
    versionCode: 1,
    allowBackup: false,
    permissions: ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"],
    blockedPermissions: ["android.permission.ACCESS_BACKGROUND_LOCATION"],
    adaptiveIcon: {
      backgroundColor: "#E6F4FE",
      foregroundImage: "./assets/android-icon-foreground.png",
      backgroundImage: "./assets/android-icon-background.png",
      monochromeImage: "./assets/android-icon-monochrome.png",
    },
  },
  plugins: [
    "expo-secure-store",
    [
      "expo-location",
      {
        isAndroidBackgroundLocationEnabled: false,
        isAndroidForegroundServiceEnabled: false,
        locationWhenInUsePermission:
          "HLS records where you are only when the app talks to the server, to keep an audit trail.",
      },
    ],
    ["expo-build-properties", { android: { minSdkVersion: 29 } }],
  ],
  extra: {
    apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://10.0.2.2:8080",
  },
};

export default config;
