import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Paper,
  Radio,
  RadioGroup,
  FormControlLabel,
  TextField,
  Typography,
} from "@mui/material";
import {
  completePasswordReset,
  getResetChannels,
  OtpRequestError,
  requestOtp,
  verifyOtpForPasswordReset,
  type OtpChannel,
} from "./authApi";
import { useCountdown } from "./useCountdown";

const DEFAULT_RESEND_COOLDOWN_SECONDS = 30;

type Step = "identifier" | "code" | "newPassword" | "done";

interface IdentifierFormValues {
  identifier: string;
}

interface CodeFormValues {
  code: string;
}

interface PasswordFormValues {
  newPassword: string;
}

/**
 * Reset a forgotten password with a code sent to the phone (SMS) or, if registered, the email on
 * file — the user's choice (FR-016, Constitution v2.3.0). Only channels the account actually has
 * are offered, without revealing whether the identifier itself is registered.
 */
export function ForgotPasswordPage() {
  const [step, setStep] = useState<Step>("identifier");
  const [identifier, setIdentifier] = useState("");
  const [channels, setChannels] = useState<OtpChannel[]>(["SMS"]);
  const [channel, setChannel] = useState<OtpChannel>("SMS");
  const [resetToken, setResetToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const cooldown = useCountdown();

  const identifierForm = useForm<IdentifierFormValues>();
  const codeForm = useForm<CodeFormValues>();
  const passwordForm = useForm<PasswordFormValues>();

  const onSubmitIdentifier = identifierForm.handleSubmit(
    async ({ identifier: entered }) => {
      setError(null);
      const available = await getResetChannels(entered);
      setIdentifier(entered);
      setChannels(available);
      setChannel(available[0]);
      setStep("code");
    },
  );

  const onSendCode = async () => {
    setError(null);
    try {
      await requestOtp(identifier, channel, "PASSWORD_RESET");
      cooldown.start(DEFAULT_RESEND_COOLDOWN_SECONDS);
    } catch (err) {
      if (err instanceof OtpRequestError) {
        setError(err.message);
        if (err.retryAfterSeconds) {
          cooldown.start(err.retryAfterSeconds);
        }
      } else {
        setError("Could not send a code.");
      }
    }
  };

  const onVerifyCode = codeForm.handleSubmit(async ({ code }) => {
    setError(null);
    try {
      const token = await verifyOtpForPasswordReset(identifier, code, channel);
      setResetToken(token);
      setStep("newPassword");
    } catch (err) {
      setError(err instanceof Error ? err.message : "That code is not valid.");
    }
  });

  const onSetNewPassword = passwordForm.handleSubmit(
    async ({ newPassword }) => {
      setError(null);
      if (!resetToken) {
        return;
      }
      try {
        await completePasswordReset(resetToken, newPassword);
        setStep("done");
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Could not reset your password.",
        );
      }
    },
  );

  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        minHeight: "100vh",
        px: 2,
      }}
    >
      <Paper sx={{ p: 4, width: "100%", maxWidth: 400 }} elevation={2}>
        <Typography variant="h5" component="h1" gutterBottom>
          Reset your password
        </Typography>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }} role="alert">
            {error}
          </Alert>
        )}

        {step === "identifier" && (
          <Box component="form" onSubmit={onSubmitIdentifier} noValidate>
            <Typography color="text.secondary" sx={{ mb: 2 }}>
              Enter your phone number or username.
            </Typography>
            <TextField
              label="Phone number or username"
              fullWidth
              margin="normal"
              {...identifierForm.register("identifier", { required: true })}
            />
            <Button type="submit" variant="contained" fullWidth sx={{ mt: 2 }}>
              Continue
            </Button>
          </Box>
        )}

        {step === "code" && (
          <Box component="form" onSubmit={onVerifyCode} noValidate>
            <Typography sx={{ mb: 1 }}>
              How should we send your code?
            </Typography>
            <RadioGroup
              value={channel}
              onChange={(_, value) => setChannel(value as OtpChannel)}
              aria-label="Delivery channel"
            >
              {channels.includes("SMS") && (
                <FormControlLabel
                  value="SMS"
                  control={<Radio />}
                  label="Text message (SMS)"
                />
              )}
              {channels.includes("EMAIL") && (
                <FormControlLabel
                  value="EMAIL"
                  control={<Radio />}
                  label="Email"
                />
              )}
              {channels.includes("SMS") && channels.includes("EMAIL") && (
                <FormControlLabel
                  value="BOTH"
                  control={<Radio />}
                  label="Both SMS and email"
                />
              )}
            </RadioGroup>
            <Button
              variant="outlined"
              fullWidth
              sx={{ mt: 1, mb: 2 }}
              disabled={cooldown.secondsRemaining > 0}
              onClick={onSendCode}
            >
              {cooldown.secondsRemaining > 0
                ? `Resend code in ${cooldown.secondsRemaining}s`
                : "Send code"}
            </Button>
            <TextField
              label="Code"
              fullWidth
              margin="normal"
              {...codeForm.register("code", { required: true })}
            />
            <Button type="submit" variant="contained" fullWidth sx={{ mt: 2 }}>
              Verify code
            </Button>
          </Box>
        )}

        {step === "newPassword" && (
          <Box component="form" onSubmit={onSetNewPassword} noValidate>
            <TextField
              label="New password"
              type="password"
              fullWidth
              margin="normal"
              helperText="At least 10 characters, not your phone number"
              {...passwordForm.register("newPassword", {
                required: true,
                minLength: 10,
              })}
            />
            <Button type="submit" variant="contained" fullWidth sx={{ mt: 2 }}>
              Set new password
            </Button>
          </Box>
        )}

        {step === "done" && (
          <Box>
            <Typography sx={{ mb: 2 }}>
              Your password has been reset. Please sign in again.
            </Typography>
            <Button
              variant="contained"
              fullWidth
              onClick={() => navigate("/sign-in", { replace: true })}
            >
              Go to sign in
            </Button>
          </Box>
        )}
      </Paper>
    </Box>
  );
}
