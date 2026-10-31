import * as Application from "expo-application";
import { Platform } from "react-native";
import { API_BASE_URL, REQUEST_TIMEOUT_MS } from "../config/constants";
import { recordServerDate } from "../attendance/serverClock";
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

/** An upload waits this many times longer than a JSON call. */
const UPLOAD_TIMEOUT_FACTOR = 3;

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
  /** A multipart upload (one file part). The content type is left to the platform so the boundary is added. */
  form?: FormData;
  /** Read the answer as bytes (an image) instead of JSON. */
  binary?: boolean;
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
  headers: Headers;
}

async function send(method: HttpMethod, path: string, options: RequestOptions): Promise<ParsedResponse> {
  const headers: Record<string, string> = {
    ...baseHeaders(),
    ...(await providerHeaders({ method, path })),
    ...options.headers,
  };
  if (options.body !== undefined && !options.form) {
    headers["Content-Type"] = "application/json";
  }
  if (options.auth !== false) {
    const token = getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const controller = new AbortController();
  // An upload may be a few megabytes on a slow connection, so it gets a longer wait than a JSON call.
  const timer = setTimeout(() => controller.abort(), options.form ? REQUEST_TIMEOUT_MS * UPLOAD_TIMEOUT_FACTOR : REQUEST_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: options.form ?? (options.body !== undefined ? JSON.stringify(options.body) : undefined),
      signal: controller.signal,
    });
  } catch {
    throw new NoConnectionError();
  } finally {
    clearTimeout(timer);
  }

  // The server clock, used only to pick the default month on attendance screens.
  recordServerDate(response.headers.get("Date"));

  let data: unknown;
  if (options.binary && response.ok) {
    data = new Uint8Array(await response.arrayBuffer());
  } else {
    const text = await response.text();
    try {
      data = text ? JSON.parse(text) : undefined;
    } catch {
      data = undefined;
    }
  }
  const retryAfterHeader = Number(response.headers.get("Retry-After"));
  return {
    status: response.status,
    data,
    retryAfter: Number.isFinite(retryAfterHeader) && retryAfterHeader > 0 ? retryAfterHeader : undefined,
    headers: response.headers,
  };
}

function messageOf(data: unknown, fallback: string): string {
  if (data && typeof data === "object" && "message" in data && typeof data.message === "string") {
    return data.message;
  }
  // The attendance endpoints (spec 008) put the refusal text in `reason`.
  if (data && typeof data === "object" && "reason" in data && typeof data.reason === "string") {
    return data.reason;
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
    default: {
      const matches =
        status === 409 && data && typeof data === "object" && "matches" in data && Array.isArray(data.matches)
          ? (data.matches as string[])
          : [];
      return new ApiError(messageOf(data, `Request failed (${status}).`), status, matches, data);
    }
  }
}

/**
 * One call to the HLS API (spec 018 T018). Adds the client headers (and any registered provider
 * headers), the bearer token when asked, retries once after a successful renewal on 401, and turns
 * every failure into a typed error.
 */
async function execute(method: HttpMethod, path: string, options: RequestOptions): Promise<ParsedResponse> {
  let response = await send(method, path, options);

  if (response.status === 401 && options.auth !== false && authHooks && getAccessToken()) {
    const renewed = await authHooks.renewAccess();
    if (renewed) {
      response = await send(method, path, options);
    }
  }

  if (response.status >= 200 && response.status < 300) {
    return response;
  }
  throw toError(response);
}

export async function apiRequest<T>(method: HttpMethod, path: string, options: RequestOptions = {}): Promise<T> {
  return (await execute(method, path, options)).data as T;
}

/** What the server says about a file it sends (spec 033: a check-in photo). */
export interface BinaryAnswer {
  bytes: Uint8Array;
  contentType: string;
  /** From `Content-Length`; the number of bytes received when the header is missing. */
  sizeBytes: number;
  /** From `Content-Disposition`; null when the server sent none. */
  fileName: string | null;
}

/** The file name of a `Content-Disposition` header, plain (`filename="a.jpg"`) or encoded (`filename*=UTF-8''a.jpg`). */
export function fileNameFromDisposition(header: string | null): string | null {
  if (!header) return null;
  const encoded = /filename\*\s*=\s*(?:[\w-]+'[^']*')?([^;]+)/i.exec(header);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, ""));
    } catch {
      // fall through to the plain form
    }
  }
  const plain = /filename\s*=\s*("([^"]*)"|[^;]+)/i.exec(header);
  return plain ? (plain[2] ?? plain[1]).trim() : null;
}

/** One call that answers with a file. Same headers, token, renewal and errors as {@link apiRequest}. */
export async function fetchBinary(path: string, options: RequestOptions = {}): Promise<BinaryAnswer> {
  const response = await execute("GET", path, { ...options, binary: true });
  const bytes = response.data instanceof Uint8Array ? response.data : new Uint8Array(0);
  const length = Number(response.headers.get("Content-Length"));
  return {
    bytes,
    contentType: response.headers.get("Content-Type") ?? "application/octet-stream",
    sizeBytes: Number.isFinite(length) && length > 0 ? length : bytes.length,
    fileName: fileNameFromDisposition(response.headers.get("Content-Disposition")),
  };
}

/** A file picked on the phone, as the platform's form data wants it. */
export interface UploadFile {
  uri: string;
  name: string;
  type: string;
}

/** One call that sends a single file as multipart form data under {@code field}. */
export async function upload<T>(path: string, field: string, file: UploadFile, options: RequestOptions = {}): Promise<T> {
  const form = new FormData();
  form.append(field, file as unknown as Blob);
  return (await execute("POST", path, { ...options, form })).data as T;
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => apiRequest<T>("GET", path, options),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>("POST", path, { ...options, body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) => apiRequest<T>("PUT", path, { ...options, body }),
  delete: <T>(path: string, options?: RequestOptions) => apiRequest<T>("DELETE", path, options),
};
