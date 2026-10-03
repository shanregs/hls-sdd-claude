/** `authFetch`-based client for contracts/user-management-api.md (mirrors features/permissions). */
export type Role = "ADMIN" | "DIRECTOR" | "MANAGER" | "TEACHER" | "SYSTEM";

export const ALL_ROLES: Role[] = [
  "ADMIN",
  "DIRECTOR",
  "MANAGER",
  "TEACHER",
  "SYSTEM",
];

export interface UserSummary {
  id: string;
  displayName: string;
  phone: string;
  username: string | null;
  email: string | null;
  roles: Role[];
  active: boolean;
}

export interface UserPage {
  content: UserSummary[];
  page: number;
  size: number;
  totalElements: number;
}

export type AuthFetch = (
  input: RequestInfo,
  init?: RequestInit,
) => Promise<Response>;

export type ApiResult<T = void> =
  { ok: true; data: T } | { ok: false; reason: string };

export interface ListParams {
  query: string;
  role: Role | "";
  active: "" | "true" | "false";
  page: number;
  size: number;
}

export interface CreateUserInput {
  displayName: string;
  phone: string;
  roles: Role[];
  username: string;
  email: string;
  initialPassword: string;
}

const GENERIC_FAILURE = "Something went wrong. Please try again.";

async function failure(
  response: Response,
): Promise<{ ok: false; reason: string }> {
  try {
    const body = (await response.json()) as { reason?: string };
    return { ok: false, reason: body.reason ?? GENERIC_FAILURE };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

const JSON_HEADERS = { "Content-Type": "application/json" };

export async function listUsers(
  authFetch: AuthFetch,
  params: ListParams,
): Promise<ApiResult<UserPage>> {
  const search = new URLSearchParams();
  if (params.query.trim()) search.set("query", params.query.trim());
  if (params.role) search.set("role", params.role);
  if (params.active) search.set("active", params.active);
  search.set("page", String(params.page));
  search.set("size", String(params.size));
  try {
    const response = await authFetch(`/api/v1/identity/users?${search}`);
    if (!response.ok) {
      return { ok: false, reason: "Could not load users." };
    }
    const data = (await response.json()) as UserPage | null;
    if (!data || !Array.isArray(data.content)) {
      return { ok: false, reason: "Could not load users." };
    }
    return { ok: true, data };
  } catch {
    return { ok: false, reason: "Could not load users." };
  }
}

export async function createUser(
  authFetch: AuthFetch,
  input: CreateUserInput,
): Promise<ApiResult<UserSummary>> {
  try {
    const response = await authFetch("/api/v1/identity/users", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify({
        displayName: input.displayName,
        phone: input.phone,
        roles: input.roles,
        username: input.username || null,
        email: input.email || null,
        initialPassword: input.initialPassword || null,
      }),
    });
    if (!response.ok) return failure(response);
    return { ok: true, data: (await response.json()) as UserSummary };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

export async function updateRoles(
  authFetch: AuthFetch,
  userId: string,
  roles: Role[],
): Promise<ApiResult<UserSummary>> {
  try {
    const response = await authFetch(`/api/v1/identity/users/${userId}/roles`, {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify({ roles }),
    });
    if (!response.ok) return failure(response);
    return { ok: true, data: (await response.json()) as UserSummary };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

export async function setActive(
  authFetch: AuthFetch,
  userId: string,
  active: boolean,
): Promise<ApiResult> {
  try {
    const response = await authFetch(
      `/api/v1/identity/users/${userId}/${active ? "reactivate" : "deactivate"}`,
      { method: "POST" },
    );
    if (!response.ok) return failure(response);
    return { ok: true, data: undefined };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

export async function resetPassword(
  authFetch: AuthFetch,
  userId: string,
  newPassword: string,
): Promise<ApiResult> {
  try {
    const response = await authFetch(
      `/api/v1/identity/users/${userId}/reset-password`,
      {
        method: "POST",
        headers: JSON_HEADERS,
        body: JSON.stringify({ newPassword }),
      },
    );
    if (!response.ok) return failure(response);
    return { ok: true, data: undefined };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}
