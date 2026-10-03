export interface SignedInUser {
  id: string;
  displayName: string;
  roles: string[];
}

export interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  user: SignedInUser;
}

interface ErrorBody {
  message?: string;
}

const GENERIC_ERROR = "Something went wrong. Please try again.";

async function parseErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as ErrorBody;
    return body.message ?? GENERIC_ERROR;
  } catch {
    return GENERIC_ERROR;
  }
}

/** Thrown by {@link loginWithPassword} on a 423 (account locked, FR-012) so the sign-in page can
 * show the unlock time distinctly from a generic wrong-credentials message. */
export class AccountLockedError extends Error {
  unlockAt: string;

  constructor(message: string, unlockAt: string) {
    super(message);
    this.unlockAt = unlockAt;
  }
}

/** Password sign-in, any role, identified by phone number or username (contracts/auth-api.md).
 * The renewal cookie is set by the browser automatically from the response; this client never
 * reads or stores it. Throws {@link AccountLockedError} on a 423. */
export async function loginWithPassword(
  identifier: string,
  password: string,
): Promise<AuthResponse> {
  const response = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ identifier, password }),
  });
  if (response.status === 423) {
    const body = (await response.json()) as {
      message: string;
      unlockAt: string;
    };
    throw new AccountLockedError(body.message, body.unlockAt);
  }
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }
  return (await response.json()) as AuthResponse;
}

export async function logout(accessToken: string): Promise<void> {
  await fetch("/api/v1/auth/logout", {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}` },
    credentials: "include",
  });
}

/** Silent session renewal (User Story 3): reads the `HttpOnly` renewal cookie the browser already
 * holds (no body needed) and, on success, returns a fresh access token/user, exactly like a
 * sign-in response. Resolves to `null` (never throws) when renewal is refused, so callers can fall
 * back to showing the sign-in page without an unhandled rejection. */
export async function renewSession(): Promise<AuthResponse | null> {
  const response = await fetch("/api/v1/auth/renew", {
    method: "POST",
    credentials: "include",
  });
  if (!response.ok) {
    return null;
  }
  return (await response.json()) as AuthResponse;
}

export type OtpChannel = "SMS" | "EMAIL" | "BOTH";
export type OtpPurpose = "SIGN_IN" | "PASSWORD_RESET";

/** Thrown by {@link requestOtp} on 429/503; carries the server's `Retry-After` (seconds) when
 * present, so the UI can show an accurate countdown instead of guessing (FR-028/FR-029). */
export class OtpRequestError extends Error {
  retryAfterSeconds: number | null;

  constructor(message: string, retryAfterSeconds: number | null) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

/**
 * Always resolves for an eligible/ineligible destination alike — the response is neutral
 * regardless of eligibility (FR-007) — but throws {@link OtpRequestError} for the resend cooldown,
 * the too-many-consecutive-requests lockout, the plain per-minute rate limit, or a gateway
 * delivery failure (FR-028/FR-029, contracts/auth-api.md).
 * @param destination for SIGN_IN, the phone number to text directly; for PASSWORD_RESET, a
 *   phone-or-username identifier — the server resolves the actual phone/email destination, so the
 *   caller never needs to already know it (contracts/auth-api.md).
 */
export async function requestOtp(
  destination: string,
  channel: OtpChannel,
  purpose: OtpPurpose,
): Promise<void> {
  const response = await fetch("/api/v1/auth/otp/request", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ destination, channel, purpose }),
  });
  if (!response.ok) {
    const retryAfterHeader = response.headers.get("Retry-After");
    const retryAfterSeconds = retryAfterHeader
      ? Number.parseInt(retryAfterHeader, 10)
      : null;
    throw new OtpRequestError(
      await parseErrorMessage(response),
      retryAfterSeconds,
    );
  }
}

export async function verifyOtpForSignIn(
  destination: string,
  code: string,
): Promise<AuthResponse> {
  const response = await fetch("/api/v1/auth/otp/verify", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ destination, code, purpose: "SIGN_IN" }),
  });
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }
  return (await response.json()) as AuthResponse;
}

/** @param identifier the same phone-or-username identifier passed to {@link requestOtp}. */
export async function verifyOtpForPasswordReset(
  identifier: string,
  code: string,
  channel: OtpChannel,
): Promise<string> {
  const response = await fetch("/api/v1/auth/otp/verify", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({
      destination: identifier,
      code,
      purpose: "PASSWORD_RESET",
      channel,
    }),
  });
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }
  const body = (await response.json()) as { resetToken: string };
  return body.resetToken;
}

export async function getResetChannels(
  identifier: string,
): Promise<OtpChannel[]> {
  const response = await fetch(
    `/api/v1/auth/password-reset/channels?identifier=${encodeURIComponent(identifier)}`,
  );
  if (!response.ok) {
    return ["SMS"];
  }
  return (await response.json()) as OtpChannel[];
}

export async function completePasswordReset(
  resetToken: string,
  newPassword: string,
): Promise<void> {
  const response = await fetch("/api/v1/auth/password-reset/complete", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ resetToken, newPassword }),
  });
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }
}

export interface SessionSummary {
  id: string;
  deviceDescription: string;
  signedInAt: string;
  lastActivityAt: string;
  current: boolean;
}

/** Lists only the caller's own active sessions (FR-015, contracts/auth-api.md). */
export async function listSessions(
  accessToken: string,
): Promise<SessionSummary[]> {
  const response = await fetch("/api/v1/me/sessions", {
    headers: { Authorization: `Bearer ${accessToken}` },
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }
  return (await response.json()) as SessionSummary[];
}

/** Ends one of the caller's own sessions (FR-015). */
export async function endSession(
  accessToken: string,
  sessionId: string,
): Promise<void> {
  const response = await fetch(`/api/v1/me/sessions/${sessionId}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${accessToken}` },
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }
}
