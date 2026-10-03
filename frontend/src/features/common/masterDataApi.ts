/** Small `authFetch` helpers shared by the master-data feature folders (spec 005). */
export type AuthFetch = (
  input: RequestInfo,
  init?: RequestInit,
) => Promise<Response>;

export type ApiResult<T = void> =
  { ok: true; data: T } | { ok: false; reason: string; status?: number };

export interface PageOf<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export const GENERIC_FAILURE = "Something went wrong. Please try again.";

async function failureOf(
  response: Response,
): Promise<{ ok: false; reason: string; status: number }> {
  try {
    const body = (await response.json()) as { reason?: string };
    return {
      ok: false,
      reason: body.reason ?? GENERIC_FAILURE,
      status: response.status,
    };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE, status: response.status };
  }
}

export async function getJson<T>(
  authFetch: AuthFetch,
  url: string,
  loadFailure = "Could not load this list.",
): Promise<ApiResult<T>> {
  try {
    const response = await authFetch(url);
    if (!response.ok) {
      const failure = await failureOf(response);
      return {
        ...failure,
        reason:
          failure.reason === GENERIC_FAILURE ? loadFailure : failure.reason,
      };
    }
    const data = (await response.json()) as T | null;
    if (data === null || data === undefined) {
      return { ok: false, reason: loadFailure };
    }
    return { ok: true, data };
  } catch {
    return { ok: false, reason: loadFailure };
  }
}

export async function sendJson<T = void>(
  authFetch: AuthFetch,
  method: "POST" | "PUT" | "DELETE",
  url: string,
  body?: unknown,
  expectBody = true,
): Promise<ApiResult<T>> {
  try {
    const response = await authFetch(url, {
      method,
      headers:
        body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) {
      return failureOf(response);
    }
    if (!expectBody) {
      return { ok: true, data: undefined as T };
    }
    return { ok: true, data: (await response.json()) as T };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

export function queryString(
  params: Record<string, string | number | boolean | undefined | null>,
): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  return search.toString();
}
