import { useEffect } from "react";
import { useAuth } from "../auth/AuthProvider";
import { startConnectivity } from "../outbox/connectivity";
import { useThemePreference } from "./ThemeProvider";

/**
 * Renders nothing. Inside the sign-in provider it loads the user's saved theme after sign-in and
 * sends a change that was made offline when the connection comes back (spec 039 US1, US4). It reuses
 * the spec 036 connectivity triggers (foreground, network, a timer while something waits); a theme
 * change is one small "replace the whole preference" request, so it does not need the claim outbox.
 */
export function ThemeSync() {
  const { state } = useAuth();
  const { syncAfterSignIn, signedOut, flush, waiting } = useThemePreference();
  const userId = state.status === "signedIn" ? state.user.id : null;

  useEffect(() => {
    if (userId) void syncAfterSignIn(userId);
    else if (state.status === "signedOut") signedOut();
  }, [userId, state.status, syncAfterSignIn, signedOut]);

  useEffect(() => {
    if (!userId) return;
    const connectivity = startConnectivity(() => void flush());
    connectivity.setWaiting(waiting);
    return () => connectivity.stop();
  }, [userId, waiting, flush]);

  return null;
}
