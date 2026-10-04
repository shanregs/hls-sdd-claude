package com.hls.identity.permissions;

import com.hls.identity.user.Role;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
            PermissionModule.AUDIT_USER_ACTIVITY);

    private final PermissionMatrixRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PermissionMatrixService(
            PermissionMatrixRepository repository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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
        for (Role role : USER_MANAGER_ROLES) {
            seed(role, PermissionModule.USER_MANAGEMENT, PermissionAction.VIEW, true);
            seed(role, PermissionModule.USER_MANAGEMENT, PermissionAction.CREATE, true);
            seed(role, PermissionModule.USER_MANAGEMENT, PermissionAction.EDIT, true);
        }
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

    private void seed(Role role, PermissionModule module, PermissionAction action, boolean granted) {
        if (repository.findByRoleAndModuleAndAction(role, module, action).isEmpty()) {
            repository.save(new PermissionMatrixEntry(role, module, action, granted, clock.instant()));
        }
    }

    public boolean isGranted(Role role, PermissionModule module, PermissionAction action) {
        return repository
                .findByRoleAndModuleAndAction(role, module, action)
                .map(PermissionMatrixEntry::isGranted)
                .orElse(false);
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
