package com.hls.identity.permissions;

import com.hls.identity.user.Role;
import java.util.EnumSet;
import java.util.Map;
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
    static final Set<PermissionModule> MATRIX_MANAGER_ONLY_MODULES = EnumSet.of(
            PermissionModule.IDENTITY_PERMISSIONS,
            PermissionModule.SESSION_MANAGEMENT,
            PermissionModule.SYSTEM_DASHBOARD,
            PermissionModule.SYSTEM_SETTINGS,
            PermissionModule.SECURITY_SETTINGS);

    /** Teacher and school business data, which System must never see (Constitution Principle III). */
    static final Set<PermissionModule> BUSINESS_MODULES = EnumSet.of(
            PermissionModule.ZONES,
            PermissionModule.SCHOOLS,
            PermissionModule.MANAGERS,
            PermissionModule.DESIGNATIONS,
            PermissionModule.TEACHERS,
            PermissionModule.TEACHER_SALARY,
            PermissionModule.ATTENDANCE,
            PermissionModule.TEACHER_ATTENDANCE,
            PermissionModule.MY_ATTENDANCE,
            PermissionModule.ATTENDANCE_SETUP,
            PermissionModule.LEAVE_MANAGEMENT,
            PermissionModule.MY_LEAVE,
            PermissionModule.SUBSTITUTION,
            PermissionModule.MY_SUBSTITUTIONS,
            PermissionModule.NOTIFICATIONS,
            PermissionModule.SCHOOL_CONTRACTS,
            PermissionModule.RECRUITMENT,
            PermissionModule.OFFERS,
            PermissionModule.INDUCTION,
            PermissionModule.MARKETING,
            PermissionModule.MARKETING_SETTINGS,
            PermissionModule.SALARY_STRUCTURES,
            PermissionModule.TASKS,
            PermissionModule.MANAGER_ATTENDANCE,
            PermissionModule.MANAGER_LEAVE,
            PermissionModule.MY_MANAGER_ATTENDANCE,
            PermissionModule.SCHOOL_WORKING_DAYS,
            PermissionModule.PAYROLL);

    /**
     * Leave (spec 009): supervisors decide requests, only Teachers have requests of their own. School
     * contracts (spec 012): staff roles only; a Teacher never reaches the School's MoU.
     */
    static final Map<PermissionModule, Set<Role>> ROLE_RESTRICTED_MODULES = Map.of(
            PermissionModule.LEAVE_MANAGEMENT, EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER),
            PermissionModule.MY_LEAVE, EnumSet.of(Role.TEACHER),
            PermissionModule.SCHOOL_CONTRACTS, EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER),
            PermissionModule.DESIGNATIONS, EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER),
            PermissionModule.RECRUITMENT, EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER),
            PermissionModule.OFFERS, EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER),
            PermissionModule.INDUCTION, EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER),
            PermissionModule.MARKETING, EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER),
            PermissionModule.MARKETING_SETTINGS, EnumSet.of(Role.ADMIN, Role.DIRECTOR),
            PermissionModule.SALARY_STRUCTURES, EnumSet.of(Role.ADMIN, Role.DIRECTOR));

    /** Substitution (spec 017a): staff roles run it, only a Teacher has "my" view of it. Kept apart from the map above. */
    static final Map<PermissionModule, Set<Role>> SUBSTITUTION_MODULES = Map.of(
            PermissionModule.SUBSTITUTION, EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER),
            PermissionModule.MY_SUBSTITUTIONS, EnumSet.of(Role.TEACHER));

    /**
     * Manager attendance (spec 032): the grid, corrections, lock and leave decisions are for Admin and Director; a
     * Manager has only their own check-in, month and requests. Kept apart from the maps above (Map.of holds ten pairs).
     */
    static final Map<PermissionModule, Set<Role>> MANAGER_ATTENDANCE_MODULES = Map.of(
            PermissionModule.MANAGER_ATTENDANCE, EnumSet.of(Role.ADMIN, Role.DIRECTOR),
            PermissionModule.MANAGER_LEAVE, EnumSet.of(Role.ADMIN, Role.DIRECTOR),
            PermissionModule.MY_MANAGER_ATTENDANCE, EnumSet.of(Role.MANAGER));

    /**
     * School working days (spec 037): Admin and Director maintain the monthly figures; a Manager may be granted View only
     * (limited to their Schools). Kept apart from the maps above (they are full).
     */
    static final Map<PermissionModule, Set<Role>> SCHOOL_WORKING_DAYS_MODULES = Map.of(
            PermissionModule.SCHOOL_WORKING_DAYS, EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER));

    /** Payroll run (spec 013b): pay is for Admin and Director only; no other role can be granted any of it. */
    static final Map<PermissionModule, Set<Role>> PAYROLL_MODULES =
            Map.of(PermissionModule.PAYROLL, EnumSet.of(Role.ADMIN, Role.DIRECTOR));

    private PermissionEligibility() {}

    /** The actions of {@code module} that may be granted to {@code role}; empty when none apply. */
    public static Set<PermissionAction> actionsFor(Role role, PermissionModule module) {
        if (MATRIX_MANAGER_ONLY_MODULES.contains(module) && !MATRIX_MANAGER_ROLES.contains(role)) {
            return EnumSet.noneOf(PermissionAction.class);
        }
        Set<Role> only = ROLE_RESTRICTED_MODULES.get(module);
        if (only != null && !only.contains(role)) {
            return EnumSet.noneOf(PermissionAction.class);
        }
        Set<Role> substitutionOnly = SUBSTITUTION_MODULES.get(module);
        if (substitutionOnly != null && !substitutionOnly.contains(role)) {
            return EnumSet.noneOf(PermissionAction.class);
        }
        Set<Role> managerAttendanceOnly = MANAGER_ATTENDANCE_MODULES.get(module);
        if (managerAttendanceOnly != null && !managerAttendanceOnly.contains(role)) {
            return EnumSet.noneOf(PermissionAction.class);
        }
        Set<Role> workingDaysOnly = SCHOOL_WORKING_DAYS_MODULES.get(module);
        if (workingDaysOnly != null && !workingDaysOnly.contains(role)) {
            return EnumSet.noneOf(PermissionAction.class);
        }
        Set<Role> payrollOnly = PAYROLL_MODULES.get(module);
        if (payrollOnly != null && !payrollOnly.contains(role)) {
            return EnumSet.noneOf(PermissionAction.class);
        }
        if (role == Role.SYSTEM && BUSINESS_MODULES.contains(module)) {
            return EnumSet.noneOf(PermissionAction.class);
        }
        // Manager tasks (spec 025): staff roles only; a Manager acts on their own tasks and never approves.
        if (module == PermissionModule.TASKS) {
            if (role == Role.MANAGER) {
                return EnumSet.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT);
            }
            if (role != Role.ADMIN && role != Role.DIRECTOR) {
                return EnumSet.noneOf(PermissionAction.class);
            }
        }
        // A Manager may be granted View of the School working days, never to change them (spec 037).
        if (role == Role.MANAGER && module == PermissionModule.SCHOOL_WORKING_DAYS) {
            return EnumSet.of(PermissionAction.VIEW);
        }
        // A Zone Manager may be allowed to read the designation list, never to change it (spec 005a FR-011).
        if (role == Role.MANAGER && module == PermissionModule.DESIGNATIONS) {
            return EnumSet.of(PermissionAction.VIEW);
        }
        return module.actions();
    }

    public static boolean isEligible(Role role, PermissionModule module, PermissionAction action) {
        return actionsFor(role, module).contains(action);
    }
}
