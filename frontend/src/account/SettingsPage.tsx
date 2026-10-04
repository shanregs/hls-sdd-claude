import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import {
  Alert,
  Box,
  Button,
  Paper,
  Stack,
  Tab,
  Tabs,
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

function ProfileEditForm({
  profile,
  onSaved,
  onCancel,
}: {
  profile: AccountProfile;
  onSaved: (next: AccountProfile) => void;
  onCancel: () => void;
}) {
  const { authFetch } = useAuth();
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
    const result = await updateProfile(authFetch, values);
    if (!result.ok) {
      setError("root", { message: result.reason });
      return;
    }
    onSaved(result.data);
  });

  return (
    <form onSubmit={submit} noValidate>
      <Stack spacing={2} sx={{ maxWidth: 480 }}>
        {errors.root && (
          <Alert severity="error" role="alert">
            {errors.root.message}
          </Alert>
        )}
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
        <Stack direction="row" spacing={1}>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Save profile
          </Button>
          <Button type="button" onClick={onCancel}>
            Cancel
          </Button>
        </Stack>
      </Stack>
    </form>
  );
}

function ProfileDetails({
  profile,
  saved,
  onEdit,
}: {
  profile: AccountProfile;
  saved: boolean;
  onEdit: () => void;
}) {
  const rows: [string, string][] = [
    ["Name", profile.displayName],
    ["Phone number", profile.phone],
    ["Username", profile.username ?? "—"],
    ["Email", profile.email ?? "—"],
    ["Roles", profile.roles.join(", ")],
  ];
  return (
    <Stack spacing={2} sx={{ maxWidth: 480 }}>
      {saved && <Alert severity="success">Your profile was saved.</Alert>}
      <Box component="dl" sx={{ m: 0 }}>
        {rows.map(([label, value]) => (
          <Box key={label} sx={{ display: "flex", py: 0.5 }}>
            <Typography
              component="dt"
              color="text.secondary"
              sx={{ width: 140, flexShrink: 0 }}
            >
              {label}
            </Typography>
            <Typography component="dd" sx={{ m: 0 }}>
              {value}
            </Typography>
          </Box>
        ))}
      </Box>
      <Box>
        <Button variant="outlined" onClick={onEdit}>
          Edit
        </Button>
      </Box>
    </Stack>
  );
}

/** Read-only details; the form appears only after Edit. */
function ProfileSection({
  profile,
  onSaved,
}: {
  profile: AccountProfile;
  onSaved: (next: AccountProfile) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [saved, setSaved] = useState(false);
  return editing ? (
    <ProfileEditForm
      profile={profile}
      onSaved={(next) => {
        onSaved(next);
        setSaved(true);
        setEditing(false);
      }}
      onCancel={() => setEditing(false)}
    />
  ) : (
    <ProfileDetails
      profile={profile}
      saved={saved}
      onEdit={() => {
        setSaved(false);
        setEditing(true);
      }}
    />
  );
}

function ChangePasswordForm() {
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
    <form onSubmit={submit} noValidate>
      <Stack spacing={2} sx={{ maxWidth: 480 }}>
        {errors.root && (
          <Alert severity="error" role="alert">
            {errors.root.message}
          </Alert>
        )}
        {changed && (
          <Alert severity="success">
            Your password was changed. Your other signed-in devices were signed
            out.
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
  );
}

/** Settings (every signed-in user): a Profile section (read-only until Edit) and a Change password section. Sessions have their own menu item. */
export function SettingsPage() {
  const { authFetch } = useAuth();
  const [profile, setProfile] = useState<AccountProfile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<"profile" | "password">("profile");

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
        <Paper variant="outlined">
          <Tabs
            value={tab}
            onChange={(_, v) => setTab(v)}
            aria-label="Settings sections"
            sx={{ borderBottom: 1, borderColor: "divider" }}
          >
            <Tab
              label="Profile"
              value="profile"
              id="settings-tab-profile"
              aria-controls="settings-panel"
            />
            <Tab
              label="Change password"
              value="password"
              id="settings-tab-password"
              aria-controls="settings-panel"
            />
          </Tabs>
          <Box
            role="tabpanel"
            id="settings-panel"
            aria-labelledby={`settings-tab-${tab}`}
            sx={{ p: 2 }}
          >
            {tab === "profile" ? (
              <ProfileSection profile={profile} onSaved={setProfile} />
            ) : (
              <ChangePasswordForm />
            )}
          </Box>
        </Paper>
      )}
    </Box>
  );
}
