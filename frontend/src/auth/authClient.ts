// research.md §7: the refresh token lives only in the httpOnly cookie the backend
// sets — this client never reads or writes it directly. `credentials: "include"`
// is what lets the browser attach/receive that cookie on every call here.

export interface TokenPair {
  accessToken: string;
  expiresIn: number;
}

export interface MfaChallenge {
  mfaChallengeId: string;
  method: "SMS" | "EMAIL";
}

export type LoginResult = TokenPair | MfaChallenge;

export interface SessionSummary {
  id: string;
  channel: "WEB" | "MOBILE";
  deviceLabel: string | null;
  issuedAt: string;
  lastActiveAt: string;
}

function isMfaChallenge(result: LoginResult): result is MfaChallenge {
  return "mfaChallengeId" in result;
}

async function postJson<T>(path: string, body: unknown, accessToken?: string): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: "Request failed." }));
    throw new Error(error.message ?? "Request failed.");
  }
  if (response.status === 204 || response.status === 202) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const authClient = {
  isMfaChallenge,

  login(phoneNumber: string, password: string, deviceLabel?: string) {
    return postJson<LoginResult>("/api/v1/auth/login", { phoneNumber, password, deviceLabel });
  },

  verifyMfa(mfaChallengeId: string, code: string, deviceLabel?: string) {
    return postJson<TokenPair>("/api/v1/auth/mfa/verify", { mfaChallengeId, code, deviceLabel });
  },

  requestOtp(phoneNumber: string) {
    return postJson<void>("/api/v1/auth/otp/request", { phoneNumber });
  },

  verifyOtp(phoneNumber: string, code: string, channel: "WEB" | "MOBILE" = "WEB", deviceLabel?: string) {
    return postJson<TokenPair>("/api/v1/auth/otp/verify", { phoneNumber, code, channel, deviceLabel });
  },

  requestPasswordReset(identifier: string) {
    return postJson<void>("/api/v1/auth/password-reset/request", { identifier });
  },

  confirmPasswordReset(resetToken: string, newPassword: string) {
    return postJson<void>("/api/v1/auth/password-reset/confirm", { resetToken, newPassword });
  },

  refresh() {
    return postJson<TokenPair>("/api/v1/auth/refresh", {});
  },

  async logout(accessToken: string) {
    await fetch("/api/v1/auth/logout", {
      method: "POST",
      credentials: "include",
      headers: { Authorization: `Bearer ${accessToken}` },
    });
  },

  async listSessions(accessToken: string): Promise<SessionSummary[]> {
    const response = await fetch("/api/v1/auth/sessions", {
      credentials: "include",
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!response.ok) throw new Error("Unable to load sessions.");
    return (await response.json()) as SessionSummary[];
  },

  async revokeSession(accessToken: string, sessionId: string) {
    const response = await fetch(`/api/v1/auth/sessions/${sessionId}`, {
      method: "DELETE",
      credentials: "include",
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!response.ok) throw new Error("Unable to revoke session.");
  },
};
