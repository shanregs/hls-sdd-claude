import {
  getJson,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../features/common/masterDataApi";

/** One active session as the Sessions tables show it. */
export interface SessionRow {
  id: string;
  deviceDescription: string | null;
  signedInAt: string;
  lastActivityAt: string;
  current: boolean;
  clientType: "WEB" | "ANDROID";
  appVersion: string | null;
  /** Only in System's all-users list. */
  userId?: string;
  userName?: string | null;
  userPhone?: string | null;
}

export interface SessionPage {
  content: SessionRow[];
  page: number;
  size: number;
  totalElements: number;
}

export interface EndResult {
  ended: number;
  /** True when the caller's own session was among those ended: the web app must sign them out. */
  includesCurrent?: boolean;
}

const ME = "/api/v1/me/sessions";
const ADMIN = "/api/v1/admin/sessions";

export function listMySessions(
  authFetch: AuthFetch,
): Promise<ApiResult<SessionRow[]>> {
  return getJson(authFetch, ME, "Could not load your sessions.");
}

/** Ends one of the caller's own sessions; ending the current one means signing out afterwards. */
export function endMySession(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<void>> {
  return sendJson(authFetch, "DELETE", `${ME}/${id}`, undefined, false);
}

/** Ends every session of the caller, the current one included. */
export function endAllMySessions(
  authFetch: AuthFetch,
): Promise<ApiResult<EndResult>> {
  return sendJson(authFetch, "DELETE", ME);
}

export function listAllSessions(
  authFetch: AuthFetch,
  params: { userId: string; page: number; size: number },
): Promise<ApiResult<SessionPage>> {
  const search = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
  });
  if (params.userId) search.set("userId", params.userId);
  return getJson(authFetch, `${ADMIN}?${search}`, "Could not load sessions.");
}

export function endSessionAsSystem(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<EndResult>> {
  return sendJson(authFetch, "DELETE", `${ADMIN}/${id}`);
}

/** Ends every session of one user, or of everyone when {@code userId} is empty. */
export function endSessionsAsSystem(
  authFetch: AuthFetch,
  userId: string,
): Promise<ApiResult<EndResult>> {
  const query = userId ? `?userId=${encodeURIComponent(userId)}` : "";
  return sendJson(authFetch, "DELETE", `${ADMIN}${query}`);
}
