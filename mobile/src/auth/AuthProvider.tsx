import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { fetchAppConfig } from "../api/appConfig";
import {
  loginWithPassword,
  logoutOnServer,
  renewSession,
  verifySignInCode,
  type AuthResult,
  type SignedInUser,
} from "../api/authApi";
import {
  NoConnectionError,
  UnauthorizedError,
  UpdateRequiredError,
} from "../api/errors";
import { setAuthHooks } from "../api/httpClient";
import { compareVersions, currentAppVersion } from "../config/version";
import { resetLocationGate, setLocationTimings } from "../location/LocationGate";
import { getAccessToken, setAccessToken } from "../security/memoryToken";
import {
  clearRenewalCredential,
  readRenewalCredential,
  saveRenewalCredential,
} from "../security/secureStore";

export type AuthState =
  | { status: "restoring" }
  | { status: "signedOut"; notice?: string }
  | { status: "signedIn"; user: SignedInUser }
  | { status: "noConnection" }
  | { status: "updateRequired"; minimumVersion?: string };

interface AuthContextValue {
  state: AuthState;
  signInWithPassword: (identifier: string, password: string) => Promise<void>;
  signInWithCode: (phone: string, code: string) => Promise<void>;
  /** Ends the session on the server and clears everything on the device (spec FR-006). */
  logout: () => Promise<void>;
  /** Starts again from the stored credential: used by the "No connection" screen's Retry. */
  retry: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const SESSION_ENDED_NOTICE = "Your session ended. Please sign in again.";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "restoring" });
  const renewal = useRef<Promise<boolean> | null>(null);
  const mounted = useRef(true);

  const update = useCallback((next: AuthState) => {
    if (mounted.current) setState(next);
  }, []);

  /** Wipes every trace of the session from the device (credential, token, and signed-in state). */
  const clearSession = useCallback(
    async (notice?: string) => {
      setAccessToken(null);
      resetLocationGate();
      await clearRenewalCredential();
      update({ status: "signedOut", notice });
    },
    [update],
  );

  const adopt = useCallback(
    async (result: AuthResult) => {
      await saveRenewalCredential(result.renewalCredential);
      setAccessToken(result.accessToken);
      update({ status: "signedIn", user: result.user });
    },
    [update],
  );

  /** Version gate: an app older than the server's minimum cannot go past Sign In (spec FR-029). */
  const isTooOld = useCallback(async (): Promise<string | null> => {
    const config = await fetchAppConfig();
    // The server tunes how long a call waits for a position and how long one may be shared.
    setLocationTimings({ waitSeconds: config.locationWaitSeconds, reuseSeconds: config.locationReuseSeconds });
    return compareVersions(currentAppVersion(), config.minimumVersion) < 0 ? config.minimumVersion : null;
  }, []);

  const restore = useCallback(async () => {
    update({ status: "restoring" });
    const minimum = await isTooOld();
    if (minimum !== null) {
      update({ status: "updateRequired", minimumVersion: minimum });
      return;
    }
    const credential = await readRenewalCredential();
    if (!credential) {
      update({ status: "signedOut" });
      return;
    }
    try {
      await adopt(await renewSession(credential));
    } catch (error) {
      if (error instanceof NoConnectionError) {
        update({ status: "noConnection" });
      } else if (error instanceof UpdateRequiredError) {
        update({ status: "updateRequired", minimumVersion: error.minimumVersion });
      } else {
        // Expired, revoked or reused: the user signs in again (spec FR-005).
        await clearSession(error instanceof UnauthorizedError ? SESSION_ENDED_NOTICE : undefined);
      }
    }
  }, [adopt, clearSession, isTooOld, update]);

  /**
   * One shared renewal for any number of calls that hit a 401 together. Resolves true when a fresh
   * access token is in memory; clears the session when the server says the credential is no longer
   * valid. A network failure is rethrown so the caller sees "no connection", not a sign-out.
   */
  const renewAccess = useCallback((): Promise<boolean> => {
    if (renewal.current) return renewal.current;
    const run = (async () => {
      const credential = await readRenewalCredential();
      if (!credential) {
        await clearSession(SESSION_ENDED_NOTICE);
        return false;
      }
      try {
        const result = await renewSession(credential);
        await saveRenewalCredential(result.renewalCredential);
        setAccessToken(result.accessToken);
        return true;
      } catch (error) {
        if (error instanceof NoConnectionError) throw error;
        if (error instanceof UpdateRequiredError) {
          update({ status: "updateRequired", minimumVersion: error.minimumVersion });
          return false;
        }
        await clearSession(SESSION_ENDED_NOTICE);
        return false;
      }
    })().finally(() => {
      renewal.current = null;
    });
    renewal.current = run;
    return run;
  }, [clearSession, update]);

  useEffect(() => {
    mounted.current = true;
    setAuthHooks({ renewAccess });
    void restore();
    return () => {
      mounted.current = false;
      setAuthHooks(null);
    };
  }, [renewAccess, restore]);

  const guarded = useCallback(
    async (call: () => Promise<AuthResult>) => {
      try {
        await adopt(await call());
      } catch (error) {
        if (error instanceof UpdateRequiredError) {
          update({ status: "updateRequired", minimumVersion: error.minimumVersion });
        }
        throw error;
      }
    },
    [adopt, update],
  );

  const signInWithPassword = useCallback(
    (identifier: string, password: string) => guarded(() => loginWithPassword(identifier, password)),
    [guarded],
  );

  const signInWithCode = useCallback(
    (phone: string, code: string) => guarded(() => verifySignInCode(phone, code)),
    [guarded],
  );

  const logout = useCallback(async () => {
    // Clear the device first, so nothing survives even if the network call below never completes.
    // The token is read for the server call before it is wiped.
    const token = getAccessToken();
    const call = token ? logoutOnServer(token) : Promise.resolve();
    setAccessToken(null);
    resetLocationGate();
    await clearRenewalCredential();
    update({ status: "signedOut" });
    try {
      await call;
    } catch {
      // Offline or already ended: the server session ends on its own (spec FR-006).
    }
  }, [update]);

  const value = useMemo(
    () => ({ state, signInWithPassword, signInWithCode, logout, retry: restore }),
    [state, signInWithPassword, signInWithCode, logout, restore],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
