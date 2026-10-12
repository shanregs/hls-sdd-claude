import { SettingsForm } from "./SettingsForm";

/** Security Settings: password, lockout, one-time code and session policies (spec 011 US2). */
export function SecuritySettingsPage() {
  return (
    <SettingsForm
      group="security"
      route="/system/security"
      title="Security Settings"
      intro="A change applies to the next sign-in, code request or session. Accounts already locked stay locked and people already signed in stay signed in."
    />
  );
}
