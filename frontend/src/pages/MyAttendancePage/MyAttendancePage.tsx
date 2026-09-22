import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../../auth/AuthContext";
import { teacherClient } from "../TeacherProfilesPage/teacherClient";
import {
  attendanceClient,
  type AttendanceStatusCodeView,
  type MonthlyAttendanceRollupView,
} from "./attendanceClient";
import "./MyAttendancePage.css";

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function currentPeriod(): string {
  return new Date().toISOString().slice(0, 7);
}

/**
 * User Story 1 (specs/011-attendance): a Teacher marks their own daily
 * attendance (status, optional half-day fractional value, optional
 * geo-tag/photo/check-in-code evidence) — the backend enforces the rest
 * (FR-023's backdating rule, FR-012's lock), this page does not itself gate
 * by role (matches TeacherProfilesPage/ZonesPage precedent).
 *
 * Also covers User Story 3: this Teacher's own monthly rollup, shown below
 * the marking form (`data-testid="my-rollup"`).
 */
export function MyAttendancePage() {
  const { accessToken } = useAuth();

  const [statusCodes, setStatusCodes] = useState<AttendanceStatusCodeView[]>(
    [],
  );
  const [markDate, setMarkDate] = useState(todayIso());
  const [schoolId, setSchoolId] = useState("");
  const [statusCode, setStatusCode] = useState("PRESENT");
  const [fractionalValue, setFractionalValue] = useState("1.00");
  const [geoLat, setGeoLat] = useState("");
  const [geoLng, setGeoLng] = useState("");
  const [photoUrl, setPhotoUrl] = useState("");
  const [checkinCode, setCheckinCode] = useState("");
  const [markMessage, setMarkMessage] = useState<string | null>(null);
  const [markError, setMarkError] = useState<string | null>(null);

  const [myTeacherId, setMyTeacherId] = useState<string | null>(null);
  const [rollup, setRollup] = useState<MonthlyAttendanceRollupView | null>(
    null,
  );
  const [rollupError, setRollupError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    attendanceClient
      .listStatusCodes(accessToken)
      .then(setStatusCodes)
      .catch(() => {
        // Non-fatal: the status dropdown just falls back to the default "PRESENT" option.
      });
  }, [accessToken]);

  async function loadRollup(teacherId: string) {
    if (!accessToken) return;
    setRollupError(null);
    try {
      setRollup(
        await attendanceClient.getMyRollup(
          accessToken,
          teacherId,
          currentPeriod(),
        ),
      );
    } catch (e) {
      setRollupError(
        e instanceof Error ? e.message : "Unable to load your rollup.",
      );
    }
  }

  useEffect(() => {
    if (!accessToken) return;
    teacherClient
      .getMyTeacherProfile(accessToken)
      .then((profile) => {
        setMyTeacherId(profile.id);
        return loadRollup(profile.id);
      })
      .catch(() => {
        // Non-fatal: the rollup section simply won't load without a linked teacher id.
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken]);

  async function handleMark(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setMarkError(null);
    setMarkMessage(null);
    try {
      const evidence =
        geoLat || geoLng || photoUrl || checkinCode
          ? {
              geoLat: geoLat ? Number(geoLat) : undefined,
              geoLng: geoLng ? Number(geoLng) : undefined,
              photoUrl: photoUrl || undefined,
              checkinCode: checkinCode || undefined,
            }
          : undefined;
      const mark = await attendanceClient.markMyAttendance(
        accessToken,
        markDate,
        schoolId,
        statusCode,
        fractionalValue ? Number(fractionalValue) : undefined,
        evidence,
      );
      setMarkMessage(`Saved ${mark.statusCode} for ${mark.markDate}.`);
      if (myTeacherId) {
        loadRollup(myTeacherId);
      }
    } catch (e) {
      setMarkError(
        e instanceof Error ? e.message : "Unable to save attendance.",
      );
    }
  }

  return (
    <div className="my-attendance-page" data-testid="my-attendance-page">
      <h1>My Attendance</h1>

      <form data-testid="mark-attendance-form" onSubmit={handleMark}>
        <h2>Mark Today's Attendance</h2>
        <label>
          Date
          <input
            type="date"
            data-testid="mark-date-input"
            value={markDate}
            onChange={(e) => setMarkDate(e.target.value)}
          />
        </label>
        <label>
          School
          <input
            data-testid="mark-school-id-input"
            value={schoolId}
            onChange={(e) => setSchoolId(e.target.value)}
          />
        </label>
        <label>
          Status
          <select
            data-testid="mark-status-select"
            value={statusCode}
            onChange={(e) => setStatusCode(e.target.value)}
          >
            {(statusCodes.length > 0
              ? statusCodes
              : [
                  {
                    code: "PRESENT",
                    label: "Present",
                  } as AttendanceStatusCodeView,
                ]
            ).map((code) => (
              <option key={code.code} value={code.code}>
                {code.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Fractional value (1.0 = full day, 0.5 = half day)
          <input
            data-testid="mark-fractional-value-input"
            value={fractionalValue}
            onChange={(e) => setFractionalValue(e.target.value)}
          />
        </label>
        <fieldset>
          <legend>Evidence (optional)</legend>
          <label>
            Geo-tag latitude
            <input
              data-testid="mark-geo-lat-input"
              value={geoLat}
              onChange={(e) => setGeoLat(e.target.value)}
            />
          </label>
          <label>
            Geo-tag longitude
            <input
              data-testid="mark-geo-lng-input"
              value={geoLng}
              onChange={(e) => setGeoLng(e.target.value)}
            />
          </label>
          <label>
            Photo URL
            <input
              data-testid="mark-photo-url-input"
              value={photoUrl}
              onChange={(e) => setPhotoUrl(e.target.value)}
            />
          </label>
          <label>
            Check-in code
            <input
              data-testid="mark-checkin-code-input"
              value={checkinCode}
              onChange={(e) => setCheckinCode(e.target.value)}
            />
          </label>
        </fieldset>
        <button type="submit">Save</button>
        {markMessage && (
          <p data-testid="mark-attendance-message">{markMessage}</p>
        )}
        {markError && <p data-testid="mark-attendance-error">{markError}</p>}
      </form>

      <section data-testid="my-rollup">
        <h2>This Month's Rollup</h2>
        {rollupError && <p data-testid="my-rollup-error">{rollupError}</p>}
        {rollup && (
          <dl>
            <dt>Days worked</dt>
            <dd data-testid="my-rollup-days-worked">{rollup.daysWorked}</dd>
            <dt>Days leave</dt>
            <dd data-testid="my-rollup-days-leave">{rollup.daysLeave}</dd>
            <dt>Training days (total / attended)</dt>
            <dd data-testid="my-rollup-training">
              {rollup.trainingDaysTotal} / {rollup.trainingDaysAttended}
            </dd>
            <dt>Overall working days</dt>
            <dd data-testid="my-rollup-overall-working-days">
              {rollup.overallWorkingDays}
            </dd>
            <dt>Unmarked days</dt>
            <dd data-testid="my-rollup-unmarked-days">{rollup.unmarkedDays}</dd>
            <dt>Weighted attendance total</dt>
            <dd data-testid="my-rollup-weighted-total">
              {rollup.weightedAttendanceTotal}
            </dd>
            <dt>Lock status</dt>
            <dd data-testid="my-rollup-lock-status">{rollup.lockStatus}</dd>
          </dl>
        )}
      </section>
    </div>
  );
}
