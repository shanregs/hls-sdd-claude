package com.hls.identity.permissions;

import com.hls.cache.SnapshotCache;
import com.hls.cache.SnapshotCaches;
import com.hls.identity.user.Role;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds and edits the role -> permission matrix (Constitution Principle II, FR-001/FR-004).
 */
@Service
public class PermissionMatrixService {

    /** The roles that may hold the capability to manage the matrix itself (research.md §8). */
    private static final Set<Role> MATRIX_MANAGER_ROLES = Set.of(Role.ADMIN, Role.DIRECTOR, Role.SYSTEM);

    /** Audit screens are Admin/System only by default (spec 003's Role & Permission Impact table). */
    private static final Set<Role> AUDIT_VIEWER_ROLES = Set.of(Role.ADMIN, Role.SYSTEM);

    /** User Management is Admin/System only by default (spec 004's Role & Permission Impact table). */
    private static final Set<Role> USER_MANAGER_ROLES = Set.of(Role.ADMIN, Role.SYSTEM);

    private static final List<PermissionModule> AUDIT_MODULES = List.of(
            PermissionModule.AUDIT_LOGS,
            PermissionModule.AUDIT_LOGIN_HISTORY,
            PermissionModule.AUDIT_CHANGE_HISTORY,
            PermissionModule.AUDIT_USER_ACTIVITY,
            PermissionModule.AUDIT_API_ACCESS);

    /** One cell of the matrix. */
    private record GrantKey(Role role, PermissionModule module, PermissionAction action) {}

    private final PermissionMatrixRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Every grant, read on almost every request (each endpoint check and each menu item), so it is kept in memory.
     * A cell with no row means not granted.
     */
    private final SnapshotCache<Map<GrantKey, Boolean>> grants;

    public PermissionMatrixService(
            PermissionMatrixRepository repository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            SnapshotCaches caches) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.grants = caches.create("permissionMatrix", () -> repository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(
                        e -> new GrantKey(e.getRole(), e.getModule(), e.getAction()),
                        PermissionMatrixEntry::isGranted,
                        (first, second) -> second)));
    }

    /**
     * Idempotently seeds the default grants (FR-001, data-model.md): Dashboard view and
     * Account/Profile view+edit for every role, and the Role & Permissions capability for
     * {@link #MATRIX_MANAGER_ROLES} only. Never overwrites an existing row.
     */
    @Transactional
    public void seedDefaults() {
        for (Role role : Role.values()) {
            seed(role, PermissionModule.DASHBOARD, PermissionAction.VIEW, true);
            seed(role, PermissionModule.ACCOUNT_PROFILE, PermissionAction.VIEW, true);
            seed(role, PermissionModule.ACCOUNT_PROFILE, PermissionAction.EDIT, true);
            // Every role may see and end its own sessions (spec 001 FR-015); Admin can switch it off per role.
            seed(role, PermissionModule.MY_SESSIONS, PermissionAction.VIEW, true);
            seed(role, PermissionModule.MY_SESSIONS, PermissionAction.DELETE, true);
        }
        for (Role role : MATRIX_MANAGER_ROLES) {
            seed(role, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.VIEW, true);
            seed(role, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT, true);
        }
        for (PermissionModule module : AUDIT_MODULES) {
            for (Role role : AUDIT_VIEWER_ROLES) {
                seed(role, module, PermissionAction.VIEW, true);
                seed(role, module, PermissionAction.EXPORT, true);
            }
        }
        seedMasterData();
        seedAttendance();
        seedLeave();
        seedSubstitution();
        seedNotifications();
        seedSchoolContracts();
        seedDesignations();
        seedSalaryStructures();
        seedRecruitment();
        seedMarketing();
        seedTasks();
        seedManagerAttendance();
        seedPayroll();
        seedActivityCalendar();
        seedSchoolWorkingDays();
        seedTeacherPerformance();
        seedExpenses();
        seedTeacherTransfers();
        seedContent();
        seedSchoolBilling();
        seedTraining();
        seedReports();
        // Viewing and ending every user's sessions is a System capability (spec 001 FR-015a).
        seed(Role.SYSTEM, PermissionModule.SESSION_MANAGEMENT, PermissionAction.VIEW, true);
        seed(Role.SYSTEM, PermissionModule.SESSION_MANAGEMENT, PermissionAction.DELETE, true);
        seedSystemSettings();
        for (Role role : USER_MANAGER_ROLES) {
            seed(role, PermissionModule.USER_MANAGEMENT, PermissionAction.VIEW, true);
            seed(role, PermissionModule.USER_MANAGEMENT, PermissionAction.CREATE, true);
            seed(role, PermissionModule.USER_MANAGEMENT, PermissionAction.EDIT, true);
        }
    }

    /**
     * System settings defaults (spec 011): System keeps the dashboard and both settings screens; Admin holds the two
     * settings screens; Director has none by default (Constitution Principle II) but may be granted them.
     */
    private void seedSystemSettings() {
        seed(Role.SYSTEM, PermissionModule.SYSTEM_DASHBOARD, PermissionAction.VIEW, true);
        for (PermissionModule module : List.of(PermissionModule.SYSTEM_SETTINGS, PermissionModule.SECURITY_SETTINGS)) {
            for (Role role : List.of(Role.SYSTEM, Role.ADMIN)) {
                seed(role, module, PermissionAction.VIEW, true);
                seed(role, module, PermissionAction.EDIT, true);
            }
        }
        grants.invalidateAround();
    }

    /** Master-data defaults (spec 005, data-model.md): see the grants table there. */
    private void seedMasterData() {
        for (PermissionAction action : List.of(
                PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT, PermissionAction.DELETE)) {
            seed(Role.ADMIN, PermissionModule.ZONES, action, true);
            seed(Role.ADMIN, PermissionModule.SCHOOLS, action, true);
        }
        for (PermissionAction action :
                List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT)) {
            seed(Role.DIRECTOR, PermissionModule.ZONES, action, true);
            seed(Role.DIRECTOR, PermissionModule.SCHOOLS, action, true);
            for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
                seed(role, PermissionModule.MANAGERS, action, true);
                seed(role, PermissionModule.TEACHERS, action, true);
            }
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            seed(role, PermissionModule.TEACHER_SALARY, PermissionAction.VIEW, true);
            seed(role, PermissionModule.TEACHER_SALARY, PermissionAction.CREATE, true);
        }
        for (PermissionAction action : List.of(PermissionAction.VIEW, PermissionAction.EDIT)) {
            seed(Role.MANAGER, PermissionModule.SCHOOLS, action, true);
            seed(Role.MANAGER, PermissionModule.TEACHERS, action, true);
        }
    }

    /** Attendance defaults (spec 008, Role & Permission Impact table). */
    private void seedAttendance() {
        for (PermissionAction action : List.of(
                PermissionAction.VIEW,
                PermissionAction.CREATE,
                PermissionAction.EDIT,
                PermissionAction.PROCESS,
                PermissionAction.EXPORT)) {
            seed(Role.ADMIN, PermissionModule.ATTENDANCE, action, true);
            seed(Role.DIRECTOR, PermissionModule.ATTENDANCE, action, true);
        }
        seed(Role.ADMIN, PermissionModule.ATTENDANCE, PermissionAction.DELETE, true);
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            seed(role, PermissionModule.ATTENDANCE_SETUP, PermissionAction.VIEW, true);
            seed(role, PermissionModule.ATTENDANCE_SETUP, PermissionAction.EDIT, true);
        }
        for (Role role : Role.values()) {
            seed(role, PermissionModule.HOLIDAY_CALENDAR, PermissionAction.VIEW, true);
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            seed(role, PermissionModule.HOLIDAY_CALENDAR, PermissionAction.EDIT, true);
        }
        for (PermissionAction action :
                List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT)) {
            seed(Role.MANAGER, PermissionModule.TEACHER_ATTENDANCE, action, true);
            seed(Role.TEACHER, PermissionModule.MY_ATTENDANCE, action, true);
        }
    }

    /** Leave defaults (spec 009, Role & Permission Impact table). */
    private void seedLeave() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            seed(role, PermissionModule.LEAVE_MANAGEMENT, PermissionAction.VIEW, true);
            seed(role, PermissionModule.LEAVE_MANAGEMENT, PermissionAction.APPROVE, true);
        }
        for (PermissionAction action :
                List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.DELETE)) {
            seed(Role.TEACHER, PermissionModule.MY_LEAVE, action, true);
        }
    }

    /** Substitution defaults (spec 017a): staff run the workflow, the Teacher reads their own assignments. */
    private void seedSubstitution() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            seed(role, PermissionModule.SUBSTITUTION, PermissionAction.VIEW, true);
            seed(role, PermissionModule.SUBSTITUTION, PermissionAction.EDIT, true);
        }
        seed(Role.TEACHER, PermissionModule.MY_SUBSTITUTIONS, PermissionAction.VIEW, true);
        grants.invalidateAround();
    }

    /** Notification defaults (spec 010): every business role reads and deletes its own; never System. */
    private void seedNotifications() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER, Role.CONTENT_CONTRIBUTOR)) {
            seed(role, PermissionModule.NOTIFICATIONS, PermissionAction.VIEW, true);
            seed(role, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE, true);
        }
        grants.invalidateAround();
    }

    /** Designation defaults (spec 005a): Admin and Director keep the list and the people's employment details. */
    private void seedDesignations() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            for (PermissionAction action :
                    List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT)) {
                seed(role, PermissionModule.DESIGNATIONS, action, true);
            }
        }
        grants.invalidateAround();
    }

    /** Manager task defaults (spec 025): Admin and Director run and verify tasks; a Manager acts on their own. */
    /**
     * Manager attendance defaults (spec 032): Admin holds every action, the Director everything but the lock (Process),
     * both decide Managers' leave; a Manager has their own check-in, month and requests. Teacher and System have none.
     */
    private void seedManagerAttendance() {
        for (PermissionAction action : PermissionModule.MANAGER_ATTENDANCE.actions()) {
            seed(Role.ADMIN, PermissionModule.MANAGER_ATTENDANCE, action, true);
            if (action != PermissionAction.PROCESS) {
                seed(Role.DIRECTOR, PermissionModule.MANAGER_ATTENDANCE, action, true);
            }
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            seed(role, PermissionModule.MANAGER_LEAVE, PermissionAction.VIEW, true);
            seed(role, PermissionModule.MANAGER_LEAVE, PermissionAction.APPROVE, true);
        }
        for (PermissionAction action : PermissionModule.MY_MANAGER_ATTENDANCE.actions()) {
            seed(Role.MANAGER, PermissionModule.MY_MANAGER_ATTENDANCE, action, true);
        }
        grants.invalidateAround();
    }

    /** School working days defaults (spec 037): Admin holds all four actions, the Director everything but Delete. */
    private void seedSchoolWorkingDays() {
        for (PermissionAction action : PermissionModule.SCHOOL_WORKING_DAYS.actions()) {
            seed(Role.ADMIN, PermissionModule.SCHOOL_WORKING_DAYS, action, true);
            if (action != PermissionAction.DELETE) {
                seed(Role.DIRECTOR, PermissionModule.SCHOOL_WORKING_DAYS, action, true);
            }
        }
        grants.invalidateAround();
    }

    /** Teacher performance defaults (spec 026): Admin, Director and Manager view, record and change; nobody approves or deletes. */
    private void seedTeacherPerformance() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            for (PermissionAction action : PermissionModule.TEACHER_PERFORMANCE.actions()) {
                seed(role, PermissionModule.TEACHER_PERFORMANCE, action, true);
            }
        }
        grants.invalidateAround();
    }

    /**
     * Expenses defaults (spec 015): Admin holds every action, the Director everything but Process (recording payment);
     * Admin, Director, Manager and Teacher raise their own claims.
     */
    private void seedExpenses() {
        for (PermissionAction action : PermissionModule.EXPENSES.actions()) {
            seed(Role.ADMIN, PermissionModule.EXPENSES, action, true);
            if (action != PermissionAction.PROCESS) {
                seed(Role.DIRECTOR, PermissionModule.EXPENSES, action, true);
            }
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER)) {
            for (PermissionAction action : PermissionModule.MY_EXPENSES.actions()) {
                seed(role, PermissionModule.MY_EXPENSES, action, true);
            }
        }
        grants.invalidateAround();
    }

    /** Transfers and replacements defaults (spec 027): Admin raises and changes; Director and Manager also approve. */
    private void seedTeacherTransfers() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            for (PermissionAction action : PermissionModule.TEACHER_TRANSFERS.actions()) {
                if (role == Role.ADMIN && action == PermissionAction.APPROVE) {
                    continue;
                }
                seed(role, PermissionModule.TEACHER_TRANSFERS, action, true);
            }
        }
        grants.invalidateAround();
    }

    /** School billing defaults (spec 022): Admin and Director hold everything; a Zone Manager views, exports and records collections. */
    private void seedSchoolBilling() {
        for (PermissionAction action : PermissionModule.SCHOOL_BILLING.actions()) {
            seed(Role.ADMIN, PermissionModule.SCHOOL_BILLING, action, true);
            seed(Role.DIRECTOR, PermissionModule.SCHOOL_BILLING, action, true);
        }
        for (PermissionAction action : List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EXPORT)) {
            seed(Role.MANAGER, PermissionModule.SCHOOL_BILLING, action, true);
        }
        grants.invalidateAround();
    }

    /**
     * Content defaults (spec 029): Admin, Manager and Content Contributor view, create and edit; only the Director
     * approves by default (Admin may be granted Approve in the matrix; it is not seeded).
     */
    private void seedContent() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.CONTENT_CONTRIBUTOR)) {
            for (PermissionAction action :
                    List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT)) {
                seed(role, PermissionModule.CONTENT, action, true);
            }
        }
        seed(Role.DIRECTOR, PermissionModule.CONTENT, PermissionAction.APPROVE, true);
        grants.invalidateAround();
    }

    /**
     * Training calendar defaults (spec 024): Admin and Director run the calendar and record attendance; a Manager sees the
     * calendar and records attendance for their own Teachers; a Teacher registers for open sessions.
     */
    private void seedTraining() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            for (PermissionAction action : PermissionModule.TRAINING.actions()) {
                seed(role, PermissionModule.TRAINING, action, true);
            }
        }
        for (PermissionAction action : PermissionModule.TRAINING_ATTENDANCE.actions()) {
            for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
                seed(role, PermissionModule.TRAINING_ATTENDANCE, action, true);
            }
        }
        seed(Role.MANAGER, PermissionModule.TRAINING, PermissionAction.VIEW, true);
        for (PermissionAction action : PermissionModule.TRAINING_REGISTRATION.actions()) {
            seed(Role.TEACHER, PermissionModule.TRAINING_REGISTRATION, action, true);
        }
        grants.invalidateAround();
    }

    /** Report defaults (spec 014a): Admin and Director read and export both reports, a Manager the Attendance one, a Teacher My Report. */
    private void seedReports() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            for (PermissionAction action : PermissionModule.REPORT_ATTENDANCE.actions()) {
                seed(role, PermissionModule.REPORT_ATTENDANCE, action, true);
            }
            for (PermissionAction action : PermissionModule.REPORT_PAYROLL.actions()) {
                seed(role, PermissionModule.REPORT_PAYROLL, action, true);
            }
        }
        for (PermissionAction action : PermissionModule.REPORT_ATTENDANCE.actions()) {
            seed(Role.MANAGER, PermissionModule.REPORT_ATTENDANCE, action, true);
        }
        seed(Role.TEACHER, PermissionModule.MY_REPORT, PermissionAction.VIEW, true);
        grants.invalidateAround();
    }

    private void seedTasks() {
        for (PermissionAction action : PermissionModule.TASKS.actions()) {
            seed(Role.ADMIN, PermissionModule.TASKS, action, true);
            seed(Role.DIRECTOR, PermissionModule.TASKS, action, true);
        }
        for (PermissionAction action : List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT)) {
            seed(Role.MANAGER, PermissionModule.TASKS, action, true);
        }
        grants.invalidateAround();
    }

    /** Activity calendar defaults (spec 035): Admin, Director and Manager may view; a Manager sees only their own items. */
    private void seedActivityCalendar() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            seed(role, PermissionModule.ACTIVITY_CALENDAR, PermissionAction.VIEW, true);
        }
        grants.invalidateAround();
    }

    /** Payroll run defaults (spec 013b): the Admin processes and exports, the Director approves and exports; both read. */
    private void seedPayroll() {
        seed(Role.ADMIN, PermissionModule.PAYROLL, PermissionAction.VIEW, true);
        seed(Role.ADMIN, PermissionModule.PAYROLL, PermissionAction.PROCESS, true);
        seed(Role.ADMIN, PermissionModule.PAYROLL, PermissionAction.EXPORT, true);
        seed(Role.DIRECTOR, PermissionModule.PAYROLL, PermissionAction.VIEW, true);
        seed(Role.DIRECTOR, PermissionModule.PAYROLL, PermissionAction.APPROVE, true);
        seed(Role.DIRECTOR, PermissionModule.PAYROLL, PermissionAction.EXPORT, true);
        grants.invalidateAround();
    }

    /** Salary structure defaults (spec 013a): Admin and Director read and record; rows are append-only, so no Edit. */
    private void seedSalaryStructures() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            seed(role, PermissionModule.SALARY_STRUCTURES, PermissionAction.VIEW, true);
            seed(role, PermissionModule.SALARY_STRUCTURES, PermissionAction.CREATE, true);
        }
        grants.invalidateAround();
    }

    /** School contract defaults (spec 012): Admin and Director keep the MoU; the Zone Manager reads it. */
    private void seedSchoolContracts() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            for (PermissionAction action :
                    List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT)) {
                seed(role, PermissionModule.SCHOOL_CONTRACTS, action, true);
            }
        }
        seed(Role.MANAGER, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.VIEW, true);
        // MoU approval (spec 031): the Director and the Zone Manager (own Schools) decide; never Admin
        seed(Role.DIRECTOR, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.APPROVE, true);
        seed(Role.MANAGER, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.APPROVE, true);
        grants.invalidateAround();
    }

    /**
     * Recruitment defaults (spec 016): the Director runs it all; Admin runs recruitment and induction and only
     * views offers; the Zone Manager takes part in drives (scoped to own drives in the service) and views offers.
     */
    private void seedRecruitment() {
        List<PermissionAction> full = List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT);
        for (PermissionAction action : full) {
            seed(Role.DIRECTOR, PermissionModule.RECRUITMENT, action, true);
            seed(Role.DIRECTOR, PermissionModule.OFFERS, action, true);
            seed(Role.DIRECTOR, PermissionModule.INDUCTION, action, true);
            seed(Role.ADMIN, PermissionModule.RECRUITMENT, action, true);
            seed(Role.ADMIN, PermissionModule.INDUCTION, action, true);
            seed(Role.MANAGER, PermissionModule.RECRUITMENT, action, true);
        }
        seed(Role.ADMIN, PermissionModule.OFFERS, PermissionAction.VIEW, true);
        seed(Role.MANAGER, PermissionModule.OFFERS, PermissionAction.VIEW, true);
        grants.invalidateAround();
    }

    /**
     * Marketing defaults (spec 023): Admin and Director run prospects, visits and proposals; the Director and the
     * Zone Manager (scoped to their Zones in the service) also review a Final Stage prospect; the settings screen is
     * for Admin and Director.
     */
    private void seedMarketing() {
        List<PermissionAction> write = List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT);
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            for (PermissionAction action : write) {
                seed(role, PermissionModule.MARKETING, action, true);
            }
        }
        seed(Role.DIRECTOR, PermissionModule.MARKETING, PermissionAction.APPROVE, true);
        seed(Role.MANAGER, PermissionModule.MARKETING, PermissionAction.APPROVE, true);
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            seed(role, PermissionModule.MARKETING_SETTINGS, PermissionAction.VIEW, true);
            seed(role, PermissionModule.MARKETING_SETTINGS, PermissionAction.EDIT, true);
        }
        grants.invalidateAround();
    }

    private void seed(Role role, PermissionModule module, PermissionAction action, boolean granted) {
        // The Content Contributor can never hold anything outside its eligible set (spec 029), so nothing else is seeded.
        if (role == Role.CONTENT_CONTRIBUTOR && !PermissionEligibility.isEligible(role, module, action)) {
            return;
        }
        if (repository.findByRoleAndModuleAndAction(role, module, action).isEmpty()) {
            repository.save(new PermissionMatrixEntry(role, module, action, granted, clock.instant()));
        }
    }

    public boolean isGranted(Role role, PermissionModule module, PermissionAction action) {
        return grants.get().getOrDefault(new GrantKey(role, module, action), false);
    }

    public List<PermissionMatrixEntry> findAll() {
        return repository.findAll();
    }

    /**
     * Sets one grant, rejecting outright (no partial change) if the grant does not apply to the role
     * and module ({@link PermissionEligibility}) or if doing so would leave no
     * {@link #MATRIX_MANAGER_ROLES} role still able to manage the matrix (FR-004, research.md §8).
     */
    @Transactional
    public UpdateResult updateGrant(
            Role role, PermissionModule module, PermissionAction action, boolean granted, UUID actorUserId) {
        if (!PermissionEligibility.isEligible(role, module, action)) {
            return UpdateResult.rejected("That permission does not apply to this role and module.");
        }
        if (isLastMatrixManagerRemoval(role, module, action, granted)) {
            return UpdateResult.rejected("This would leave no one able to manage the permission matrix.");
        }

        PermissionMatrixEntry entry = repository
                .findByRoleAndModuleAndAction(role, module, action)
                .orElseGet(() -> new PermissionMatrixEntry(role, module, action, false, clock.instant()));
        boolean before = entry.isGranted();
        entry.setGranted(granted);
        entry.setUpdatedBy(actorUserId);
        entry.setUpdatedAt(clock.instant());
        PermissionMatrixEntry saved = repository.save(entry);
        grants.invalidateAround();

        eventPublisher.publishEvent(new PermissionMatrixChanged(
                UUID.randomUUID(), actorUserId, clock.instant(), role, module, action, before, granted));
        return UpdateResult.success(saved);
    }

    private boolean isLastMatrixManagerRemoval(
            Role role, PermissionModule module, PermissionAction action, boolean granted) {
        if (granted || module != PermissionModule.IDENTITY_PERMISSIONS || action != PermissionAction.EDIT) {
            return false;
        }
        return MATRIX_MANAGER_ROLES.stream()
                .filter(r -> r != role)
                .noneMatch(r -> isGranted(r, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT));
    }

    public record UpdateResult(boolean success, PermissionMatrixEntry entry, String rejectionReason) {
        static UpdateResult success(PermissionMatrixEntry entry) {
            return new UpdateResult(true, entry, null);
        }

        static UpdateResult rejected(String reason) {
            return new UpdateResult(false, null, reason);
        }
    }
}
