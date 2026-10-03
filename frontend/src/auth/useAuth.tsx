import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  loginWithPassword,
  logout as logoutRequest,
  renewSession,
  verifyOtpForSignIn,
  type AuthResponse,
  type SignedInUser,
} from "./authApi";

interface AuthContextValue {
  user: SignedInUser | null;
  accessToken: string | null;
  /** True until the initial silent-renewal attempt (on page load) has resolved, so routing can
   * wait rather than flashing the sign-in page for an already-valid session (User Story 3). */
  initializing: boolean;
  loginWithPassword: (identifier: string, password: string) => Promise<void>;
  loginWithOtp: (phone: string, code: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Fetch wrapper for authenticated API calls: attaches the current access token, and on a 401
   * (expired access token) performs one silent renewal via the `HttpOnly` cookie and retries the
   * original request once, transparently (FR-009, User Story 3). */
  authFetch: (input: RequestInfo, init?: RequestInit) => Promise<Response>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Holds the access token in memory only - never localStorage/sessionStorage (research.md §3), so
 * it cannot be exfiltrated by a successful XSS. Restored on page load, and renewed silently as it
 * expires, purely from the `HttpOnly` renewal cookie the browser already holds.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SignedInUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [initializing, setInitializing] = useState(true);

  const applySession = useCallback((response: AuthResponse) => {
    setUser(response.user);
    setAccessToken(response.accessToken);
  }, []);

  const clearSession = useCallback(() => {
    setUser(null);
    setAccessToken(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    renewSession().then((response) => {
      if (cancelled) {
        return;
      }
      if (response) {
        applySession(response);
      }
      setInitializing(false);
    });
    return () => {
      cancelled = true;
    };
  }, [applySession]);

  const handleLoginWithPassword = useCallback(
    async (identifier: string, password: string) => {
      applySession(await loginWithPassword(identifier, password));
    },
    [applySession],
  );

  const handleLoginWithOtp = useCallback(
    async (phone: string, code: string) => {
      applySession(await verifyOtpForSignIn(phone, code));
    },
    [applySession],
  );

  const handleLogout = useCallback(async () => {
    if (accessToken) {
      await logoutRequest(accessToken);
    }
    clearSession();
  }, [accessToken, clearSession]);

  const authFetch = useCallback(
    async (input: RequestInfo, init: RequestInit = {}): Promise<Response> => {
      const withToken = (token: string | null): RequestInit => ({
        ...init,
        credentials: "include",
        headers: {
          ...(init.headers ?? {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      });

      const first = await fetch(input, withToken(accessToken));
      if (first.status !== 401) {
        return first;
      }

      const renewed = await renewSession();
      if (!renewed) {
        clearSession();
        return first;
      }
      applySession(renewed);
      return fetch(input, withToken(renewed.accessToken));
    },
    [accessToken, applySession, clearSession],
  );

  const value = useMemo(
    () => ({
      user,
      accessToken,
      initializing,
      loginWithPassword: handleLoginWithPassword,
      loginWithOtp: handleLoginWithOtp,
      logout: handleLogout,
      authFetch,
    }),
    [
      user,
      accessToken,
      initializing,
      handleLoginWithPassword,
      handleLoginWithOtp,
      handleLogout,
      authFetch,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
