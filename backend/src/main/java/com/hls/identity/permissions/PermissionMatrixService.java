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
        seedNotifications();
        seedSchoolContracts();
        // Viewing and ending every user's sessions is a System capability (spec 001 FR-015a).
        seed(Role.SYSTEM, PermissionModule.SESSION_MANAGEMENT, PermissionAction.VIEW, true);
        seed(Role.SYSTEM, PermissionModule.SESSION_MANAGEMENT, PermissionAction.DELETE, true);
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

    /** Notification defaults (spec 010): every business role reads and deletes its own; never System. */
    private void seedNotifications() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER)) {
            seed(role, PermissionModule.NOTIFICATIONS, PermissionAction.VIEW, true);
            seed(role, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE, true);
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
        grants.invalidateAround();
    }

    private void seed(Role role, PermissionModule module, PermissionAction action, boolean granted) {
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
