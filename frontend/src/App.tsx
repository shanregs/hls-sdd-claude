import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/useAuth";
import { SignInPage } from "./auth/SignInPage";
import { ForgotPasswordPage } from "./auth/ForgotPasswordPage";
import { ProfilePage } from "./account/ProfilePage";
import { AttendanceHistoryPage } from "./features/attendance/AttendanceHistoryPage";
import { HolidayCalendarPage } from "./features/attendance/HolidayCalendarPage";
import { SessionsPage } from "./account/SessionsPage";
import { AllSessionsPage } from "./features/sessions/AllSessionsPage";
import { SettingsPage } from "./account/SettingsPage";
import { AppShell } from "./app/AppShell";
import { RouteGuard } from "./app/RouteGuard";
import { NotAuthorizedPage } from "./app/NotAuthorizedPage";
import { DashboardRouter } from "./dashboards/DashboardRouter";
import { RolePermissionsGrid } from "./features/permissions/RolePermissionsGrid";
import { UserManagementPage } from "./features/users/UserManagementPage";
import { ManagersPage } from "./features/managers/ManagersPage";
import { SchoolsPage } from "./features/schools/SchoolsPage";
import { TeachersPage } from "./features/teachers/TeachersPage";
import { ZonesPage } from "./features/zones/ZonesPage";
import { LoginHistoryPage } from "./features/audit/LoginHistoryPage";
import { ChangeHistoryPage } from "./features/audit/ChangeHistoryPage";
import { UserActivityPage } from "./features/audit/UserActivityPage";
import { AuditLogsPage } from "./features/audit/AuditLogsPage";
import { ApiAccessPage } from "./features/audit/ApiAccessPage";
import { AttendanceSetupPage } from "./features/attendance/AttendanceSetupPage";
import { MyAttendancePage } from "./features/attendance/MyAttendancePage";
import { TeacherAttendancePage } from "./features/attendance/TeacherAttendancePage";
import { AttendanceGridPage } from "./features/attendance/AttendanceGridPage";

/**
 * The real client-side router (spec 002, FR-010/FR-011): every authenticated screen renders
 * inside {@link AppShell} and behind {@link RouteGuard}, which checks the current access model
 * before mounting the target screen — nothing route-guarded ever fetches its data when
 * unauthorized. The root route always redirects to `/dashboard` (contracts/access-model-api.md);
 * there is no role-selection step anywhere in this router.
 */
export function App() {
  const { user, initializing } = useAuth();

  if (initializing) {
    return null;
  }

  return (
    <Routes>
      <Route
        path="/sign-in"
        element={user ? <Navigate to="/" replace /> : <SignInPage />}
      />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />

      <Route element={<AppShell />}>
        <Route
          path="/dashboard"
          element={
            <RouteGuard>
              <DashboardRouter />
            </RouteGuard>
          }
        />
        <Route
          path="/account/profile"
          element={
            <RouteGuard>
              <ProfilePage />
            </RouteGuard>
          }
        />
        <Route
          path="/master-data/zones"
          element={
            <RouteGuard>
              <ZonesPage />
            </RouteGuard>
          }
        />
        <Route
          path="/master-data/managers"
          element={
            <RouteGuard>
              <ManagersPage />
            </RouteGuard>
          }
        />
        <Route
          path="/master-data/teachers"
          element={
            <RouteGuard>
              <TeachersPage />
            </RouteGuard>
          }
        />
        <Route
          path="/master-data/schools"
          element={
            <RouteGuard>
              <SchoolsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/identity/users"
          element={
            <RouteGuard>
              <UserManagementPage />
            </RouteGuard>
          }
        />
        <Route
          path="/identity/permissions"
          element={
            <RouteGuard>
              <RolePermissionsGrid />
            </RouteGuard>
          }
        />
        <Route
          path="/audit/logs"
          element={
            <RouteGuard>
              <AuditLogsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/audit/login-history"
          element={
            <RouteGuard>
              <LoginHistoryPage />
            </RouteGuard>
          }
        />
        <Route
          path="/audit/change-history"
          element={
            <RouteGuard>
              <ChangeHistoryPage />
            </RouteGuard>
          }
        />
        <Route
          path="/audit/api-access"
          element={
            <RouteGuard>
              <ApiAccessPage />
            </RouteGuard>
          }
        />
        <Route
          path="/audit/user-activity"
          element={
            <RouteGuard>
              <UserActivityPage />
            </RouteGuard>
          }
        />
        <Route
          path="/master-data/attendance-setup"
          element={
            <RouteGuard>
              <AttendanceSetupPage />
            </RouteGuard>
          }
        />
        <Route
          path="/master-data/holiday-calendar"
          element={
            <RouteGuard>
              <HolidayCalendarPage />
            </RouteGuard>
          }
        />
        <Route
          path="/my-attendance"
          element={
            <RouteGuard>
              <MyAttendancePage />
            </RouteGuard>
          }
        />
        <Route
          path="/my-attendance/history"
          element={
            <RouteGuard>
              <AttendanceHistoryPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/teacher-attendance"
          element={
            <RouteGuard>
              <TeacherAttendancePage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/attendance"
          element={
            <RouteGuard>
              <AttendanceGridPage />
            </RouteGuard>
          }
        />
        <Route
          path="/account/settings"
          element={
            <RouteGuard>
              <SettingsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/account/sessions"
          element={
            <RouteGuard>
              <SessionsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/identity/sessions"
          element={
            <RouteGuard>
              <AllSessionsPage />
            </RouteGuard>
          }
        />
        <Route path="/not-authorized" element={<NotAuthorizedPage />} />
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
