import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText, RadioButton, Text, TextInput } from "react-native-paper";
import {
  completePasswordReset,
  fetchResetChannels,
  requestResetCode,
  verifyResetCode,
  type ResetChannel,
} from "../api/authApi";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { signInErrorMessage } from "./errorMessages";
import { Screen } from "./Screen";
import type { RootStackParamList } from "./SignInScreen";

type Props = NativeStackScreenProps<RootStackParamList, "ResetPassword">;
type Step = "identify" | "code" | "password";

/** Matches the server's rule: at least 10 characters (spec 001 FR-017). */
export const MIN_PASSWORD_LENGTH = 10;

/**
 * Password reset by one-time code (spec 001 FR-016): identify the account, pick where to send the
 * code when more than one place is available, enter the code, then choose a new password. Every
 * response about the account is neutral, so nothing here reveals whether an account exists.
 */
export function ResetPasswordScreen({ navigation }: Props) {
  const [step, setStep] = useState<Step>("identify");
  const [identifier, setIdentifier] = useState("");
  const [channels, setChannels] = useState<ResetChannel[]>(["SMS"]);
  const [channel, setChannel] = useState<ResetChannel>("SMS");
  const [code, setCode] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(signInErrorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const identify = () =>
    run(async () => {
      const available = (await fetchResetChannels(identifier.trim())) as ResetChannel[];
      const offered: ResetChannel[] = available.length > 1 ? [...available, "BOTH"] : ["SMS"];
      setChannels(offered);
      setChannel(offered[0]);
      if (offered.length === 1) {
        await requestResetCode(identifier.trim(), offered[0]);
        setStep("code");
      }
    });

  const sendCode = () =>
    run(async () => {
      await requestResetCode(identifier.trim(), channel);
      setStep("code");
    });

  const verify = () =>
    run(async () => {
      const result = await verifyResetCode(identifier.trim(), code.trim(), channel);
      setResetToken(result.resetToken);
      setStep("password");
    });

  const finish = () =>
    run(async () => {
      await completePasswordReset(resetToken, password);
      navigation.navigate("SignIn", { notice: "Password changed. Sign in with your new password." });
    });

  const passwordTooShort = password.length > 0 && password.length < MIN_PASSWORD_LENGTH;
  const mismatch = confirm.length > 0 && confirm !== password;
  const canFinish = password.length >= MIN_PASSWORD_LENGTH && confirm === password;
  const choosing = step === "identify" && channels.length > 1;

  return (
    <Screen>
      <Text variant="headlineMedium" accessibilityRole="header">
        Reset your password
      </Text>

      {step === "identify" ? (
        <View style={styles.form}>
          <TextInput
            label="Phone number or username"
            value={identifier}
            onChangeText={setIdentifier}
            autoCapitalize="none"
            autoCorrect={false}
            accessibilityLabel="Phone number or username"
          />
          {choosing ? (
            <RadioButton.Group onValueChange={(v) => setChannel(v as ResetChannel)} value={channel}>
              <Text variant="titleSmall">Send the code to</Text>
              {channels.map((c) => (
                <RadioButton.Item
                  key={c}
                  value={c}
                  label={c === "SMS" ? "My phone (SMS)" : c === "EMAIL" ? "My email" : "Both phone and email"}
                  accessibilityLabel={c}
                />
              ))}
            </RadioButton.Group>
          ) : null}
          <Button
            mode="contained"
            onPress={choosing ? sendCode : identify}
            loading={busy}
            disabled={busy || identifier.trim() === ""}
            contentStyle={styles.buttonContent}
            accessibilityLabel="Send code"
          >
            Send code
          </Button>
        </View>
      ) : null}

      {step === "code" ? (
        <View style={styles.form}>
          <HelperText type="info" visible accessibilityLiveRegion="polite">
            If this account exists, a code has been sent.
          </HelperText>
          <TextInput
            label="Code"
            value={code}
            onChangeText={setCode}
            keyboardType="number-pad"
            maxLength={6}
            accessibilityLabel="One-time code"
          />
          <Button
            mode="contained"
            onPress={verify}
            loading={busy}
            disabled={busy || code.trim().length < 6}
            contentStyle={styles.buttonContent}
            accessibilityLabel="Verify code"
          >
            Verify code
          </Button>
        </View>
      ) : null}

      {step === "password" ? (
        <View style={styles.form}>
          <TextInput
            label="New password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            autoCapitalize="none"
            accessibilityLabel="New password"
          />
          <HelperText type={passwordTooShort ? "error" : "info"} visible>
            At least {MIN_PASSWORD_LENGTH} characters.
          </HelperText>
          <TextInput
            label="Confirm new password"
            value={confirm}
            onChangeText={setConfirm}
            secureTextEntry
            autoCapitalize="none"
            accessibilityLabel="Confirm new password"
          />
          <HelperText type="error" visible={mismatch}>
            The passwords do not match.
          </HelperText>
          <Button
            mode="contained"
            onPress={finish}
            loading={busy}
            disabled={busy || !canFinish}
            contentStyle={styles.buttonContent}
            accessibilityLabel="Change password"
          >
            Change password
          </Button>
        </View>
      ) : null}

      {error ? (
        <HelperText type="error" visible accessibilityLiveRegion="assertive">
          {error}
        </HelperText>
      ) : null}

      <Button mode="text" onPress={() => navigation.navigate("SignIn")} contentStyle={styles.buttonContent} accessibilityLabel="Back to sign in">
        Back to sign in
      </Button>
    </Screen>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacingUnit },
  buttonContent: { minHeight: minTouchTarget },
});
