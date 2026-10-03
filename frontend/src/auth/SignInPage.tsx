import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Link as MuiLink,
  Paper,
  Tab,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import { OtpEntryPage } from "./OtpEntryPage";
import { useAuth } from "./useAuth";

interface PasswordFormValues {
  identifier: string;
  password: string;
}

/**
 * Single sign-in page with Password (phone number or username) and One-time code modes, plus a
 * "Forgot password?" link, and no role picker at any point (FR-023). Both modes are available to
 * every role (Constitution v2.3.0) — the Password mode is fully wired in User Story 1; the
 * One-time code mode is wired in User Story 2.
 */
export function SignInPage() {
  const [mode, setMode] = useState<"password" | "otp">("password");
  const [serverError, setServerError] = useState<string | null>(null);
  const { loginWithPassword } = useAuth();
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<PasswordFormValues>();

  const onSubmitPassword = handleSubmit(async ({ identifier, password }) => {
    setServerError(null);
    try {
      await loginWithPassword(identifier, password);
      navigate("/", { replace: true });
    } catch (error) {
      setServerError(
        error instanceof Error ? error.message : "Sign in failed.",
      );
    }
  });

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
          HLS Teacher Management System
        </Typography>

        <Tabs
          value={mode}
          onChange={(_, value) => setMode(value)}
          aria-label="Sign-in method"
          sx={{ mb: 2 }}
        >
          <Tab
            value="password"
            label="Password"
            id="tab-password"
            aria-controls="panel-password"
          />
          <Tab
            value="otp"
            label="One-time code"
            id="tab-otp"
            aria-controls="panel-otp"
          />
        </Tabs>

        {serverError && (
          <Alert severity="error" sx={{ mb: 2 }} role="alert">
            {serverError}
          </Alert>
        )}

        {mode === "password" && (
          <Box
            component="form"
            id="panel-password"
            role="tabpanel"
            aria-labelledby="tab-password"
            onSubmit={onSubmitPassword}
            noValidate
          >
            <TextField
              label="Phone number or username"
              fullWidth
              margin="normal"
              autoComplete="username"
              error={Boolean(errors.identifier)}
              helperText={
                errors.identifier
                  ? "Phone number or username is required"
                  : undefined
              }
              {...register("identifier", { required: true })}
            />
            <TextField
              label="Password"
              type="password"
              fullWidth
              margin="normal"
              autoComplete="current-password"
              error={Boolean(errors.password)}
              helperText={errors.password ? "Password is required" : undefined}
              {...register("password", { required: true })}
            />
            <Button
              type="submit"
              variant="contained"
              fullWidth
              sx={{ mt: 2 }}
              disabled={isSubmitting}
            >
              Sign in
            </Button>
            <Box sx={{ mt: 2, textAlign: "center" }}>
              <MuiLink href="/forgot-password" underline="hover">
                Forgot password?
              </MuiLink>
            </Box>
          </Box>
        )}

        {mode === "otp" && (
          <Box id="panel-otp" role="tabpanel" aria-labelledby="tab-otp">
            <OtpEntryPage />
          </Box>
        )}
      </Paper>
    </Box>
  );
}
