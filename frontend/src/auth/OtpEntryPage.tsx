import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { Alert, Box, Button, TextField, Typography } from "@mui/material";
import { OtpRequestError, requestOtp } from "./authApi";
import { useAuth } from "./useAuth";
import { useCountdown } from "./useCountdown";

interface PhoneFormValues {
  phone: string;
}

interface CodeFormValues {
  code: string;
}

const DEFAULT_RESEND_COOLDOWN_SECONDS = 30;

/**
 * One-time code sign-in, any role (User Story 2, FR-006). Step 1 requests a code by SMS; step 2
 * verifies it. The request step always shows the same neutral confirmation (FR-007) unless
 * throttled (FR-028/FR-029): resending is disabled for a cooldown after each send, driven by the
 * server's `Retry-After` when it rejects a too-soon resend, so the UI stays accurate even if the
 * cooldown is reconfigured (research.md §17) without a client redeploy.
 */
export function OtpEntryPage() {
  const [step, setStep] = useState<"phone" | "code">("phone");
  const [phone, setPhone] = useState("");
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { loginWithOtp } = useAuth();
  const navigate = useNavigate();
  const cooldown = useCountdown();

  const phoneForm = useForm<PhoneFormValues>();
  const codeForm = useForm<CodeFormValues>();

  async function sendCode(targetPhone: string) {
    setError(null);
    try {
      await requestOtp(targetPhone, "SMS", "SIGN_IN");
      setPhone(targetPhone);
      setInfo("If this number is registered, a code has been sent.");
      setStep("code");
      cooldown.start(DEFAULT_RESEND_COOLDOWN_SECONDS);
    } catch (err) {
      if (err instanceof OtpRequestError) {
        setError(err.message);
        if (err.retryAfterSeconds) {
          cooldown.start(err.retryAfterSeconds);
        }
      } else {
        setError("Could not request a code.");
      }
    }
  }

  const onRequestCode = phoneForm.handleSubmit(({ phone: enteredPhone }) =>
    sendCode(enteredPhone),
  );

  const onVerifyCode = codeForm.handleSubmit(async ({ code }) => {
    setError(null);
    try {
      await loginWithOtp(phone, code);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign in failed.");
    }
  });

  if (step === "phone") {
    return (
      <Box component="form" onSubmit={onRequestCode} noValidate>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} role="alert">
            {error}
          </Alert>
        )}
        <TextField
          label="Phone number"
          fullWidth
          margin="normal"
          autoComplete="tel"
          {...phoneForm.register("phone", { required: true })}
        />
        <Button
          type="submit"
          variant="contained"
          fullWidth
          sx={{ mt: 2 }}
          disabled={phoneForm.formState.isSubmitting}
        >
          Send code
        </Button>
      </Box>
    );
  }

  return (
    <Box component="form" onSubmit={onVerifyCode} noValidate>
      {info && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {info}
        </Alert>
      )}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      <TextField
        label="One-time code"
        fullWidth
        margin="normal"
        autoComplete="one-time-code"
        {...codeForm.register("code", { required: true })}
      />
      <Button
        type="submit"
        variant="contained"
        fullWidth
        sx={{ mt: 2 }}
        disabled={codeForm.formState.isSubmitting}
      >
        Verify and sign in
      </Button>
      <Button
        variant="outlined"
        fullWidth
        sx={{ mt: 1 }}
        disabled={cooldown.secondsRemaining > 0}
        onClick={() => sendCode(phone)}
      >
        {cooldown.secondsRemaining > 0
          ? `Resend code in ${cooldown.secondsRemaining}s`
          : "Resend code"}
      </Button>
      <Button
        variant="text"
        fullWidth
        sx={{ mt: 1 }}
        onClick={() => {
          setStep("phone");
          setError(null);
        }}
      >
        Use a different number
      </Button>
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{ display: "block", mt: 1, textAlign: "center" }}
      >
        Code sent to {phone}
      </Typography>
    </Box>
  );
}
