import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../../auth/AuthContext";
import {
  attendanceClient,
  type AttendanceMarkView,
  type AttendanceStatusCodeView,
} from "../MyAttendancePage/attendanceClient";
import {
  attendanceAdminClient,
  type AttendanceGridView,
  type LockStatusView,
  type NonWorkingDateView,
} from "./attendanceAdminClient";
import { AttendanceGrid } from "./AttendanceGrid";
import "./AttendancePage.css";

function currentPeriod(): string {
  return new Date().toISOString().slice(0, 7);
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * User Story 2 (specs/011-attendance): a Manager (own portfolio) or Admin
 * (any Teacher, unscoped — FR-024) marks attendance on behalf of a Teacher.
 * Also covers User Story 3 (viewing a Teacher's rollup/marks), User Story 4
 * (Director-only lock/reopen), User Story 5 (the editable grid), and the
 * Polish-phase Admin/Director status-code and Admin-only non-working-calendar
 * management. The backend enforces who can actually do what — this page does
 * not itself gate by role (matches TeacherProfilesPage/ZonesPage precedent).
 */
export function AttendancePage() {
  const { accessToken } = useAuth();
  const [statusCodes, setStatusCodes] = useState<AttendanceStatusCodeView[]>(
    [],
  );

  useEffect(() => {
    if (!accessToken) return;
    attendanceClient
      .listStatusCodes(accessToken)
      .then(setStatusCodes)
      .catch(() => {});
  }, [accessToken]);

  // ---- Mark on behalf --------------------------------------------------------------

  const [markTeacherId, setMarkTeacherId] = useState("");
  const [markDate, setMarkDate] = useState(todayIso());
  const [markSchoolId, setMarkSchoolId] = useState("");
  const [markStatusCode, setMarkStatusCode] = useState("PRESENT");
  const [markFractionalValue, setMarkFractionalValue] = useState("1.00");
  const [markMessage, setMarkMessage] = useState<string | null>(null);
  const [markError, setMarkError] = useState<string | null>(null);

  async function handleMarkOnBehalf(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setMarkError(null);
    setMarkMessage(null);
    try {
      const mark = await attendanceAdminClient.markOnBehalf(
        accessToken,
        markTeacherId,
        markDate,
        markSchoolId,
        markStatusCode,
        markFractionalValue ? Number(markFractionalValue) : undefined,
      );
      setMarkMessage(
        `Saved ${mark.statusCode} for ${mark.markDate} (${mark.markedByRole}).`,
      );
    } catch (e) {
      setMarkError(
        e instanceof Error ? e.message : "Unable to save attendance.",
      );
    }
  }

  // ---- View rollup / marks ---------------------------------------------------------

  const [viewTeacherId, setViewTeacherId] = useState("");
  const [viewPeriod, setViewPeriod] = useState(currentPeriod());
  const [rollup, setRollup] = useState<Awaited<
    ReturnType<typeof attendanceAdminClient.getRollup>
  > | null>(null);
  const [marks, setMarks] = useState<AttendanceMarkView[]>([]);
  const [viewError, setViewError] = useState<string | null>(null);

  async function handleView(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setViewError(null);
    setRollup(null);
    setMarks([]);
    try {
      const [rollupResult, marksResult] = await Promise.all([
        attendanceAdminClient.getRollup(accessToken, viewTeacherId, viewPeriod),
        attendanceAdminClient.listMarks(accessToken, viewTeacherId, viewPeriod),
      ]);
      setRollup(rollupResult);
      setMarks(marksResult);
    } catch (e) {
      setViewError(
        e instanceof Error
          ? e.message
          : "Unable to load this Teacher's attendance.",
      );
    }
  }

  // ---- Lock / reopen -----------------------------------------------------------------

  const [lockTeacherId, setLockTeacherId] = useState("");
  const [lockPeriod, setLockPeriod] = useState(currentPeriod());
  const [reopenReason, setReopenReason] = useState("");
  const [lockStatus, setLockStatus] = useState<LockStatusView | null>(null);
  const [lockError, setLockError] = useState<string | null>(null);

  async function handleRefreshStatus() {
    if (!accessToken) return;
    setLockError(null);
    try {
      setLockStatus(
        await attendanceAdminClient.getLockStatus(
          accessToken,
          lockTeacherId,
          lockPeriod,
        ),
      );
    } catch (e) {
      setLockError(
        e instanceof Error ? e.message : "Unable to load lock status.",
      );
    }
  }

  async function handleLock() {
    if (!accessToken) return;
    setLockError(null);
    try {
      setLockStatus(
        await attendanceAdminClient.lockMonth(
          accessToken,
          lockTeacherId,
          lockPeriod,
        ),
      );
    } catch (e) {
      setLockError(
        e instanceof Error ? e.message : "Unable to lock this teacher-month.",
      );
    }
  }

  async function handleReopen(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setLockError(null);
    try {
      setLockStatus(
        await attendanceAdminClient.reopenMonth(
          accessToken,
          lockTeacherId,
          lockPeriod,
          reopenReason,
        ),
      );
    } catch (e) {
      setLockError(
        e instanceof Error ? e.message : "Unable to reopen this teacher-month.",
      );
    }
  }

  // ---- Status code management (Admin or Director, FR-005) --------------------------

  const [newCode, setNewCode] = useState("");
  const [newLabel, setNewLabel] = useState("");
  const [newCategory, setNewCategory] = useState<
    "WORKED" | "LEAVE" | "TRAINING" | "NON_WORKING"
  >("WORKED");
  const [newWeight, setNewWeight] = useState("1.00");
  const [statusCodeMessage, setStatusCodeMessage] = useState<string | null>(
    null,
  );
  const [statusCodeError, setStatusCodeError] = useState<string | null>(null);

  async function handleCreateStatusCode(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setStatusCodeError(null);
    setStatusCodeMessage(null);
    try {
      const code = await attendanceAdminClient.createStatusCode(
        accessToken,
        newCode,
        newLabel,
        newCategory,
        Number(newWeight),
      );
      setStatusCodeMessage(`Added status code ${code.code}.`);
      setStatusCodes((prev) => [...prev, code]);
    } catch (e) {
      setStatusCodeError(
        e instanceof Error ? e.message : "Unable to add status code.",
      );
    }
  }

  // ---- Non-working calendar management (Admin-only, FR-022) ------------------------

  const [calendarPeriod, setCalendarPeriod] = useState(currentPeriod());
  const [nonWorkingDates, setNonWorkingDates] = useState<NonWorkingDateView[]>(
    [],
  );
  const [newHolidayDate, setNewHolidayDate] = useState("");
  const [newHolidayLabel, setNewHolidayLabel] = useState("");
  const [calendarMessage, setCalendarMessage] = useState<string | null>(null);
  const [calendarError, setCalendarError] = useState<string | null>(null);

  async function loadNonWorkingDates() {
    if (!accessToken) return;
    try {
      setNonWorkingDates(
        await attendanceAdminClient.listNonWorkingDates(
          accessToken,
          calendarPeriod,
        ),
      );
    } catch (e) {
      setCalendarError(
        e instanceof Error
          ? e.message
          : "Unable to load the non-working calendar.",
      );
    }
  }

  async function handleAddNonWorkingDate(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setCalendarError(null);
    setCalendarMessage(null);
    try {
      const date = await attendanceAdminClient.addNonWorkingDate(
        accessToken,
        newHolidayDate,
        newHolidayLabel,
      );
      setCalendarMessage(`Added ${date.label} (${date.date}).`);
      loadNonWorkingDates();
    } catch (e) {
      setCalendarError(
        e instanceof Error ? e.message : "Unable to add this date.",
      );
    }
  }

  async function handleDeactivateNonWorkingDate(id: string) {
    if (!accessToken) return;
    setCalendarError(null);
    try {
      await attendanceAdminClient.deactivateNonWorkingDate(accessToken, id);
      loadNonWorkingDates();
    } catch (e) {
      setCalendarError(
        e instanceof Error ? e.message : "Unable to deactivate this date.",
      );
    }
  }

  // ---- Grid (User Story 5) ----------------------------------------------------------

  const [gridPeriod, setGridPeriod] = useState(currentPeriod());
  const [gridManagerId, setGridManagerId] = useState("");
  const [grid, setGrid] = useState<AttendanceGridView | null>(null);
  const [gridError, setGridError] = useState<string | null>(null);

  async function loadGrid() {
    if (!accessToken) return;
    setGridError(null);
    try {
      setGrid(
        await attendanceAdminClient.getAttendanceGrid(
          accessToken,
          gridPeriod,
          gridManagerId || undefined,
        ),
      );
    } catch (e) {
      setGridError(
        e instanceof Error ? e.message : "Unable to load the attendance grid.",
      );
    }
  }

  async function handleLoadGrid(event: FormEvent) {
    event.preventDefault();
    await loadGrid();
  }

  async function handleGridCellEdit(
    teacherId: string,
    date: string,
    schoolId: string,
    statusCode: string,
    fractionalValue: number,
  ) {
    if (!accessToken) return;
    await attendanceAdminClient.markOnBehalf(
      accessToken,
      teacherId,
      date,
      schoolId,
      statusCode,
      fractionalValue,
    );
    await loadGrid();
  }

  return (
    <div className="attendance-page" data-testid="attendance-page">
      <h1>Attendance</h1>

      <form data-testid="mark-on-behalf-form" onSubmit={handleMarkOnBehalf}>
        <h2>Mark On Behalf</h2>
        <label>
          Teacher ID
          <input
            data-testid="mark-teacher-id-input"
            value={markTeacherId}
            onChange={(e) => setMarkTeacherId(e.target.value)}
          />
        </label>
        <label>
          Date
          <input
            type="date"
            data-testid="mark-on-behalf-date-input"
            value={markDate}
            onChange={(e) => setMarkDate(e.target.value)}
          />
        </label>
        <label>
          School
          <input
            data-testid="mark-on-behalf-school-id-input"
            value={markSchoolId}
            onChange={(e) => setMarkSchoolId(e.target.value)}
          />
        </label>
        <label>
          Status
          <select
            data-testid="mark-on-behalf-status-select"
            value={markStatusCode}
            onChange={(e) => setMarkStatusCode(e.target.value)}
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
          Fractional value
          <input
            data-testid="mark-on-behalf-fractional-value-input"
            value={markFractionalValue}
            onChange={(e) => setMarkFractionalValue(e.target.value)}
          />
        </label>
        <button type="submit">Save</button>
        {markMessage && (
          <p data-testid="mark-on-behalf-message">{markMessage}</p>
        )}
        {markError && <p data-testid="mark-on-behalf-error">{markError}</p>}
      </form>

      <form data-testid="view-attendance-form" onSubmit={handleView}>
        <h2>View a Teacher's Attendance</h2>
        <label>
          Teacher ID
          <input
            data-testid="view-teacher-id-input"
            value={viewTeacherId}
            onChange={(e) => setViewTeacherId(e.target.value)}
          />
        </label>
        <label>
          Month
          <input
            data-testid="view-period-input"
            value={viewPeriod}
            onChange={(e) => setViewPeriod(e.target.value)}
          />
        </label>
        <button type="submit">Look up</button>
        {viewError && <p data-testid="view-attendance-error">{viewError}</p>}
        {rollup && (
          <dl data-testid="teacher-rollup">
            <dt>Days worked</dt>
            <dd data-testid="teacher-rollup-days-worked">
              {rollup.daysWorked}
            </dd>
            <dt>Days leave</dt>
            <dd data-testid="teacher-rollup-days-leave">{rollup.daysLeave}</dd>
            <dt>Lock status</dt>
            <dd data-testid="teacher-rollup-lock-status">
              {rollup.lockStatus}
            </dd>
          </dl>
        )}
        {marks.length > 0 && (
          <ul data-testid="teacher-marks-list">
            {marks.map((mark) => (
              <li key={mark.id}>
                {mark.markDate}: {mark.statusCode} ({mark.fractionalValue})
              </li>
            ))}
          </ul>
        )}
      </form>

      <section data-testid="lock-reopen-section">
        <h2>Lock / Reopen</h2>
        <label>
          Teacher ID
          <input
            data-testid="lock-teacher-id-input"
            value={lockTeacherId}
            onChange={(e) => setLockTeacherId(e.target.value)}
          />
        </label>
        <label>
          Month
          <input
            data-testid="lock-period-input"
            value={lockPeriod}
            onChange={(e) => setLockPeriod(e.target.value)}
          />
        </label>
        <button
          data-testid="refresh-lock-status-button"
          type="button"
          onClick={handleRefreshStatus}
        >
          Refresh Status
        </button>
        <button
          data-testid="lock-month-button"
          type="button"
          onClick={handleLock}
        >
          Lock Month
        </button>

        <form data-testid="reopen-form" onSubmit={handleReopen}>
          <label>
            Reason
            <input
              data-testid="reopen-reason-input"
              value={reopenReason}
              onChange={(e) => setReopenReason(e.target.value)}
            />
          </label>
          <button type="submit">Reopen</button>
        </form>

        {lockError && <p data-testid="lock-reopen-error">{lockError}</p>}
        {lockStatus && (
          <div data-testid="lock-status">
            <p data-testid="lock-status-value">{lockStatus.status}</p>
            <ul data-testid="reopen-history">
              {lockStatus.reopenHistory.map((entry, i) => (
                <li key={i}>
                  {entry.reason} — reopened {entry.reopenedAt}
                  {entry.relockedAt ? `, relocked ${entry.relockedAt}` : ""}
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>

      <form
        data-testid="create-status-code-form"
        onSubmit={handleCreateStatusCode}
      >
        <h2>Attendance Status Codes</h2>
        <label>
          Code
          <input
            data-testid="new-status-code-input"
            value={newCode}
            onChange={(e) => setNewCode(e.target.value)}
          />
        </label>
        <label>
          Label
          <input
            data-testid="new-status-label-input"
            value={newLabel}
            onChange={(e) => setNewLabel(e.target.value)}
          />
        </label>
        <label>
          Category
          <select
            data-testid="new-status-category-select"
            value={newCategory}
            onChange={(e) =>
              setNewCategory(e.target.value as typeof newCategory)
            }
          >
            <option value="WORKED">Worked</option>
            <option value="LEAVE">Leave</option>
            <option value="TRAINING">Training</option>
            <option value="NON_WORKING">Non-working</option>
          </select>
        </label>
        <label>
          Weight
          <input
            data-testid="new-status-weight-input"
            value={newWeight}
            onChange={(e) => setNewWeight(e.target.value)}
          />
        </label>
        <button type="submit">Add Status Code</button>
        {statusCodeMessage && (
          <p data-testid="status-code-message">{statusCodeMessage}</p>
        )}
        {statusCodeError && (
          <p data-testid="status-code-error">{statusCodeError}</p>
        )}
      </form>

      <section
        data-testid="non-working-calendar"
        className="non-working-calendar"
      >
        <h2>Non-Working Calendar</h2>
        <label>
          Month
          <input
            data-testid="calendar-period-input"
            value={calendarPeriod}
            onChange={(e) => setCalendarPeriod(e.target.value)}
          />
        </label>
        <button
          data-testid="load-calendar-button"
          type="button"
          onClick={loadNonWorkingDates}
        >
          Load
        </button>

        <form
          data-testid="add-non-working-date-form"
          onSubmit={handleAddNonWorkingDate}
        >
          <label>
            Date
            <input
              type="date"
              data-testid="new-holiday-date-input"
              value={newHolidayDate}
              onChange={(e) => setNewHolidayDate(e.target.value)}
            />
          </label>
          <label>
            Label
            <input
              data-testid="new-holiday-label-input"
              value={newHolidayLabel}
              onChange={(e) => setNewHolidayLabel(e.target.value)}
            />
          </label>
          <button type="submit">Add</button>
        </form>

        {calendarMessage && (
          <p data-testid="calendar-message">{calendarMessage}</p>
        )}
        {calendarError && <p data-testid="calendar-error">{calendarError}</p>}
        <ul data-testid="non-working-dates-list">
          {nonWorkingDates.map((d) => (
            <li key={d.id}>
              {d.date} — {d.label} {d.active ? "" : "(inactive)"}
              {d.active && (
                <button
                  data-testid={`deactivate-non-working-date-${d.id}`}
                  type="button"
                  onClick={() => handleDeactivateNonWorkingDate(d.id)}
                >
                  Deactivate
                </button>
              )}
            </li>
          ))}
        </ul>
      </section>

      <form data-testid="load-grid-form" onSubmit={handleLoadGrid}>
        <h2>Attendance Grid</h2>
        <label>
          Month
          <input
            data-testid="grid-period-input"
            value={gridPeriod}
            onChange={(e) => setGridPeriod(e.target.value)}
          />
        </label>
        <label>
          Manager ID (optional filter)
          <input
            data-testid="grid-manager-id-input"
            value={gridManagerId}
            onChange={(e) => setGridManagerId(e.target.value)}
          />
        </label>
        <button type="submit">Load Grid</button>
        {gridError && <p data-testid="grid-error">{gridError}</p>}
      </form>
      {grid && (
        <AttendanceGrid
          grid={grid}
          statusCodes={statusCodes}
          onEditCell={handleGridCellEdit}
        />
      )}
    </div>
  );
}
