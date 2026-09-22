import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../../auth/AuthContext";
import {
  organizationClient,
  type PortfolioItem,
  type UnassignedItem,
  type ZoneCoverage,
} from "./organizationClient";
import "./AssignmentsPage.css";

type ItemKind = "SCHOOL" | "TEACHER";

/**
 * User Stories 1-3: assign/reassign a Manager to a School or Teacher, view a
 * Manager's current portfolio, and see the unassigned-items list. Director/
 * Admin-only — the backend enforces this; this page assumes the caller is
 * already authenticated via Identity's login (research.md §8).
 *
 * Also covers specs/006-zone-scoping (reworked): assigning a Manager to
 * cover a Zone, and looking up a Zone's current coverage (Managers +
 * Schools, the latter read live from `school`). Assigning a School's Manager
 * above now surfaces a 422 when the chosen Manager doesn't cover that
 * School's Zone — the same error display already used for 409s.
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

  const [zoneManagerZoneId, setZoneManagerZoneId] = useState("");
  const [zoneManagerManagerId, setZoneManagerManagerId] = useState("");
  const [zoneManagerMessage, setZoneManagerMessage] = useState<string | null>(
    null,
  );
  const [zoneManagerError, setZoneManagerError] = useState<string | null>(null);

  const [coverageZoneId, setCoverageZoneId] = useState("");
  const [coverage, setCoverage] = useState<ZoneCoverage | null>(null);
  const [coverageError, setCoverageError] = useState<string | null>(null);

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
          ? await organizationClient.getSchoolAssignmentHistory(
              accessToken,
              itemId,
            )
          : await organizationClient.getTeacherAssignmentHistory(
              accessToken,
              itemId,
            );
      const currentEntry = history.find((entry) => entry.effectiveTo === null);
      const endsAssignmentId = currentEntry?.id;

      const result =
        itemKind === "SCHOOL"
          ? await organizationClient.assignSchoolManager(
              accessToken,
              itemId,
              managerId,
              endsAssignmentId,
            )
          : await organizationClient.assignTeacherManager(
              accessToken,
              itemId,
              managerId,
              endsAssignmentId,
            );
      setMessage(`Assigned. Current manager: ${result.managerId}`);
      loadUnassigned();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Assignment failed.");
    }
  }

  async function handleAssignManagerToZone(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setZoneManagerError(null);
    setZoneManagerMessage(null);
    try {
      await organizationClient.assignManagerToZone(
        accessToken,
        zoneManagerZoneId,
        zoneManagerManagerId,
      );
      setZoneManagerMessage(
        `Manager ${zoneManagerManagerId} now covers zone ${zoneManagerZoneId}.`,
      );
    } catch (e) {
      setZoneManagerError(
        e instanceof Error ? e.message : "Unable to assign Manager to Zone.",
      );
    }
  }

  async function handleLookupCoverage(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setCoverageError(null);
    try {
      const result = await organizationClient.getZoneCoverage(
        accessToken,
        coverageZoneId,
      );
      setCoverage(result);
    } catch (e) {
      setCoverage(null);
      setCoverageError(
        e instanceof Error ? e.message : "Unable to load Zone coverage.",
      );
    }
  }

  async function handleLoadPortfolio(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setError(null);
    try {
      const items = await organizationClient.getManagerPortfolio(
        accessToken,
        portfolioManagerId,
      );
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
          <input
            data-testid="item-id-input"
            value={itemId}
            onChange={(e) => setItemId(e.target.value)}
          />
        </label>
        <label>
          Manager ID
          <input
            data-testid="manager-id-input"
            value={managerId}
            onChange={(e) => setManagerId(e.target.value)}
          />
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
            <li
              key={`${item.itemType}-${item.itemId}`}
              data-testid="portfolio-row"
            >
              {item.itemType}: {item.itemId} (since{" "}
              {new Date(item.since).toLocaleDateString()})
            </li>
          ))}
        </ul>
      </form>

      <section data-testid="unassigned-section">
        <h2>Unassigned</h2>
        <ul>
          {unassigned.map((item) => (
            <li
              key={`${item.itemType}-${item.itemId}`}
              data-testid="unassigned-row"
            >
              {item.itemType}: {item.itemId}
              {item.lastEndedAt
                ? ` (ended ${new Date(item.lastEndedAt).toLocaleDateString()})`
                : " (never assigned)"}
            </li>
          ))}
        </ul>
      </section>

      <form
        data-testid="assign-manager-to-zone-form"
        onSubmit={handleAssignManagerToZone}
      >
        <h2>Assign Manager to Zone</h2>
        <label>
          Zone ID
          <input
            data-testid="zone-manager-zone-id-input"
            value={zoneManagerZoneId}
            onChange={(e) => setZoneManagerZoneId(e.target.value)}
          />
        </label>
        <label>
          Manager ID
          <input
            data-testid="zone-manager-manager-id-input"
            value={zoneManagerManagerId}
            onChange={(e) => setZoneManagerManagerId(e.target.value)}
          />
        </label>
        <button type="submit">Assign Manager to Zone</button>
        {zoneManagerMessage && (
          <p data-testid="zone-manager-message">{zoneManagerMessage}</p>
        )}
        {zoneManagerError && (
          <p data-testid="zone-manager-error">{zoneManagerError}</p>
        )}
      </form>

      <form data-testid="zone-coverage-form" onSubmit={handleLookupCoverage}>
        <h2>Zone Coverage</h2>
        <label>
          Zone ID
          <input
            data-testid="coverage-zone-id-input"
            value={coverageZoneId}
            onChange={(e) => setCoverageZoneId(e.target.value)}
          />
        </label>
        <button type="submit">Look up</button>
        {coverage && (
          <div data-testid="zone-coverage-result">
            <p>Managers:</p>
            <ul>
              {coverage.managerIds.map((id) => (
                <li key={id} data-testid="coverage-manager-row">
                  {id}
                </li>
              ))}
            </ul>
            <p>Schools:</p>
            <ul>
              {coverage.schoolIds.map((id) => (
                <li key={id} data-testid="coverage-school-row">
                  {id}
                </li>
              ))}
            </ul>
          </div>
        )}
        {coverageError && (
          <p data-testid="zone-coverage-error">{coverageError}</p>
        )}
      </form>
    </div>
  );
}
