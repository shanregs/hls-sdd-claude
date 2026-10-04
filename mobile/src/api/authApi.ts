import { api } from "./httpClient";

/** The signed-in identity returned by every sign-in path (spec 001 contracts/auth-api.md). */
export interface SignedInUser {
  id: string;
  displayName: string;
  roles: string[];
}

/**
 * The 200 body of sign-in and renewal for the Android client: the renewal credential travels in the
 * body, not a cookie (spec 018 research.md §3).
 */
export interface AuthResult {
  accessToken: string;
  expiresInSeconds: number;
  renewalCredential: string;
  user: SignedInUser;
}

export type ResetChannel = "SMS" | "EMAIL" | "BOTH";

export const loginWithPassword = (identifier: string, password: string) =>
  api.post<AuthResult>("/api/v1/auth/login", { identifier, password }, { auth: false });

/** Asks for a sign-in code by SMS to the phone number. The server always answers neutrally. */
export const requestSignInCode = (phone: string) =>
  api.post<{ message: string }>(
    "/api/v1/auth/otp/request",
    { destination: phone, channel: "SMS", purpose: "SIGN_IN" },
    { auth: false },
  );

export const verifySignInCode = (phone: string, code: string) =>
  api.post<AuthResult>(
    "/api/v1/auth/otp/verify",
    { destination: phone, code, purpose: "SIGN_IN" },
    { auth: false },
  );

export const renewSession = (renewalCredential: string) =>
  api.post<AuthResult>("/api/v1/auth/renew", { renewalCredential }, { auth: false });

/**
 * Ends the session on the server. The token is passed explicitly because the caller clears the
 * in-memory token before this request is sent (spec FR-006: the device is wiped first).
 */
export const logoutOnServer = (accessToken: string) =>
  api.post<void>("/api/v1/auth/logout", undefined, {
    auth: false,
    headers: { Authorization: `Bearer ${accessToken}` },
  });

/** Password reset (spec 001 FR-016): channels, request a code, verify it, then set a new password. */
export const fetchResetChannels = (identifier: string) =>
  api.get<ResetChannel[] | string[]>(
    `/api/v1/auth/password-reset/channels?identifier=${encodeURIComponent(identifier)}`,
    { auth: false },
  );

export const requestResetCode = (identifier: string, channel: ResetChannel) =>
  api.post<{ message: string }>(
    "/api/v1/auth/otp/request",
    { destination: identifier, channel, purpose: "PASSWORD_RESET" },
    { auth: false },
  );

export const verifyResetCode = (identifier: string, code: string, channel: ResetChannel) =>
  api.post<{ resetToken: string }>(
    "/api/v1/auth/otp/verify",
    { destination: identifier, code, purpose: "PASSWORD_RESET", channel },
    { auth: false },
  );

export const completePasswordReset = (resetToken: string, newPassword: string) =>
  api.post<void>("/api/v1/auth/password-reset/complete", { resetToken, newPassword }, { auth: false });
