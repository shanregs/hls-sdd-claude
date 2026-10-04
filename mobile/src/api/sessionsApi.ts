import { api } from "./httpClient";

/** One signed-in device or browser of the caller (spec 001 FR-015, extended by spec 018 FR-007). */
export interface SessionInfo {
  id: string;
  deviceDescription: string | null;
  signedInAt: string;
  lastActivityAt: string;
  current: boolean;
  clientType: "WEB" | "ANDROID";
  appVersion: string | null;
}

export const listSessions = () => api.get<SessionInfo[]>("/api/v1/me/sessions");

export const endSession = (sessionId: string) =>
  api.delete<void>(`/api/v1/me/sessions/${encodeURIComponent(sessionId)}`);
