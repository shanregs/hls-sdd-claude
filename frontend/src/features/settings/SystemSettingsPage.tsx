import { SettingsForm } from "./SettingsForm";

/** System Settings: the mobile app values (spec 011 US3). */
export function SystemSettingsPage() {
  return (
    <SettingsForm
      group="system"
      route="/system/settings"
      title="System Settings"
      intro="Values the Android app reads before anyone signs in. A change applies on the app's next check."
    />
  );
}
