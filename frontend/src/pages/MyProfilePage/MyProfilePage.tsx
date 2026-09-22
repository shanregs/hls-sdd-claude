import { useEffect, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import {
  teacherClient,
  type TeacherProfileView,
} from "../TeacherProfilesPage/teacherClient";
import "./MyProfilePage.css";

/**
 * User Story 4 (specs/005-teacher): a Teacher views their own profile,
 * read-only. Calls GET /teachers/me (research.md §3's new "teacherId" JWT
 * claim). No edit control is offered anywhere on this page (FR-009, AC2).
 */
export function MyProfilePage() {
  const { accessToken } = useAuth();
  const [profile, setProfile] = useState<TeacherProfileView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    teacherClient
      .getMyTeacherProfile(accessToken)
      .then(setProfile)
      .catch((e) =>
        setError(
          e instanceof Error ? e.message : "Unable to load your profile.",
        ),
      );
  }, [accessToken]);

  return (
    <div className="my-profile-page" data-testid="my-profile-page">
      <h1>My Profile</h1>
      {error && <p data-testid="my-profile-error">{error}</p>}
      {profile && (
        <dl data-testid="my-profile-details">
          <dt>Name</dt>
          <dd data-testid="my-profile-name">{profile.name}</dd>
          <dt>Phone</dt>
          <dd data-testid="my-profile-phone">{profile.phone}</dd>
          {profile.email && (
            <>
              <dt>Email</dt>
              <dd data-testid="my-profile-email">{profile.email}</dd>
            </>
          )}
          <dt>HLS-offered salary</dt>
          <dd data-testid="my-profile-salary">{profile.hlsOfferedSalary}</dd>
          <dt>Status</dt>
          <dd data-testid="my-profile-status">{profile.status}</dd>
        </dl>
      )}
    </div>
  );
}
