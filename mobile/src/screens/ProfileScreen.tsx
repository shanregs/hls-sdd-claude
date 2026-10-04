import { useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { ActivityIndicator, Button, HelperText, SegmentedButtons, Text, TextInput } from "react-native-paper";
import { getProfile, updateProfile, type AccountProfile } from "../api/profileApi";
import { ApiError, NoConnectionError } from "../api/errors";
import { useAuth } from "../auth/AuthProvider";
import { useThemePreference, type ThemePreference } from "../theme/ThemeProvider";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

/**
 * ACCOUNT → Profile (spec FR-014): the user's own details as on the web (name, username, email;
 * the phone number is read-only), a way to the signed-in devices, the theme setting, and Log out.
 */
export function ProfileScreen({
  onOpenDevices,
  onOpenPrivacy,
}: {
  onOpenDevices: () => void;
  onOpenPrivacy: () => void;
}) {
  const { logout } = useAuth();
  const { preference, setPreference } = useThemePreference();
  const [profile, setProfile] = useState<AccountProfile | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveMessage, setSaveMessage] = useState<{ type: "info" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    getProfile()
      .then((loaded) => {
        if (cancelled) return;
        setProfile(loaded);
        setDisplayName(loaded.displayName);
        setUsername(loaded.username ?? "");
        setEmail(loaded.email ?? "");
        setLoadError(null);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setLoadError(
          e instanceof NoConnectionError
            ? "No connection. Check your internet and try again."
            : "We couldn't load your profile. Please try again.",
        );
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  async function save() {
    setBusy(true);
    setSaveMessage(null);
    try {
      const saved = await updateProfile({ displayName: displayName.trim(), username: username.trim(), email: email.trim() });
      setProfile(saved);
      setSaveMessage({ type: "info", text: "Profile saved." });
    } catch (e) {
      setSaveMessage({
        type: "error",
        text:
          e instanceof NoConnectionError
            ? "No connection. Check your internet and try again."
            : e instanceof ApiError && (e.status === 400 || e.status === 409)
              ? e.message
              : "We couldn't save your profile. Please try again.",
      });
    } finally {
      setBusy(false);
    }
  }

  const changed =
    profile !== null &&
    (displayName.trim() !== profile.displayName ||
      username.trim() !== (profile.username ?? "") ||
      email.trim() !== (profile.email ?? ""));

  return (
    <Screen>
      <Text variant="headlineMedium" accessibilityRole="header">
        Profile
      </Text>

      {loadError ? (
        <View>
          <HelperText type="error" visible accessibilityLiveRegion="assertive">
            {loadError}
          </HelperText>
          <Button mode="outlined" onPress={() => setReloadKey((k) => k + 1)} contentStyle={styles.buttonContent} accessibilityLabel="Try again">
            Try again
          </Button>
        </View>
      ) : null}

      {profile === null && !loadError ? <ActivityIndicator accessibilityLabel="Loading profile" /> : null}

      {profile ? (
        <View style={styles.form}>
          <TextInput label="Phone number" value={profile.phone} editable={false} accessibilityLabel="Phone number" />
          <TextInput label="Name" value={displayName} onChangeText={setDisplayName} accessibilityLabel="Name" />
          <TextInput
            label="Username"
            value={username}
            onChangeText={setUsername}
            autoCapitalize="none"
            autoCorrect={false}
            accessibilityLabel="Username"
          />
          <TextInput
            label="Email"
            value={email}
            onChangeText={setEmail}
            keyboardType="email-address"
            autoCapitalize="none"
            accessibilityLabel="Email"
          />
          {saveMessage ? (
            <HelperText type={saveMessage.type} visible accessibilityLiveRegion="polite">
              {saveMessage.text}
            </HelperText>
          ) : null}
          <Button
            mode="contained"
            onPress={() => void save()}
            loading={busy}
            disabled={busy || !changed || displayName.trim() === ""}
            contentStyle={styles.buttonContent}
            accessibilityLabel="Save profile"
          >
            Save
          </Button>
        </View>
      ) : null}

      <Text variant="titleMedium" style={styles.heading}>
        Appearance
      </Text>
      <SegmentedButtons
        value={preference}
        onValueChange={(value) => setPreference(value as ThemePreference)}
        buttons={[
          { value: "system", label: "Device", accessibilityLabel: "Follow device theme" },
          { value: "light", label: "Light", accessibilityLabel: "Light theme" },
          { value: "dark", label: "Dark", accessibilityLabel: "Dark theme" },
        ]}
      />

      <Button mode="outlined" onPress={onOpenDevices} contentStyle={styles.buttonContent} accessibilityLabel="Signed-in devices">
        Signed-in devices
      </Button>
      <Button mode="outlined" onPress={onOpenPrivacy} contentStyle={styles.buttonContent} accessibilityLabel="Location and privacy">
        Location and privacy
      </Button>
      <Button mode="outlined" onPress={() => void logout()} contentStyle={styles.buttonContent} accessibilityLabel="Log out">
        Log out
      </Button>
    </Screen>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacingUnit },
  heading: { marginTop: spacingUnit * 2 },
  buttonContent: { minHeight: minTouchTarget },
});
