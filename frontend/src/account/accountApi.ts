import {
  getJson,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../features/common/masterDataApi";

export interface AccountProfile {
  id: string;
  displayName: string;
  phone: string;
  username: string | null;
  email: string | null;
  roles: string[];
}

export function getProfile(
  authFetch: AuthFetch,
): Promise<ApiResult<AccountProfile>> {
  return getJson(
    authFetch,
    "/api/v1/me/profile",
    "Could not load your profile.",
  );
}

export function updateProfile(
  authFetch: AuthFetch,
  body: { displayName: string; username: string; email: string },
): Promise<ApiResult<AccountProfile>> {
  return sendJson(authFetch, "PUT", "/api/v1/me/profile", body);
}

export function changePassword(
  authFetch: AuthFetch,
  body: { currentPassword: string; newPassword: string },
): Promise<ApiResult<void>> {
  return sendJson(authFetch, "POST", "/api/v1/me/password", body, false);
}
