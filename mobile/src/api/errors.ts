/** Typed outcomes of a call to the HLS API (spec 018 T018). Screens branch on these, not on codes. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = new.target.name;
  }
}

/** The server could not be reached (offline, DNS, timeout). */
export class NoConnectionError extends ApiError {
  constructor() {
    super("No connection");
  }
}

/** 426: this app version is older than the server's minimum. */
export class UpdateRequiredError extends ApiError {
  constructor(readonly minimumVersion?: string) {
    super("Please update the HLS app to continue.", 426);
  }
}

/** 401: not signed in, or the session ended and could not be renewed. */
export class UnauthorizedError extends ApiError {
  constructor(message = "Please sign in again.") {
    super(message, 401);
  }
}

/** 403 WEB_ONLY_ROLE: valid credentials, but the account's roles use the web application. */
export class WebOnlyRoleError extends ApiError {
  constructor(message = "Your account uses the HLS web application.") {
    super(message, 403);
  }
}

/** Any other 403. */
export class ForbiddenError extends ApiError {
  constructor(message = "You are not allowed to do this.") {
    super(message, 403);
  }
}

/** 423: the account is temporarily locked after repeated failed sign-ins. */
export class LockedError extends ApiError {
  constructor(
    message: string,
    readonly unlockAt?: string,
  ) {
    super(message, 423);
  }
}

/** 429: too many requests; retry after the given number of seconds when the server said so. */
export class RateLimitedError extends ApiError {
  constructor(
    message: string,
    readonly retryAfterSeconds?: number,
  ) {
    super(message, 429);
  }
}
