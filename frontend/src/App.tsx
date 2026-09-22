import { useState } from "react";
import { StatusPage } from "./pages/StatusPage/StatusPage";
import { LoginPage } from "./pages/LoginPage/LoginPage";
import { TeacherOtpLoginPage } from "./pages/TeacherOtpLoginPage/TeacherOtpLoginPage";
import { PasswordResetPage } from "./pages/PasswordResetPage/PasswordResetPage";
import { SessionsPage } from "./pages/SessionsPage/SessionsPage";
import { AssignmentsPage } from "./pages/AssignmentsPage/AssignmentsPage";
import { AuditHistoryPage } from "./pages/AuditHistoryPage/AuditHistoryPage";
import { ZonesPage } from "./pages/ZonesPage/ZonesPage";
import { TeacherProfilesPage } from "./pages/TeacherProfilesPage/TeacherProfilesPage";
import { MyProfilePage } from "./pages/MyProfilePage/MyProfilePage";
import { MyAttendancePage } from "./pages/MyAttendancePage/MyAttendancePage";
import { AttendancePage } from "./pages/AttendancePage/AttendancePage";
import { AuthProvider, useAuth } from "./auth/AuthContext";

type UnauthenticatedView = "staff-login" | "teacher-login" | "password-reset";

/**
 * No routing library exists in this project yet (a deliberate scope decision,
 * plan.md); a small state-based switch is enough for the two views Identity &
 * Access adds. A real router can replace this once more modules need routes.
 */
function UnauthenticatedApp() {
  const [view, setView] = useState<UnauthenticatedView>("staff-login");

  return (
    <div>
      <nav
        style={{
          display: "flex",
          gap: "1rem",
          justifyContent: "center",
          padding: "1rem",
        }}
      >
        <button onClick={() => setView("staff-login")}>Staff login</button>
        <button onClick={() => setView("teacher-login")}>Teacher login</button>
        <button onClick={() => setView("password-reset")}>
          Forgot password?
        </button>
      </nav>
      {view === "staff-login" && <LoginPage />}
      {view === "teacher-login" && <TeacherOtpLoginPage />}
      {view === "password-reset" && <PasswordResetPage />}
    </div>
  );
}

type AuthenticatedView =
  | "sessions"
  | "assignments"
  | "audit-history"
  | "zones"
  | "teacher-profiles"
  | "my-profile"
  | "my-attendance"
  | "attendance";

function AuthenticatedApp() {
  const { logout } = useAuth();
  const [view, setView] = useState<AuthenticatedView>("assignments");
  return (
    <div>
      <nav
        style={{
          display: "flex",
          gap: "1rem",
          justifyContent: "center",
          padding: "1rem",
        }}
      >
        <button onClick={() => setView("assignments")}>Assignments</button>
        <button onClick={() => setView("zones")}>Zones</button>
        <button onClick={() => setView("teacher-profiles")}>
          Teacher Profiles
        </button>
        <button onClick={() => setView("my-profile")}>My Profile</button>
        <button onClick={() => setView("my-attendance")}>My Attendance</button>
        <button onClick={() => setView("attendance")}>Attendance</button>
        <button onClick={() => setView("audit-history")}>Audit History</button>
        <button onClick={() => setView("sessions")}>Sessions</button>
        <button onClick={() => logout()}>Sign out</button>
      </nav>
      {view === "assignments" && <AssignmentsPage />}
      {view === "zones" && <ZonesPage />}
      {view === "teacher-profiles" && <TeacherProfilesPage />}
      {view === "my-profile" && <MyProfilePage />}
      {view === "my-attendance" && <MyAttendancePage />}
      {view === "attendance" && <AttendancePage />}
      {view === "audit-history" && <AuditHistoryPage />}
      {view === "sessions" && <SessionsPage />}
    </div>
  );
}

/**
 * spec 001 FR-001: the status page has no auth guard and must stay reachable
 * regardless of login state — it does not move inside the authenticated branch.
 */
function AppShell() {
  const { isAuthenticated } = useAuth();
  return (
    <div>
      <StatusPage />
      {isAuthenticated ? <AuthenticatedApp /> : <UnauthenticatedApp />}
    </div>
  );
}

export function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  );
}
