import { useState, type FormEvent } from "react";
import { useAuth } from "../../auth/AuthContext";
import {
  zoneClient,
  type BulkImportResponse,
  type PlaceView,
} from "./zoneClient";
import "./ZonesPage.css";

/**
 * User Stories 1-3 (specs/007-school-zone): Director/Admin creates Zones,
 * assigns a School to a Zone, and views a Zone's Schools / a School's Zone.
 * Reassignment (naming an existing assignment's id) isn't exposed here yet —
 * this minimal slice's API has no endpoint to look up that id (quickstart.md
 * notes the same gap); assigning a School that already has a Zone surfaces
 * the resulting 409 as an error rather than silently failing.
 *
 * Also covers specs/008-school-places User Stories 1-3: adding a Place to a
 * Zone, looking up which Zone a Place belongs to (by PIN code or by name),
 * and viewing every Place recorded under a Zone.
 *
 * Also covers specs/010-place-bulk-import: submitting many places (pasted as
 * a JSON array) in one request, with a per-row success/failure report — a
 * bad row never blocks the other valid rows in the same batch.
 */
export function ZonesPage() {
  const { accessToken } = useAuth();

  const [zoneName, setZoneName] = useState("");
  const [createdZoneId, setCreatedZoneId] = useState<string | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);

  const [assignSchoolId, setAssignSchoolId] = useState("");
  const [assignZoneId, setAssignZoneId] = useState("");
  const [assignMessage, setAssignMessage] = useState<string | null>(null);
  const [assignError, setAssignError] = useState<string | null>(null);

  const [lookupZoneId, setLookupZoneId] = useState("");
  const [zoneSchools, setZoneSchools] = useState<string[] | null>(null);

  const [lookupSchoolId, setLookupSchoolId] = useState("");
  const [schoolZone, setSchoolZone] = useState<string | null>(null);

  const [placeZoneId, setPlaceZoneId] = useState("");
  const [placeName, setPlaceName] = useState("");
  const [placePincode, setPlacePincode] = useState("");
  const [addPlaceMessage, setAddPlaceMessage] = useState<string | null>(null);
  const [addPlaceError, setAddPlaceError] = useState<string | null>(null);

  const [findBy, setFindBy] = useState<"pincode" | "name">("pincode");
  const [findValue, setFindValue] = useState("");
  const [foundPlaces, setFoundPlaces] = useState<PlaceView[] | null>(null);
  const [findPlacesError, setFindPlacesError] = useState<string | null>(null);

  const [zonePlacesZoneId, setZonePlacesZoneId] = useState("");
  const [zonePlaces, setZonePlaces] = useState<PlaceView[] | null>(null);

  const [bulkImportJson, setBulkImportJson] = useState("");
  const [bulkImportResult, setBulkImportResult] =
    useState<BulkImportResponse | null>(null);
  const [bulkImportError, setBulkImportError] = useState<string | null>(null);

  async function handleCreateZone(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setCreateError(null);
    try {
      const zone = await zoneClient.createZone(accessToken, zoneName);
      setCreatedZoneId(zone.id);
    } catch (e) {
      setCreateError(e instanceof Error ? e.message : "Unable to create Zone.");
    }
  }

  async function handleAssignSchool(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setAssignError(null);
    setAssignMessage(null);
    try {
      await zoneClient.assignSchoolToZone(
        accessToken,
        assignSchoolId,
        assignZoneId,
      );
      setAssignMessage(
        `Assigned school ${assignSchoolId} to zone ${assignZoneId}.`,
      );
    } catch (e) {
      setAssignError(e instanceof Error ? e.message : "Assignment failed.");
    }
  }

  async function handleLookupZoneSchools(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    try {
      const schools = await zoneClient.getZoneSchools(
        accessToken,
        lookupZoneId,
      );
      setZoneSchools(schools);
    } catch {
      setZoneSchools(null);
    }
  }

  async function handleLookupSchoolZone(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    try {
      const answer = await zoneClient.getSchoolZone(
        accessToken,
        lookupSchoolId,
      );
      setSchoolZone(
        answer.state === "CURRENT_ZONE"
          ? (answer.zoneId ?? null)
          : "unassigned",
      );
    } catch {
      setSchoolZone(null);
    }
  }

  async function handleAddPlace(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setAddPlaceError(null);
    setAddPlaceMessage(null);
    try {
      const place = await zoneClient.addPlace(
        accessToken,
        placeZoneId,
        placeName,
        placePincode,
      );
      setAddPlaceMessage(
        `Added place ${place.name} (${place.pincode}) to zone ${place.zoneId}.`,
      );
    } catch (e) {
      setAddPlaceError(e instanceof Error ? e.message : "Unable to add place.");
    }
  }

  async function handleFindPlaces(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setFindPlacesError(null);
    try {
      const places =
        findBy === "pincode"
          ? await zoneClient.findPlacesByPincode(accessToken, findValue)
          : await zoneClient.findPlacesByName(accessToken, findValue);
      setFoundPlaces(places);
    } catch (e) {
      setFoundPlaces(null);
      setFindPlacesError(e instanceof Error ? e.message : "Lookup failed.");
    }
  }

  async function handleLookupZonePlaces(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    try {
      const places = await zoneClient.getZonePlaces(
        accessToken,
        zonePlacesZoneId,
      );
      setZonePlaces(places);
    } catch {
      setZonePlaces(null);
    }
  }

  async function handleBulkImport(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setBulkImportError(null);
    setBulkImportResult(null);
    try {
      const rows = JSON.parse(bulkImportJson);
      const result = await zoneClient.bulkImportPlaces(accessToken, rows);
      setBulkImportResult(result);
    } catch (e) {
      setBulkImportError(
        e instanceof Error ? e.message : "Bulk import failed.",
      );
    }
  }

  return (
    <div className="zones-page" data-testid="zones-page">
      <h1>Zones</h1>

      <form data-testid="create-zone-form" onSubmit={handleCreateZone}>
        <h2>Create Zone</h2>
        <label>
          Name
          <input
            data-testid="zone-name-input"
            value={zoneName}
            onChange={(e) => setZoneName(e.target.value)}
          />
        </label>
        <button type="submit">Create</button>
        {createdZoneId && (
          <p data-testid="create-zone-message">Created zone {createdZoneId}</p>
        )}
        {createError && <p data-testid="create-zone-error">{createError}</p>}
      </form>

      <form data-testid="assign-school-form" onSubmit={handleAssignSchool}>
        <h2>Assign School to Zone</h2>
        <label>
          School ID
          <input
            data-testid="assign-school-id-input"
            value={assignSchoolId}
            onChange={(e) => setAssignSchoolId(e.target.value)}
          />
        </label>
        <label>
          Zone ID
          <input
            data-testid="assign-zone-id-input"
            value={assignZoneId}
            onChange={(e) => setAssignZoneId(e.target.value)}
          />
        </label>
        <button type="submit">Assign</button>
        {assignMessage && <p data-testid="assign-message">{assignMessage}</p>}
        {assignError && <p data-testid="assign-error">{assignError}</p>}
      </form>

      <form data-testid="zone-schools-form" onSubmit={handleLookupZoneSchools}>
        <h2>Zone's Schools</h2>
        <label>
          Zone ID
          <input
            data-testid="lookup-zone-id-input"
            value={lookupZoneId}
            onChange={(e) => setLookupZoneId(e.target.value)}
          />
        </label>
        <button type="submit">Look up</button>
        <ul>
          {zoneSchools?.map((schoolId) => (
            <li key={schoolId} data-testid="zone-school-row">
              {schoolId}
            </li>
          ))}
        </ul>
      </form>

      <form data-testid="school-zone-form" onSubmit={handleLookupSchoolZone}>
        <h2>School's Zone</h2>
        <label>
          School ID
          <input
            data-testid="lookup-school-id-input"
            value={lookupSchoolId}
            onChange={(e) => setLookupSchoolId(e.target.value)}
          />
        </label>
        <button type="submit">Look up</button>
        {schoolZone && <p data-testid="school-zone-result">{schoolZone}</p>}
      </form>

      <form data-testid="add-place-form" onSubmit={handleAddPlace}>
        <h2>Add Place</h2>
        <label>
          Zone ID
          <input
            data-testid="place-zone-id-input"
            value={placeZoneId}
            onChange={(e) => setPlaceZoneId(e.target.value)}
          />
        </label>
        <label>
          Name
          <input
            data-testid="place-name-input"
            value={placeName}
            onChange={(e) => setPlaceName(e.target.value)}
          />
        </label>
        <label>
          PIN code
          <input
            data-testid="place-pincode-input"
            value={placePincode}
            onChange={(e) => setPlacePincode(e.target.value)}
          />
        </label>
        <button type="submit">Add place</button>
        {addPlaceMessage && (
          <p data-testid="add-place-message">{addPlaceMessage}</p>
        )}
        {addPlaceError && <p data-testid="add-place-error">{addPlaceError}</p>}
      </form>

      <form data-testid="find-places-form" onSubmit={handleFindPlaces}>
        <h2>Find Places</h2>
        <label>
          <input
            type="radio"
            name="find-by"
            checked={findBy === "pincode"}
            onChange={() => setFindBy("pincode")}
          />
          By PIN code
        </label>
        <label>
          <input
            type="radio"
            name="find-by"
            checked={findBy === "name"}
            onChange={() => setFindBy("name")}
          />
          By name
        </label>
        <label>
          {findBy === "pincode" ? "PIN code" : "Name"}
          <input
            data-testid="find-places-value-input"
            value={findValue}
            onChange={(e) => setFindValue(e.target.value)}
          />
        </label>
        <button type="submit">Find</button>
        {findPlacesError && (
          <p data-testid="find-places-error">{findPlacesError}</p>
        )}
        <ul>
          {foundPlaces?.map((place) => (
            <li key={place.id} data-testid="found-place-row">
              {place.name} ({place.pincode}) — zone {place.zoneId}
            </li>
          ))}
          {foundPlaces?.length === 0 && (
            <li data-testid="found-place-empty">No matching place.</li>
          )}
        </ul>
      </form>

      <form data-testid="zone-places-form" onSubmit={handleLookupZonePlaces}>
        <h2>Zone's Places</h2>
        <label>
          Zone ID
          <input
            data-testid="zone-places-id-input"
            value={zonePlacesZoneId}
            onChange={(e) => setZonePlacesZoneId(e.target.value)}
          />
        </label>
        <button type="submit">Look up</button>
        <ul>
          {zonePlaces?.map((place) => (
            <li key={place.id} data-testid="zone-place-row">
              {place.name} ({place.pincode})
            </li>
          ))}
        </ul>
      </form>

      <form data-testid="bulk-import-form" onSubmit={handleBulkImport}>
        <h2>Bulk Import Places</h2>
        <label>
          Rows (JSON array of {"{"}zoneId, name, pincode{"}"})
          <textarea
            data-testid="bulk-import-textarea"
            value={bulkImportJson}
            onChange={(e) => setBulkImportJson(e.target.value)}
            rows={6}
          />
        </label>
        <button type="submit">Import</button>
        {bulkImportError && (
          <p data-testid="bulk-import-error">{bulkImportError}</p>
        )}
        {bulkImportResult && (
          <div data-testid="bulk-import-summary">
            <p>
              {bulkImportResult.successCount} succeeded,{" "}
              {bulkImportResult.failureCount} failed.
            </p>
            <ul>
              {bulkImportResult.results
                .filter((r) => !r.succeeded)
                .map((r) => (
                  <li key={r.index} data-testid="bulk-import-failure-row">
                    Row {r.index}: {r.reason}
                  </li>
                ))}
            </ul>
          </div>
        )}
      </form>
    </div>
  );
}
