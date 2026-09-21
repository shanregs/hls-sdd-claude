import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../../auth/AuthContext";
import { organizationClient, type PortfolioItem, type UnassignedItem } from "./organizationClient";
import "./AssignmentsPage.css";

type ItemKind = "SCHOOL" | "TEACHER";

/**
 * User Stories 1-3: assign/reassign a Manager to a School or Teacher, view a
 * Manager's current portfolio, and see the unassigned-items list. Director/
 * Admin-only — the backend enforces this; this page assumes the caller is
 * already authenticated via Identity's login (research.md §8).
 */
export function AssignmentsPage() {
  const { accessToken } = useAuth();

  const [itemKind, setItemKind] = useState<ItemKind>("SCHOOL");
  const [itemId, setItemId] = useState("");
  const [managerId, setManagerId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [portfolioManagerId, setPortfolioManagerId] = useState("");
  const [portfolio, setPortfolio] = useState<PortfolioItem[]>([]);
  const [unassigned, setUnassigned] = useState<UnassignedItem[]>([]);

  const loadUnassigned = useCallback(() => {
    if (!accessToken) return;
    organizationClient
      .listUnassigned(accessToken)
      .then(setUnassigned)
      .catch(() => setError("Unable to load unassigned items."));
  }, [accessToken]);

  useEffect(() => {
    loadUnassigned();
  }, [loadUnassigned]);

  async function handleAssign(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setError(null);
    setMessage(null);
    try {
      // FR-004/FR-011: a reassignment must name the exact current-assignment row
      // it intends to end, so it can be rejected as a conflict if someone else
      // already ended it first. The accountable-manager query only returns
      // (state, managerId) — no row id — so the current row's id has to come
      // from the history endpoint's still-open (effectiveTo === null) entry.
      const history =
        itemKind === "SCHOOL"
          ? await organizationClient.getSchoolAssignmentHistory(accessToken, itemId)
          : await organizationClient.getTeacherAssignmentHistory(accessToken, itemId);
      const currentEntry = history.find((entry) => entry.effectiveTo === null);
      const endsAssignmentId = currentEntry?.id;

      const result =
        itemKind === "SCHOOL"
          ? await organizationClient.assignSchoolManager(accessToken, itemId, managerId, endsAssignmentId)
          : await organizationClient.assignTeacherManager(accessToken, itemId, managerId, endsAssignmentId);
      setMessage(`Assigned. Current manager: ${result.managerId}`);
      loadUnassigned();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Assignment failed.");
    }
  }

  async function handleLoadPortfolio(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setError(null);
    try {
      const items = await organizationClient.getManagerPortfolio(accessToken, portfolioManagerId);
      setPortfolio(items);
    } catch {
      setError("Unable to load portfolio.");
    }
  }

  return (
    <div className="assignments-page" data-testid="assignments-page">
      <h1>Manager Assignments</h1>

      <form data-testid="assign-form" onSubmit={handleAssign}>
        <h2>Assign / Reassign</h2>
        <label>
          Type
          <select
            data-testid="item-kind-select"
            value={itemKind}
            onChange={(e) => setItemKind(e.target.value as ItemKind)}
          >
            <option value="SCHOOL">School</option>
            <option value="TEACHER">Teacher</option>
          </select>
        </label>
        <label>
          {itemKind === "SCHOOL" ? "School ID" : "Teacher ID"}
          <input data-testid="item-id-input" value={itemId} onChange={(e) => setItemId(e.target.value)} />
        </label>
        <label>
          Manager ID
          <input data-testid="manager-id-input" value={managerId} onChange={(e) => setManagerId(e.target.value)} />
        </label>
        <button type="submit">Assign</button>
        {message && <p data-testid="assign-message">{message}</p>}
        {error && <p data-testid="assign-error">{error}</p>}
      </form>

      <form data-testid="portfolio-form" onSubmit={handleLoadPortfolio}>
        <h2>Manager Portfolio</h2>
        <label>
          Manager ID
          <input
            data-testid="portfolio-manager-id-input"
            value={portfolioManagerId}
            onChange={(e) => setPortfolioManagerId(e.target.value)}
          />
        </label>
        <button type="submit">Load Portfolio</button>
        <ul>
          {portfolio.map((item) => (
            <li key={`${item.itemType}-${item.itemId}`} data-testid="portfolio-row">
              {item.itemType}: {item.itemId} (since {new Date(item.since).toLocaleDateString()})
            </li>
          ))}
        </ul>
      </form>

      <section data-testid="unassigned-section">
        <h2>Unassigned</h2>
        <ul>
          {unassigned.map((item) => (
            <li key={`${item.itemType}-${item.itemId}`} data-testid="unassigned-row">
              {item.itemType}: {item.itemId}
              {item.lastEndedAt ? ` (ended ${new Date(item.lastEndedAt).toLocaleDateString()})` : " (never assigned)"}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
