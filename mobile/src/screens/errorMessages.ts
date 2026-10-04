import {
  ApiError,
  LockedError,
  NoConnectionError,
  RateLimitedError,
  UnauthorizedError,
  WebOnlyRoleError,
} from "../api/errors";

/**
 * The text a user sees for a failed sign-in call. Wrong-credential and unknown-account failures use
 * the server's identical message, so the app never reveals which part was wrong (spec 001 FR-008).
 */
export function signInErrorMessage(error: unknown): string {
  if (error instanceof NoConnectionError) {
    return "No connection. Check your internet and try again.";
  }
  if (
    error instanceof UnauthorizedError ||
    error instanceof LockedError ||
    error instanceof WebOnlyRoleError ||
    error instanceof RateLimitedError
  ) {
    return error.message;
  }
  if (error instanceof ApiError && error.status === 400) {
    return error.message;
  }
  if (error instanceof ApiError && error.status === 503) {
    return "We couldn't send the code right now. Please try again shortly.";
  }
  return "Something went wrong. Please try again.";
}
