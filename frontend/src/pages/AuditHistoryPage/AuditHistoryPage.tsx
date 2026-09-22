import { useState, type FormEvent } from "react";
import { useAuth } from "../../auth/AuthContext";
import { auditClient, type AuditEntryView } from "./auditClient";
import "./AuditHistoryPage.css";

/**
 * User Story 2: Director/Admin looks up a record's full audit history by its
 * entity type and entity id. Director/Admin-only — the backend enforces this
 * (FR-008); this page just surfaces the 403 as a message (research.md §7).
 */
export function AuditHistoryPage() {
  const { accessToken } = useAuth();

  const [entityType, setEntityType] = useState("");
  const [entityId, setEntityId] = useState("");
  const [entries, setEntries] = useState<AuditEntryView[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleLookup(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setError(null);
    setEntries(null);
    try {
      const history = await auditClient.getHistory(
        accessToken,
        entityType,
        entityId,
      );
      setEntries(history);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to load history.");
    }
  }

  return (
    <div className="audit-history-page" data-testid="audit-history-page">
      <h1>Audit History</h1>

      <form data-testid="lookup-form" onSubmit={handleLookup}>
        <label>
          Entity Type
          <input
            data-testid="entity-type-input"
            value={entityType}
            onChange={(e) => setEntityType(e.target.value)}
            placeholder="e.g. AttendanceRecord"
          />
        </label>
        <label>
          Entity ID
          <input
            data-testid="entity-id-input"
            value={entityId}
            onChange={(e) => setEntityId(e.target.value)}
          />
        </label>
        <button type="submit">Look up history</button>
        {error && <p data-testid="lookup-error">{error}</p>}
      </form>

      {entries && (
        <table data-testid="history-table">
          <thead>
            <tr>
              <th>When</th>
              <th>Action</th>
              <th>Summary</th>
              <th>Before</th>
              <th>After</th>
              <th>Actor</th>
            </tr>
          </thead>
          <tbody>
            {entries.length === 0 && (
              <tr>
                <td colSpan={6} data-testid="history-empty">
                  No history recorded for this record.
                </td>
              </tr>
            )}
            {entries.map((entry) => (
              <tr key={entry.id} data-testid="history-row">
                <td>{new Date(entry.occurredAt).toLocaleString()}</td>
                <td>{entry.action}</td>
                <td>{entry.summary}</td>
                <td>{entry.beforeValue ?? "—"}</td>
                <td>{entry.afterValue ?? "—"}</td>
                <td>
                  {entry.actorUserId}
                  {entry.actorRole ? ` (${entry.actorRole})` : ""}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
