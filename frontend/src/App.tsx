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
import { DesignationsPage } from "./features/designations/DesignationsPage";
import { SchoolWorkingDaysPage } from "./features/schoolWorkingDays/SchoolWorkingDaysPage";
import { YearEntryPage } from "./features/schoolWorkingDays/YearEntryPage";
import { DesignationSalaryPage } from "./features/payroll/DesignationSalaryPage";
import { PayPolicyPage } from "./features/payroll/PayPolicyPage";
import { PersonSalaryPage } from "./features/payroll/PersonSalaryPage";
import { SalaryStructuresPage } from "./features/payroll/SalaryStructuresPage";
import { AdjustmentsPage } from "./features/payroll/run/AdjustmentsPage";
import { PayrollRunPage } from "./features/payroll/run/PayrollRunPage";
import { PayrollRunsPage } from "./features/payroll/run/PayrollRunsPage";
import { PersonPayrollHistory } from "./features/payroll/run/PersonPayrollHistory";
import { SecuritySettingsPage } from "./features/settings/SecuritySettingsPage";
import { SystemSettingsPage } from "./features/settings/SystemSettingsPage";
import { TransfersPage } from "./features/transfers/TransfersPage";
import { BillingOverviewPage } from "./features/schoolbilling/receivables/BillingOverviewPage";
import { SchoolLedgerPage } from "./features/schoolbilling/receivables/SchoolLedgerPage";
import { FeedbackPage } from "./features/performance/FeedbackPage";
import { IssuePage, IssuesPage } from "./features/performance/IssuePage";
import { ObservationForm } from "./features/performance/ObservationForm";
import { ObservationsPage } from "./features/performance/ObservationsPage";
import { PlannedPage } from "./features/performance/PlannedPage";
import { TeacherPerformancePage } from "./features/performance/TeacherPerformancePage";
import { BudgetVersusActualPage } from "./features/expenses/BudgetVersusActualPage";
import { ClaimDetailPage } from "./features/expenses/ClaimDetailPage";
import { ClaimFormPage } from "./features/expenses/ClaimFormPage";
import { CostMeasuresPage } from "./features/expenses/CostMeasuresPage";
import { ExpensesReviewPage } from "./features/expenses/ExpensesReviewPage";
import { MyExpensesPage } from "./features/expenses/MyExpensesPage";
import { TaskDetailPage } from "./features/tasks/TaskDetailPage";
import { TasksPage } from "./features/tasks/TasksPage";
import { ContentDetailPage } from "./features/content/ContentDetailPage";
import { ContentPage } from "./features/content/ContentPage";
import { ActivityCalendarPage } from "./features/activityCalendar/ActivityCalendarPage";
import { ExceptionsPage } from "./features/training/ExceptionsPage";
import { HistoryPage } from "./features/training/HistoryPage";
import { MyTrainingPage } from "./features/training/MyTrainingPage";
import { TrainingCalendarPage } from "./features/training/TrainingCalendarPage";
import { UpcomingTrainingPage } from "./features/training/UpcomingTrainingPage";
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
import { ManagerAttendanceGridPage } from "./features/managerAttendance/ManagerAttendanceGridPage";
import { ManagerCheckInPage } from "./features/managerAttendance/ManagerCheckInPage";
import { ManagerLeaveDecisionsPage } from "./features/managerAttendance/ManagerLeaveDecisionsPage";
import { MyManagerLeavePage } from "./features/managerAttendance/MyManagerLeavePage";
import { MyManagerMonthPage } from "./features/managerAttendance/MyManagerMonthPage";
import { MyAttendancePage } from "./features/attendance/MyAttendancePage";
import { TeacherAttendancePage } from "./features/attendance/TeacherAttendancePage";
import { AttendanceGridPage } from "./features/attendance/AttendanceGridPage";
import { MySubstitutionsPage } from "./features/substitution/MySubstitutionsPage";
import { SubstitutionDetailPage } from "./features/substitution/SubstitutionDetailPage";
import { SubstitutionsPage } from "./features/substitution/SubstitutionsPage";
import { ApplyLeavePage } from "./features/leave/ApplyLeavePage";
import { MyLeaveHistoryPage } from "./features/leave/MyLeaveHistoryPage";
import { LeaveManagementPage } from "./features/leave/LeaveManagementPage";
import { NotificationsPage } from "./features/notifications/NotificationsPage";
import { ContractsListPage } from "./features/schoolbilling/ContractsListPage";
import { SchoolContractPage } from "./features/schoolbilling/SchoolContractPage";
import { MouApprovalsPage } from "./features/schoolbilling/MouApprovalsPage";
import { CandidatesPage } from "./features/recruitment/CandidatesPage";
import { DriveDetailPage } from "./features/recruitment/DriveDetailPage";
import { DrivesPage } from "./features/recruitment/DrivesPage";
import { InductionPage } from "./features/recruitment/InductionPage";
import { OffersPage } from "./features/recruitment/OffersPage";
import { RecruitmentDashboard } from "./features/recruitment/RecruitmentDashboard";
import { MarketingCalendar } from "./features/marketing/MarketingCalendar";
import { MarketingDashboard } from "./features/marketing/MarketingDashboard";
import { MarketingSettingsPage } from "./features/marketing/MarketingSettingsPage";
import { PipelineBoard } from "./features/marketing/PipelineBoard";
import { ProspectDetailPage } from "./features/marketing/ProspectDetailPage";
import { ProspectsPage } from "./features/marketing/ProspectsPage";

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
          path="/master-data/designations"
          element={
            <RouteGuard>
              <DesignationsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/master-data/school-working-days"
          element={
            <RouteGuard>
              <SchoolWorkingDaysPage />
            </RouteGuard>
          }
        />
        <Route
          path="/master-data/school-working-days/year"
          element={
            <RouteGuard>
              <YearEntryPage />
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
          path="/manager-attendance/me"
          element={
            <RouteGuard>
              <ManagerCheckInPage />
            </RouteGuard>
          }
        />
        <Route
          path="/manager-attendance/me/month"
          element={
            <RouteGuard>
              <MyManagerMonthPage />
            </RouteGuard>
          }
        />
        <Route
          path="/manager-attendance/me/leave"
          element={
            <RouteGuard>
              <MyManagerLeavePage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/manager-leave"
          element={
            <RouteGuard>
              <ManagerLeaveDecisionsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/manager-attendance"
          element={
            <RouteGuard>
              <ManagerAttendanceGridPage />
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
          path="/leave/apply"
          element={
            <RouteGuard>
              <ApplyLeavePage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/substitutions"
          element={
            <RouteGuard>
              <SubstitutionsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/substitutions/:id"
          element={
            <RouteGuard>
              <SubstitutionDetailPage />
            </RouteGuard>
          }
        />
        <Route
          path="/my-substitutions"
          element={
            <RouteGuard>
              <MySubstitutionsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/leave"
          element={
            <RouteGuard>
              <LeaveManagementPage />
            </RouteGuard>
          }
        />
        <Route
          path="/system/settings"
          element={
            <RouteGuard>
              <SystemSettingsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/system/security"
          element={
            <RouteGuard>
              <SecuritySettingsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/school-billing"
          element={
            <RouteGuard>
              <BillingOverviewPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/school-billing/:schoolId"
          element={
            <RouteGuard>
              <SchoolLedgerPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/transfers"
          element={
            <RouteGuard>
              <TransfersPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/teacher-performance"
          element={
            <RouteGuard>
              <ObservationsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/teacher-performance/planned"
          element={
            <RouteGuard>
              <PlannedPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/teacher-performance/feedback"
          element={
            <RouteGuard>
              <FeedbackPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/teacher-performance/issues"
          element={
            <RouteGuard>
              <IssuesPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/teacher-performance/issues/:id"
          element={
            <RouteGuard>
              <IssuePage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/teacher-performance/observations/:id"
          element={
            <RouteGuard>
              <ObservationForm />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/teacher-performance/teachers/:teacherId"
          element={
            <RouteGuard>
              <TeacherPerformancePage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/activity-calendar"
          element={
            <RouteGuard>
              <ActivityCalendarPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/training"
          element={
            <RouteGuard>
              <TrainingCalendarPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/training/exceptions"
          element={
            <RouteGuard>
              <ExceptionsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/training/history/:kind/:id"
          element={
            <RouteGuard>
              <HistoryPage />
            </RouteGuard>
          }
        />
        <Route
          path="/my-attendance/training/history"
          element={
            <RouteGuard>
              <HistoryPage mine />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/my-training"
          element={
            <RouteGuard>
              <MyTrainingPage />
            </RouteGuard>
          }
        />
        <Route
          path="/my-attendance/training"
          element={
            <RouteGuard>
              <UpcomingTrainingPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/my-expenses"
          element={
            <RouteGuard>
              <MyExpensesPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/my-expenses/new"
          element={
            <RouteGuard>
              <ClaimFormPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/my-expenses/:id/edit"
          element={
            <RouteGuard>
              <ClaimFormPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/my-expenses/:id"
          element={
            <RouteGuard>
              <ClaimDetailPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/expenses"
          element={
            <RouteGuard>
              <ExpensesReviewPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/expenses/budgets"
          element={
            <RouteGuard>
              <BudgetVersusActualPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/expenses/costs"
          element={
            <RouteGuard>
              <CostMeasuresPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/expenses/:id"
          element={
            <RouteGuard>
              <ClaimDetailPage review />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/content"
          element={
            <RouteGuard>
              <ContentPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/content/:id"
          element={
            <RouteGuard>
              <ContentDetailPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/tasks"
          element={
            <RouteGuard>
              <TasksPage />
            </RouteGuard>
          }
        />
        <Route
          path="/system/settings"
          element={
            <RouteGuard>
              <SystemSettingsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/tasks/:id"
          element={
            <RouteGuard>
              <TaskDetailPage />
            </RouteGuard>
          }
        />
        <Route
          path="/system/security"
          element={
            <RouteGuard>
              <SecuritySettingsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/salary-structures"
          element={
            <RouteGuard>
              <SalaryStructuresPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/salary-structures/policy"
          element={
            <RouteGuard>
              <PayPolicyPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/salary-structures/people/:kind/:personId"
          element={
            <RouteGuard>
              <PersonSalaryPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/salary-structures/:designationId"
          element={
            <RouteGuard>
              <DesignationSalaryPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/payroll"
          element={
            <RouteGuard>
              <PayrollRunsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/payroll/adjustments"
          element={
            <RouteGuard>
              <AdjustmentsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/payroll/people/:kind/:personId"
          element={
            <RouteGuard>
              <PersonPayrollHistory />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/payroll/:runId"
          element={
            <RouteGuard>
              <PayrollRunPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/school-contracts"
          element={
            <RouteGuard>
              <ContractsListPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/school-contracts/approvals"
          element={
            <RouteGuard>
              <MouApprovalsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/operations/school-contracts/schools/:schoolId"
          element={
            <RouteGuard>
              <SchoolContractPage />
            </RouteGuard>
          }
        />
        <Route
          path="/recruitment/drives"
          element={
            <RouteGuard>
              <DrivesPage />
            </RouteGuard>
          }
        />
        <Route
          path="/recruitment/drives/:id"
          element={
            <RouteGuard>
              <DriveDetailPage />
            </RouteGuard>
          }
        />
        <Route
          path="/recruitment/candidates"
          element={
            <RouteGuard>
              <CandidatesPage />
            </RouteGuard>
          }
        />
        <Route
          path="/recruitment/offers"
          element={
            <RouteGuard>
              <OffersPage />
            </RouteGuard>
          }
        />
        <Route
          path="/recruitment/induction"
          element={
            <RouteGuard>
              <InductionPage />
            </RouteGuard>
          }
        />
        <Route
          path="/recruitment/dashboard"
          element={
            <RouteGuard>
              <RecruitmentDashboard />
            </RouteGuard>
          }
        />
        <Route
          path="/marketing/prospects"
          element={
            <RouteGuard>
              <ProspectsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/marketing/prospects/:id"
          element={
            <RouteGuard>
              <ProspectDetailPage />
            </RouteGuard>
          }
        />
        <Route
          path="/marketing/calendar"
          element={
            <RouteGuard>
              <MarketingCalendar />
            </RouteGuard>
          }
        />
        <Route
          path="/marketing/pipeline"
          element={
            <RouteGuard>
              <PipelineBoard />
            </RouteGuard>
          }
        />
        <Route
          path="/marketing/dashboard"
          element={
            <RouteGuard>
              <MarketingDashboard />
            </RouteGuard>
          }
        />
        <Route
          path="/marketing/settings"
          element={
            <RouteGuard>
              <MarketingSettingsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/leave/history"
          element={
            <RouteGuard>
              <MyLeaveHistoryPage />
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
        <Route
          path="/account/notifications"
          element={
            <RouteGuard>
              <NotificationsPage />
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
