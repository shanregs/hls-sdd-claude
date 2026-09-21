import { useCallback, useEffect, useState } from "react";
import { authClient, type SessionSummary } from "../../auth/authClient";
import { useAuth } from "../../auth/AuthContext";
import "./SessionsPage.css";

/** User Story 5: view and revoke active sessions/devices. */
export function SessionsPage() {
  const { accessToken } = useAuth();
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!accessToken) return;
    authClient
      .listSessions(accessToken)
      .then(setSessions)
      .catch(() => setError("Unable to load sessions."));
  }, [accessToken]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleRevoke(sessionId: string) {
    if (!accessToken) return;
    try {
      await authClient.revokeSession(accessToken, sessionId);
      load();
    } catch {
      setError("Unable to revoke that session.");
    }
  }

  if (error) {
    return <p data-testid="sessions-error">{error}</p>;
  }

  return (
    <div className="sessions-page" data-testid="sessions-page">
      <h1>Active sessions</h1>
      <ul>
        {sessions.map((session) => (
          <li key={session.id} data-testid="session-row">
            <span>
              {session.channel} — {session.deviceLabel ?? "Unknown device"} — last active{" "}
              {new Date(session.lastActiveAt).toLocaleString()}
            </span>
            <button onClick={() => handleRevoke(session.id)}>Revoke</button>
          </li>
        ))}
      </ul>
    </div>
  );
}
