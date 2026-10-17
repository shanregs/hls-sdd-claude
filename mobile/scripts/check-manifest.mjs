// Build check (spec 018 T083, quickstart "Build checks"): the Android config must never request
// background location and must disable device backups. Reads the resolved Expo config.
import { execSync } from "node:child_process";

const raw = execSync("npx expo config --type public --json", {
  encoding: "utf8",
  env: { ...process.env, EXPO_NO_TELEMETRY: "1" },
});
const config = JSON.parse(raw);
const android = config.android ?? {};
const problems = [];

const permissions = android.permissions ?? [];
if (permissions.some((p) => p.includes("BACKGROUND_LOCATION"))) {
  problems.push("android.permissions requests ACCESS_BACKGROUND_LOCATION");
}
if (!(android.blockedPermissions ?? []).some((p) => p.includes("ACCESS_BACKGROUND_LOCATION"))) {
  problems.push("ACCESS_BACKGROUND_LOCATION is not in android.blockedPermissions");
}
// The camera is allowed (spec 033: a check-in photo), but nothing wider than the system photo picker needs.
for (const banned of [
  "RECORD_AUDIO",
  "READ_EXTERNAL_STORAGE",
  "WRITE_EXTERNAL_STORAGE",
  "READ_MEDIA_IMAGES",
  "READ_MEDIA_VIDEO",
]) {
  if (permissions.some((p) => p.includes(banned))) {
    problems.push(`android.permissions requests ${banned}`);
  }
}
if (android.allowBackup !== false) {
  problems.push("android.allowBackup is not false");
}
const locationPlugin = (config.plugins ?? []).find(
  (p) => Array.isArray(p) && p[0] === "expo-location",
);
if (!locationPlugin || locationPlugin[1]?.isAndroidBackgroundLocationEnabled !== false) {
  problems.push("expo-location plugin must set isAndroidBackgroundLocationEnabled: false");
}
// Plugins add permissions at build time, so the picker plugin must be set to ask for the camera only.
const pickerPlugin = (config.plugins ?? []).find((p) => Array.isArray(p) && p[0] === "expo-image-picker");
if (pickerPlugin && (pickerPlugin[1]?.microphonePermission !== false || pickerPlugin[1]?.photosPermission !== false)) {
  problems.push("expo-image-picker plugin must set microphonePermission: false and photosPermission: false");
}
const build = (config.plugins ?? []).find(
  (p) => Array.isArray(p) && p[0] === "expo-build-properties",
);
if (!build || build[1]?.android?.minSdkVersion !== 29) {
  problems.push("minSdkVersion must be 29 (Android 10)");
}

if (problems.length > 0) {
  console.error("Manifest check failed:\n - " + problems.join("\n - "));
  process.exit(1);
}
console.log("Manifest check passed.");
