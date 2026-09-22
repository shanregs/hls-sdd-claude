import { useState, type FormEvent } from "react";
import { useAuth } from "../../auth/AuthContext";
import {
  teacherClient,
  type SalaryAsOfAnswer,
  type TeacherProfileView,
  type TeacherStatus,
} from "./teacherClient";
import "./TeacherProfilesPage.css";

/**
 * User Stories 1-3 (specs/005-teacher): Admin creates/updates/status-changes
 * a Teacher Profile; Director/Manager/Admin can view one (the backend
 * enforces who can actually do what — this page does not itself gate by
 * role, matching ZonesPage/AssignmentsPage precedent).
 *
 * Also covers specs/009-teacher-salary-history User Stories 2 and 4:
 * recording a salary change (its own form, separate from the general
 * contact-detail update — a salary change carries an effective date) and
 * looking up the salary in effect on a specific past date.
 */
export function TeacherProfilesPage() {
  const { accessToken } = useAuth();

  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [salary, setSalary] = useState("");
  const [status, setStatus] = useState<TeacherStatus>("IN_TRAINING");
  const [createdTeacherId, setCreatedTeacherId] = useState<string | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);

  const [updateTeacherId, setUpdateTeacherId] = useState("");
  const [updateEmail, setUpdateEmail] = useState("");
  const [updateMessage, setUpdateMessage] = useState<string | null>(null);
  const [updateError, setUpdateError] = useState<string | null>(null);

  const [salaryTeacherId, setSalaryTeacherId] = useState("");
  const [salaryAmount, setSalaryAmount] = useState("");
  const [salaryEffectiveFrom, setSalaryEffectiveFrom] = useState("");
  const [salaryMessage, setSalaryMessage] = useState<string | null>(null);
  const [salaryError, setSalaryError] = useState<string | null>(null);

  const [lookupSalaryTeacherId, setLookupSalaryTeacherId] = useState("");
  const [lookupSalaryAsOf, setLookupSalaryAsOf] = useState("");
  const [lookedUpSalary, setLookedUpSalary] = useState<SalaryAsOfAnswer | null>(
    null,
  );
  const [lookupSalaryError, setLookupSalaryError] = useState<string | null>(
    null,
  );

  const [statusTeacherId, setStatusTeacherId] = useState("");
  const [newStatus, setNewStatus] = useState<TeacherStatus>("ACTIVE");
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);

  const [viewTeacherId, setViewTeacherId] = useState("");
  const [viewedProfile, setViewedProfile] = useState<TeacherProfileView | null>(
    null,
  );
  const [viewError, setViewError] = useState<string | null>(null);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setCreateError(null);
    try {
      const profile = await teacherClient.createTeacherProfile(accessToken, {
        name,
        phone,
        email: email || undefined,
        hlsOfferedSalary: Number(salary),
        status,
      });
      setCreatedTeacherId(profile.id);
    } catch (e) {
      setCreateError(
        e instanceof Error ? e.message : "Unable to create profile.",
      );
    }
  }

  async function handleUpdate(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setUpdateError(null);
    setUpdateMessage(null);
    try {
      const profile = await teacherClient.updateTeacherProfile(
        accessToken,
        updateTeacherId,
        {
          email: updateEmail || undefined,
        },
      );
      setUpdateMessage(`Updated ${profile.name}: email now ${profile.email}.`);
    } catch (e) {
      setUpdateError(e instanceof Error ? e.message : "Update failed.");
    }
  }

  async function handleRecordSalaryChange(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setSalaryError(null);
    setSalaryMessage(null);
    try {
      const entry = await teacherClient.recordSalaryChange(
        accessToken,
        salaryTeacherId,
        Number(salaryAmount),
        salaryEffectiveFrom || undefined,
      );
      setSalaryMessage(
        `Recorded ${entry.amount}, effective ${entry.effectiveFrom}.`,
      );
    } catch (e) {
      setSalaryError(
        e instanceof Error ? e.message : "Unable to record salary change.",
      );
    }
  }

  async function handleLookupSalary(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setLookupSalaryError(null);
    setLookedUpSalary(null);
    try {
      const answer = await teacherClient.getSalary(
        accessToken,
        lookupSalaryTeacherId,
        lookupSalaryAsOf || undefined,
      );
      setLookedUpSalary(answer);
    } catch (e) {
      setLookupSalaryError(
        e instanceof Error ? e.message : "Unable to look up salary.",
      );
    }
  }

  async function handleChangeStatus(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setStatusError(null);
    setStatusMessage(null);
    try {
      const profile = await teacherClient.changeTeacherStatus(
        accessToken,
        statusTeacherId,
        newStatus,
      );
      setStatusMessage(`${profile.name} is now ${profile.status}.`);
    } catch (e) {
      setStatusError(e instanceof Error ? e.message : "Status change failed.");
    }
  }

  async function handleView(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setViewError(null);
    setViewedProfile(null);
    try {
      const profile = await teacherClient.getTeacherProfile(
        accessToken,
        viewTeacherId,
      );
      setViewedProfile(profile);
    } catch (e) {
      setViewError(
        e instanceof Error ? e.message : "Unable to view this profile.",
      );
    }
  }

  return (
    <div className="teacher-profiles-page" data-testid="teacher-profiles-page">
      <h1>Teacher Profiles</h1>

      <form data-testid="create-teacher-form" onSubmit={handleCreate}>
        <h2>Create Profile</h2>
        <label>
          Name
          <input
            data-testid="teacher-name-input"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <label>
          Phone
          <input
            data-testid="teacher-phone-input"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
          />
        </label>
        <label>
          Email
          <input
            data-testid="teacher-email-input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label>
          HLS-offered salary
          <input
            data-testid="teacher-salary-input"
            value={salary}
            onChange={(e) => setSalary(e.target.value)}
          />
        </label>
        <label>
          Initial status
          <select
            data-testid="teacher-status-select"
            value={status}
            onChange={(e) => setStatus(e.target.value as TeacherStatus)}
          >
            <option value="IN_TRAINING">In training</option>
            <option value="ACTIVE">Active</option>
            <option value="ON_LEAVE">On leave</option>
            <option value="EXITED">Exited</option>
          </select>
        </label>
        <button type="submit">Create</button>
        {createdTeacherId && (
          <p data-testid="create-teacher-message">
            Created teacher {createdTeacherId}
          </p>
        )}
        {createError && <p data-testid="create-teacher-error">{createError}</p>}
      </form>

      <form data-testid="update-teacher-form" onSubmit={handleUpdate}>
        <h2>Update Profile</h2>
        <label>
          Teacher ID
          <input
            data-testid="update-teacher-id-input"
            value={updateTeacherId}
            onChange={(e) => setUpdateTeacherId(e.target.value)}
          />
        </label>
        <label>
          New email
          <input
            data-testid="update-email-input"
            value={updateEmail}
            onChange={(e) => setUpdateEmail(e.target.value)}
          />
        </label>
        <button type="submit">Update</button>
        {updateMessage && (
          <p data-testid="update-teacher-message">{updateMessage}</p>
        )}
        {updateError && <p data-testid="update-teacher-error">{updateError}</p>}
      </form>

      <form
        data-testid="record-salary-form"
        onSubmit={handleRecordSalaryChange}
      >
        <h2>Record Salary Change</h2>
        <label>
          Teacher ID
          <input
            data-testid="salary-teacher-id-input"
            value={salaryTeacherId}
            onChange={(e) => setSalaryTeacherId(e.target.value)}
          />
        </label>
        <label>
          Amount
          <input
            data-testid="salary-amount-input"
            value={salaryAmount}
            onChange={(e) => setSalaryAmount(e.target.value)}
          />
        </label>
        <label>
          Effective from (optional, defaults to today)
          <input
            type="date"
            data-testid="salary-effective-from-input"
            value={salaryEffectiveFrom}
            onChange={(e) => setSalaryEffectiveFrom(e.target.value)}
          />
        </label>
        <button type="submit">Record</button>
        {salaryMessage && (
          <p data-testid="record-salary-message">{salaryMessage}</p>
        )}
        {salaryError && <p data-testid="record-salary-error">{salaryError}</p>}
      </form>

      <form data-testid="salary-lookup-form" onSubmit={handleLookupSalary}>
        <h2>Salary As Of</h2>
        <label>
          Teacher ID
          <input
            data-testid="lookup-salary-teacher-id-input"
            value={lookupSalaryTeacherId}
            onChange={(e) => setLookupSalaryTeacherId(e.target.value)}
          />
        </label>
        <label>
          As of date (optional, defaults to current)
          <input
            type="date"
            data-testid="lookup-salary-as-of-input"
            value={lookupSalaryAsOf}
            onChange={(e) => setLookupSalaryAsOf(e.target.value)}
          />
        </label>
        <button type="submit">Look up</button>
        {lookedUpSalary && lookedUpSalary.state === "RECORDED" && (
          <p data-testid="salary-lookup-result">{lookedUpSalary.amount}</p>
        )}
        {lookedUpSalary && lookedUpSalary.state === "NOT_YET_RECORDED" && (
          <p data-testid="salary-lookup-not-yet-recorded">
            No salary recorded as of that date.
          </p>
        )}
        {lookupSalaryError && (
          <p data-testid="salary-lookup-error">{lookupSalaryError}</p>
        )}
      </form>

      <form data-testid="change-status-form" onSubmit={handleChangeStatus}>
        <h2>Change Status</h2>
        <label>
          Teacher ID
          <input
            data-testid="status-teacher-id-input"
            value={statusTeacherId}
            onChange={(e) => setStatusTeacherId(e.target.value)}
          />
        </label>
        <label>
          New status
          <select
            data-testid="new-status-select"
            value={newStatus}
            onChange={(e) => setNewStatus(e.target.value as TeacherStatus)}
          >
            <option value="IN_TRAINING">In training</option>
            <option value="ACTIVE">Active</option>
            <option value="ON_LEAVE">On leave</option>
            <option value="EXITED">Exited</option>
          </select>
        </label>
        <button type="submit">Change status</button>
        {statusMessage && <p data-testid="status-message">{statusMessage}</p>}
        {statusError && <p data-testid="status-error">{statusError}</p>}
      </form>

      <form data-testid="view-teacher-form" onSubmit={handleView}>
        <h2>View Profile</h2>
        <label>
          Teacher ID
          <input
            data-testid="view-teacher-id-input"
            value={viewTeacherId}
            onChange={(e) => setViewTeacherId(e.target.value)}
          />
        </label>
        <button type="submit">Look up</button>
        {viewedProfile && (
          <div data-testid="viewed-profile">
            <p>{viewedProfile.name}</p>
            <p>{viewedProfile.phone}</p>
            <p>{viewedProfile.hlsOfferedSalary}</p>
            <p>{viewedProfile.status}</p>
          </div>
        )}
        {viewError && <p data-testid="view-teacher-error">{viewError}</p>}
      </form>
    </div>
  );
}
