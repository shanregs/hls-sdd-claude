import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import {
  Alert,
  Box,
  Button,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../auth/useAuth";
import {
  changePassword,
  getProfile,
  updateProfile,
  type AccountProfile,
} from "./accountApi";

interface ProfileForm {
  displayName: string;
  username: string;
  email: string;
}

interface PasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const MIN_PASSWORD = 10;

function ProfileCard({
  profile,
  onSaved,
}: {
  profile: AccountProfile;
  onSaved: (next: AccountProfile) => void;
}) {
  const { authFetch } = useAuth();
  const [saved, setSaved] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProfileForm>({
    defaultValues: {
      displayName: profile.displayName,
      username: profile.username ?? "",
      email: profile.email ?? "",
    },
  });

  const submit = handleSubmit(async (values) => {
    setSaved(false);
    const result = await updateProfile(authFetch, values);
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    setSaved(true);
    onSaved(result.data);
  });

  return (
    <Paper
      variant="outlined"
      sx={{ p: 2 }}
      component="section"
      aria-labelledby="profile-heading"
    >
      <Typography variant="h6" component="h2" id="profile-heading" gutterBottom>
        Profile
      </Typography>
      <form onSubmit={submit} noValidate>
        <Stack spacing={2} sx={{ maxWidth: 480 }}>
          {errors.root && (
            <Alert severity="error" role="alert">
              {errors.root.message}
            </Alert>
          )}
          {saved && <Alert severity="success">Your profile was saved.</Alert>}
          <TextField
            label="Name"
            required
            error={!!errors.displayName}
            helperText={errors.displayName?.message}
            slotProps={{ htmlInput: { maxLength: 160 } }}
            {...register("displayName", { required: "Your name is required." })}
          />
          <TextField
            label="Phone number"
            value={profile.phone}
            disabled
            helperText="Your phone number is your sign-in. Ask an Admin to change it."
          />
          <TextField
            label="Username"
            error={!!errors.username}
            helperText={
              errors.username?.message ??
              "Optional. 3 to 30 letters, digits, dots, dashes or underscores."
            }
            {...register("username", {
              validate: (v) =>
                v === "" ||
                /^[A-Za-z0-9._-]{3,30}$/.test(v) ||
                "Use 3 to 30 letters, digits, dots, dashes or underscores.",
            })}
          />
          <TextField
            label="Email"
            type="email"
            error={!!errors.email}
            helperText={
              errors.email?.message ??
              "Optional. Used to send a password reset if you ask for one."
            }
            {...register("email", {
              validate: (v) =>
                v === "" ||
                /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v) ||
                "Enter a valid email address.",
            })}
          />
          <Typography variant="body2" color="text.secondary">
            Roles: {profile.roles.join(", ")}
          </Typography>
          <Box>
            <Button type="submit" variant="contained" disabled={isSubmitting}>
              Save profile
            </Button>
          </Box>
        </Stack>
      </form>
    </Paper>
  );
}

function PasswordCard() {
  const { authFetch } = useAuth();
  const [changed, setChanged] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<PasswordForm>({
    defaultValues: {
      currentPassword: "",
      newPassword: "",
      confirmPassword: "",
    },
  });

  const submit = handleSubmit(async (values) => {
    setChanged(false);
    const result = await changePassword(authFetch, {
      currentPassword: values.currentPassword,
      newPassword: values.newPassword,
    });
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    reset();
    setChanged(true);
  });

  return (
    <Paper
      variant="outlined"
      sx={{ p: 2 }}
      component="section"
      aria-labelledby="password-heading"
    >
      <Typography
        variant="h6"
        component="h2"
        id="password-heading"
        gutterBottom
      >
        Change password
      </Typography>
      <form onSubmit={submit} noValidate>
        <Stack spacing={2} sx={{ maxWidth: 480 }}>
          {errors.root && (
            <Alert severity="error" role="alert">
              {errors.root.message}
            </Alert>
          )}
          {changed && (
            <Alert severity="success">
              Your password was changed. Your other signed-in devices were
              signed out.
            </Alert>
          )}
          <TextField
            label="Current password"
            type="password"
            autoComplete="current-password"
            required
            error={!!errors.currentPassword}
            helperText={errors.currentPassword?.message}
            {...register("currentPassword", {
              required: "Enter your current password.",
            })}
          />
          <TextField
            label="New password"
            type="password"
            autoComplete="new-password"
            required
            error={!!errors.newPassword}
            helperText={
              errors.newPassword?.message ??
              `At least ${MIN_PASSWORD} characters, and not your phone number.`
            }
            {...register("newPassword", {
              required: "Enter a new password.",
              minLength: {
                value: MIN_PASSWORD,
                message: `The password must be at least ${MIN_PASSWORD} characters.`,
              },
            })}
          />
          <TextField
            label="Confirm new password"
            type="password"
            autoComplete="new-password"
            required
            error={!!errors.confirmPassword}
            helperText={errors.confirmPassword?.message}
            {...register("confirmPassword", {
              required: "Confirm your new password.",
              validate: (v) =>
                v === getValues("newPassword") || "The passwords do not match.",
            })}
          />
          <Box>
            <Button type="submit" variant="contained" disabled={isSubmitting}>
              Change password
            </Button>
          </Box>
        </Stack>
      </form>
    </Paper>
  );
}

/** Settings (every signed-in user): edit your own profile and change your own password. Sessions have their own menu item. */
export function SettingsPage() {
  const { authFetch } = useAuth();
  const [profile, setProfile] = useState<AccountProfile | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await getProfile(authFetch);
      if (cancelled) return;
      if (result.ok) setProfile(result.data);
      else setError(result.reason);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  return (
    <Box>
      <Typography variant="h5" component="h1" gutterBottom>
        Settings
      </Typography>
      {error && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {!profile && !error && <Typography>Loading your settings…</Typography>}
      {profile && (
        <Stack spacing={3}>
          <ProfileCard profile={profile} onSaved={setProfile} />
          <PasswordCard />
        </Stack>
      )}
    </Box>
  );
}
