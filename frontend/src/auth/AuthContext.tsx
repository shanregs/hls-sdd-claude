import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { authClient, type TokenPair } from "./authClient";

interface AuthState {
  accessToken: string | null;
  isAuthenticated: boolean;
  setTokens: (tokens: TokenPair) => void;
  logout: () => Promise<void>;
}

// research.md §7: the access token lives only in this component's state — never
// localStorage/sessionStorage — so it disappears the moment the tab is closed
// or this provider unmounts. The refresh token never enters JS at all.
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);

  const setTokens = useCallback((tokens: TokenPair) => {
    setAccessToken(tokens.accessToken);
  }, []);

  const logout = useCallback(async () => {
    if (accessToken) {
      await authClient.logout(accessToken).catch(() => {
        // Best-effort: even if the network call fails, clear local state so the
        // UI doesn't keep presenting a token that the server may have already revoked.
      });
    }
    setAccessToken(null);
  }, [accessToken]);

  const value = useMemo<AuthState>(
    () => ({ accessToken, isAuthenticated: accessToken !== null, setTokens, logout }),
    [accessToken, setTokens, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
