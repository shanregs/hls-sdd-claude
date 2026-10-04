package com.hls.identity.permissions;

import com.hls.identity.user.Role;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which grants can exist for a role on a module (spec 002 FR-005): the module's own actions, minus
 * the combinations the Constitution rules out. An eligible grant can be switched on or off in the
 * matrix; an ineligible one is shown as "not applicable" and refused by the server.
 */
public final class PermissionEligibility {

    /** Only these roles may hold the capabilities in {@link #MATRIX_MANAGER_ONLY_MODULES} (Constitution Principle II). */
    static final Set<Role> MATRIX_MANAGER_ROLES = EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.SYSTEM);

    /** Capabilities only the matrix-manager roles may hold: the matrix itself and every user's sessions. */
    static final Set<PermissionModule> MATRIX_MANAGER_ONLY_MODULES =
            EnumSet.of(PermissionModule.IDENTITY_PERMISSIONS, PermissionModule.SESSION_MANAGEMENT);

    /** Teacher and school business data, which System must never see (Constitution Principle III). */
    static final Set<PermissionModule> BUSINESS_MODULES = EnumSet.of(
            PermissionModule.ZONES,
            PermissionModule.SCHOOLS,
            PermissionModule.MANAGERS,
            PermissionModule.TEACHERS,
            PermissionModule.TEACHER_SALARY,
            PermissionModule.ATTENDANCE,
            PermissionModule.TEACHER_ATTENDANCE,
            PermissionModule.MY_ATTENDANCE,
            PermissionModule.ATTENDANCE_SETUP);

    private PermissionEligibility() {}

    /** The actions of {@code module} that may be granted to {@code role}; empty when none apply. */
    public static Set<PermissionAction> actionsFor(Role role, PermissionModule module) {
        if (MATRIX_MANAGER_ONLY_MODULES.contains(module) && !MATRIX_MANAGER_ROLES.contains(role)) {
            return EnumSet.noneOf(PermissionAction.class);
        }
        if (role == Role.SYSTEM && BUSINESS_MODULES.contains(module)) {
            return EnumSet.noneOf(PermissionAction.class);
        }
        return module.actions();
    }

    public static boolean isEligible(Role role, PermissionModule module, PermissionAction action) {
        return actionsFor(role, module).contains(action);
    }
}
