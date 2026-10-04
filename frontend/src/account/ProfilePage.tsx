import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Chip,
  Link,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../auth/useAuth";
import { MyTeacherProfile } from "../features/teachers/MyTeacherProfile";
import { getProfile, type AccountProfile } from "./accountApi";

function Detail({ label, value }: { label: string; value: string | null }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" component="dt">
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0 }}>
        {value && value.length > 0 ? value : "Not set"}
      </Typography>
    </Box>
  );
}

/**
 * My profile (every signed-in user): the caller's own account details, read-only, from
 * {@code GET /api/v1/me/profile}. A Teacher also sees their Teacher record. Editing the details,
 * changing the password and managing sessions are on the Settings page.
 */
export function ProfilePage() {
  const { user, authFetch } = useAuth();
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

  if (!user) {
    return null;
  }

  return (
    <Box sx={{ maxWidth: 640 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        My profile
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      {!profile && !error && <Typography>Loading your profile…</Typography>}

      {profile && (
        <Paper
          variant="outlined"
          sx={{ p: 3, mb: 3 }}
          component="section"
          aria-labelledby="account-details-heading"
        >
          <Typography
            variant="h6"
            component="h2"
            id="account-details-heading"
            gutterBottom
          >
            Account details
          </Typography>
          <Stack component="dl" spacing={1.5} sx={{ m: 0 }}>
            <Detail label="Name" value={profile.displayName} />
            <Detail label="Phone number" value={profile.phone} />
            <Detail label="Username" value={profile.username} />
            <Detail label="Email" value={profile.email} />
            <Box>
              <Typography
                variant="caption"
                color="text.secondary"
                component="dt"
              >
                Roles
              </Typography>
              <Stack
                component="dd"
                direction="row"
                spacing={1}
                sx={{ m: 0, mt: 0.5 }}
              >
                {profile.roles.map((role) => (
                  <Chip key={role} label={role} size="small" />
                ))}
              </Stack>
            </Box>
          </Stack>
          <Typography variant="body2" sx={{ mt: 2 }}>
            To change your details or password, or to manage your signed-in
            devices, go to{" "}
            <Link component={RouterLink} to="/account/settings">
              Settings
            </Link>
            .
          </Typography>
        </Paper>
      )}

      {user.roles.includes("TEACHER") && <MyTeacherProfile />}
    </Box>
  );
}
