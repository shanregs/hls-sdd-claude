import * as Application from "expo-application";
import { Platform } from "react-native";
import { API_BASE_URL, REQUEST_TIMEOUT_MS } from "../config/constants";
import { getAccessToken } from "../security/memoryToken";
import {
  ApiError,
  ForbiddenError,
  LockedError,
  NoConnectionError,
  RateLimitedError,
  UnauthorizedError,
  UpdateRequiredError,
  WebOnlyRoleError,
} from "./errors";

export type HttpMethod = "GET" | "POST" | "PUT" | "DELETE";

export interface CallInfo {
  method: HttpMethod;
  path: string;
}

/**
 * Adds extra request headers for one call: used by the location gate (US4) and the rooted-device
 * flag (US1). A provider that throws is ignored, so it can never make a call fail (spec FR-021, FR-025).
 */
export type ClientHeadersProvider = (call: CallInfo) => Promise<Record<string, string>>;

const providers: ClientHeadersProvider[] = [];

export function registerClientHeadersProvider(provider: ClientHeadersProvider): () => void {
  providers.push(provider);
  return () => {
    const index = providers.indexOf(provider);
    if (index >= 0) providers.splice(index, 1);
  };
}

export interface AuthHooks {
  /** Tries to renew the access token once (single-flight). Resolves true when a new token is set. */
  renewAccess: () => Promise<boolean>;
}

let authHooks: AuthHooks | null = null;

export function setAuthHooks(hooks: AuthHooks | null): void {
  authHooks = hooks;
}

export interface RequestOptions {
  body?: unknown;
  /** Send the access token (default true). Sign-in, renewal and public calls pass false. */
  auth?: boolean;
  headers?: Record<string, string>;
}

/** "1.0" or "1.0.0-beta" become the dotted three-part form the server expects. */
function appVersion(): string {
  const raw = Application.nativeApplicationVersion ?? "0.0.0";
  const parts = raw.split(/[.-]/).slice(0, 3);
  while (parts.length < 3) parts.push("0");
  return parts.map((p) => (/^\d+$/.test(p) ? p : "0")).join(".");
}

function baseHeaders(): Record<string, string> {
  const version = appVersion();
  const constants = Platform.constants as { Release?: string; Model?: string } | undefined;
  return {
    Accept: "application/json",
    "X-HLS-Client": `android/${version}`,
    "User-Agent": `HLS-Android/${version} (Android ${constants?.Release ?? "?"}; ${constants?.Model ?? "device"})`,
  };
}

async function providerHeaders(call: CallInfo): Promise<Record<string, string>> {
  const merged: Record<string, string> = {};
  for (const provider of providers) {
    try {
      Object.assign(merged, await provider(call));
    } catch {
      // A failing provider (for example location) must never fail the call.
    }
  }
  return merged;
}

interface ParsedResponse {
  status: number;
  data: unknown;
  retryAfter: number | undefined;
}

async function send(method: HttpMethod, path: string, options: RequestOptions): Promise<ParsedResponse> {
  const headers: Record<string, string> = {
    ...baseHeaders(),
    ...(await providerHeaders({ method, path })),
    ...options.headers,
  };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (options.auth !== false) {
    const token = getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: controller.signal,
    });
  } catch {
    throw new NoConnectionError();
  } finally {
    clearTimeout(timer);
  }

  const text = await response.text();
  let data: unknown;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    data = undefined;
  }
  const retryAfterHeader = Number(response.headers.get("Retry-After"));
  return {
    status: response.status,
    data,
    retryAfter: Number.isFinite(retryAfterHeader) && retryAfterHeader > 0 ? retryAfterHeader : undefined,
  };
}

function messageOf(data: unknown, fallback: string): string {
  if (data && typeof data === "object" && "message" in data && typeof data.message === "string") {
    return data.message;
  }
  return fallback;
}

function codeOf(data: unknown): string | undefined {
  return data && typeof data === "object" && "code" in data && typeof data.code === "string"
    ? data.code
    : undefined;
}

function toError(response: ParsedResponse): ApiError {
  const { status, data } = response;
  switch (status) {
    case 401:
      return new UnauthorizedError(messageOf(data, "Please sign in again."));
    case 403:
      return codeOf(data) === "WEB_ONLY_ROLE"
        ? new WebOnlyRoleError(messageOf(data, "Your account uses the HLS web application."))
        : new ForbiddenError(messageOf(data, "You are not allowed to do this."));
    case 423: {
      const unlockAt =
        data && typeof data === "object" && "unlockAt" in data && typeof data.unlockAt === "string"
          ? data.unlockAt
          : undefined;
      return new LockedError(messageOf(data, "Account locked."), unlockAt);
    }
    case 426: {
      const minimum =
        data && typeof data === "object" && "minimumVersion" in data && typeof data.minimumVersion === "string"
          ? data.minimumVersion
          : undefined;
      return new UpdateRequiredError(minimum);
    }
    case 429:
      return new RateLimitedError(messageOf(data, "Too many requests, try again shortly."), response.retryAfter);
    default:
      return new ApiError(messageOf(data, `Request failed (${status}).`), status);
  }
}

/**
 * One call to the HLS API (spec 018 T018). Adds the client headers (and any registered provider
 * headers), the bearer token when asked, retries once after a successful renewal on 401, and turns
 * every failure into a typed error.
 */
export async function apiRequest<T>(method: HttpMethod, path: string, options: RequestOptions = {}): Promise<T> {
  let response = await send(method, path, options);

  if (response.status === 401 && options.auth !== false && authHooks && getAccessToken()) {
    const renewed = await authHooks.renewAccess();
    if (renewed) {
      response = await send(method, path, options);
    }
  }

  if (response.status >= 200 && response.status < 300) {
    return response.data as T;
  }
  throw toError(response);
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => apiRequest<T>("GET", path, options),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>("POST", path, { ...options, body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) => apiRequest<T>("PUT", path, { ...options, body }),
  delete: <T>(path: string, options?: RequestOptions) => apiRequest<T>("DELETE", path, options),
};
