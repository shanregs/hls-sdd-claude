import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText, SegmentedButtons, Text, TextInput } from "react-native-paper";
import { requestSignInCode } from "../api/authApi";
import { RateLimitedError } from "../api/errors";
import { useAuth } from "../auth/AuthProvider";
import { useLocationConsent } from "../location/LocationConsent";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { signInErrorMessage } from "./errorMessages";
import { Screen } from "./Screen";
import { useCountdown } from "./useCountdown";

export type RootStackParamList = {
  SignIn: { notice?: string } | undefined;
  ResetPassword: undefined;
};

type Props = NativeStackScreenProps<RootStackParamList, "SignIn">;
type Method = "password" | "code";

const CODE_SENT_MESSAGE = "If this number is registered, a code has been sent.";

/**
 * One page, two clearly labelled ways to sign in (spec 001 FR-023): phone or username with a
 * password, or a one-time code sent by SMS. The user never chooses a role.
 */
export function SignInScreen({ navigation, route }: Props) {
  const { signInWithPassword, signInWithCode, state } = useAuth();
  const [method, setMethod] = useState<Method>("password");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [codeRequested, setCodeRequested] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const resend = useCountdown();
  const { ensureConsent } = useLocationConsent();

  const notice = route.params?.notice ?? (state.status === "signedOut" ? state.notice : undefined);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(signInErrorMessage(e));
      if (e instanceof RateLimitedError && e.retryAfterSeconds) resend.start(e.retryAfterSeconds);
    } finally {
      setBusy(false);
    }
  }

  // The first time, explain location use and ask for the permission before the call that records it.
  // A "no", or any failure here, only means the call goes ahead without a location (spec FR-021).
  const submitPassword = () =>
    run(async () => {
      await ensureConsent();
      await signInWithPassword(identifier.trim(), password);
    });

  const sendCode = () =>
    run(async () => {
      await ensureConsent();
      await requestSignInCode(phone.trim());
      setCodeRequested(true);
      setInfo(CODE_SENT_MESSAGE);
      resend.start(30);
    });

  const submitCode = () => run(() => signInWithCode(phone.trim(), code.trim()));

  return (
    <Screen>
      <Text variant="headlineMedium" accessibilityRole="header">
        Sign in to HLS
      </Text>
      {notice ? (
        <HelperText type="info" visible accessibilityLiveRegion="polite">
          {notice}
        </HelperText>
      ) : null}

      <SegmentedButtons
        value={method}
        onValueChange={(value) => {
          setMethod(value as Method);
          setError(null);
        }}
        buttons={[
          { value: "password", label: "Password", accessibilityLabel: "Sign in with password" },
          { value: "code", label: "One-time code", accessibilityLabel: "Sign in with a one-time code" },
        ]}
      />

      {method === "password" ? (
        <View style={styles.form}>
          <TextInput
            label="Phone number or username"
            value={identifier}
            onChangeText={setIdentifier}
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete="off"
            textContentType="username"
            accessibilityLabel="Phone number or username"
          />
          <TextInput
            label="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            autoCapitalize="none"
            autoComplete="off"
            textContentType="password"
            accessibilityLabel="Password"
          />
          <Button
            mode="contained"
            onPress={submitPassword}
            loading={busy}
            disabled={busy || identifier.trim() === "" || password === ""}
            style={styles.button}
            contentStyle={styles.buttonContent}
            accessibilityLabel="Sign in"
          >
            Sign in
          </Button>
          <Button
            mode="text"
            onPress={() => navigation.navigate("ResetPassword")}
            contentStyle={styles.buttonContent}
            accessibilityLabel="Forgot password"
          >
            Forgot password?
          </Button>
        </View>
      ) : (
        <View style={styles.form}>
          <TextInput
            label="Phone number"
            value={phone}
            onChangeText={setPhone}
            keyboardType="phone-pad"
            autoComplete="tel"
            accessibilityLabel="Phone number"
          />
          {codeRequested ? (
            <TextInput
              label="Code"
              value={code}
              onChangeText={setCode}
              keyboardType="number-pad"
              maxLength={6}
              autoComplete="sms-otp"
              accessibilityLabel="One-time code"
            />
          ) : null}
          {codeRequested ? (
            <Button
              mode="contained"
              onPress={submitCode}
              loading={busy}
              disabled={busy || code.trim().length < 6}
              style={styles.button}
              contentStyle={styles.buttonContent}
              accessibilityLabel="Verify code and sign in"
            >
              Verify and sign in
            </Button>
          ) : null}
          <Button
            mode={codeRequested ? "text" : "contained"}
            onPress={sendCode}
            disabled={busy || phone.trim() === "" || resend.remaining > 0}
            style={styles.button}
            contentStyle={styles.buttonContent}
            accessibilityLabel={codeRequested ? "Resend code" : "Send code"}
          >
            {resend.remaining > 0
              ? `Resend code in ${resend.remaining}s`
              : codeRequested
                ? "Resend code"
                : "Send code"}
          </Button>
          {info ? (
            <HelperText type="info" visible accessibilityLiveRegion="polite">
              {info}
            </HelperText>
          ) : null}
        </View>
      )}

      {error ? (
        <HelperText type="error" visible accessibilityLiveRegion="assertive">
          {error}
        </HelperText>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacingUnit * 1.5 },
  button: { marginTop: spacingUnit },
  buttonContent: { minHeight: minTouchTarget },
});
