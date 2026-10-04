package com.hls.identity.accessmodel;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The versioned, code-defined catalog of every screen that could appear in the navigation
 * (research.md §5) — not a database table. {@link AccessModelService} filters this by the
 * permission matrix at request time. Later specs append their own entries here; this shape does
 * not change when they do.
 *
 * <p>Some capabilities (Dashboard, Role &amp; Permissions, Profile) render under a different
 * section label, or a different item label, depending on which of the caller's roles it applies
 * to (Constitution Principle IV) — modeled as separate entries sharing the same
 * module/action/route rather than a single entry with per-role text.
 */
public final class NavigationCatalog {

    public static final List<NavItem> ITEMS = List.of(
            new NavItem(
                    "Dashboard",
                    "/dashboard",
                    PermissionModule.DASHBOARD,
                    PermissionAction.VIEW,
                    "Dashboard",
                    10,
                    EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER)),
            new NavItem(
                    "Dashboard",
                    "/dashboard",
                    PermissionModule.DASHBOARD,
                    PermissionAction.VIEW,
                    "SYSTEM DASHBOARD",
                    10,
                    EnumSet.of(Role.SYSTEM)),
            new NavItem(
                    "User Management",
                    "/identity/users",
                    PermissionModule.USER_MANAGEMENT,
                    PermissionAction.VIEW,
                    "SYSTEM",
                    15,
                    EnumSet.of(Role.ADMIN)),
            new NavItem(
                    "User Management",
                    "/identity/users",
                    PermissionModule.USER_MANAGEMENT,
                    PermissionAction.VIEW,
                    "SYSTEM CONFIGURATION",
                    15,
                    EnumSet.of(Role.SYSTEM)),
            new NavItem(
                    "Role & Permissions",
                    "/identity/permissions",
                    PermissionModule.IDENTITY_PERMISSIONS,
                    PermissionAction.VIEW,
                    "SYSTEM",
                    20,
                    EnumSet.of(Role.ADMIN, Role.DIRECTOR)),
            new NavItem(
                    "Role & Permissions",
                    "/identity/permissions",
                    PermissionModule.IDENTITY_PERMISSIONS,
                    PermissionAction.VIEW,
                    "SYSTEM CONFIGURATION",
                    20,
                    EnumSet.of(Role.SYSTEM)),
            new NavItem(
                    "Zones",
                    "/master-data/zones",
                    PermissionModule.ZONES,
                    PermissionAction.VIEW,
                    "MASTER DATA",
                    40,
                    EnumSet.of(Role.ADMIN, Role.DIRECTOR)),
            new NavItem(
                    "Schools",
                    "/master-data/schools",
                    PermissionModule.SCHOOLS,
                    PermissionAction.VIEW,
                    "MASTER DATA",
                    41,
                    EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)),
            new NavItem(
                    "Managers",
                    "/master-data/managers",
                    PermissionModule.MANAGERS,
                    PermissionAction.VIEW,
                    "MASTER DATA",
                    42,
                    EnumSet.of(Role.ADMIN, Role.DIRECTOR)),
            new NavItem(
                    "Teachers",
                    "/master-data/teachers",
                    PermissionModule.TEACHERS,
                    PermissionAction.VIEW,
                    "MASTER DATA",
                    43,
                    EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)),
            new NavItem(
                    "Holiday Calendar",
                    "/master-data/holiday-calendar",
                    PermissionModule.HOLIDAY_CALENDAR,
                    PermissionAction.VIEW,
                    "MASTER DATA",
                    44,
                    EnumSet.allOf(Role.class)),
            new NavItem(
                    "Attendance",
                    "/operations/attendance",
                    PermissionModule.ATTENDANCE,
                    PermissionAction.VIEW,
                    "OPERATIONS",
                    50,
                    EnumSet.of(Role.ADMIN, Role.DIRECTOR)),
            new NavItem(
                    "Attendance Setup",
                    "/master-data/attendance-setup",
                    PermissionModule.ATTENDANCE_SETUP,
                    PermissionAction.VIEW,
                    "MASTER DATA",
                    45,
                    EnumSet.of(Role.ADMIN, Role.DIRECTOR)),
            new NavItem(
                    "Teacher Attendance",
                    "/operations/teacher-attendance",
                    PermissionModule.TEACHER_ATTENDANCE,
                    PermissionAction.VIEW,
                    "OPERATIONS",
                    52,
                    EnumSet.of(Role.MANAGER)),
            new NavItem(
                    "My Attendance",
                    "/my-attendance",
                    PermissionModule.MY_ATTENDANCE,
                    PermissionAction.VIEW,
                    "MY ATTENDANCE",
                    60,
                    EnumSet.of(Role.TEACHER)),
            new NavItem(
                    "Attendance History",
                    "/my-attendance/history",
                    PermissionModule.MY_ATTENDANCE,
                    PermissionAction.VIEW,
                    "MY ATTENDANCE",
                    61,
                    EnumSet.of(Role.TEACHER)),
            new NavItem(
                    "Audit Logs",
                    "/audit/logs",
                    PermissionModule.AUDIT_LOGS,
                    PermissionAction.VIEW,
                    "AUDIT",
                    30,
                    EnumSet.of(Role.ADMIN, Role.SYSTEM)),
            new NavItem(
                    "Login History",
                    "/audit/login-history",
                    PermissionModule.AUDIT_LOGIN_HISTORY,
                    PermissionAction.VIEW,
                    "AUDIT",
                    31,
                    EnumSet.of(Role.ADMIN, Role.SYSTEM)),
            new NavItem(
                    "Change History",
                    "/audit/change-history",
                    PermissionModule.AUDIT_CHANGE_HISTORY,
                    PermissionAction.VIEW,
                    "AUDIT",
                    32,
                    EnumSet.of(Role.ADMIN, Role.SYSTEM)),
            new NavItem(
                    "User Activity",
                    "/audit/user-activity",
                    PermissionModule.AUDIT_USER_ACTIVITY,
                    PermissionAction.VIEW,
                    "AUDIT",
                    33,
                    EnumSet.of(Role.ADMIN, Role.SYSTEM)),
            new NavItem(
                    "API Access",
                    "/audit/api-access",
                    PermissionModule.AUDIT_API_ACCESS,
                    PermissionAction.VIEW,
                    "AUDIT",
                    34,
                    EnumSet.of(Role.ADMIN, Role.SYSTEM)),
            new NavItem(
                    "Profile",
                    "/account/profile",
                    PermissionModule.ACCOUNT_PROFILE,
                    PermissionAction.VIEW,
                    "ACCOUNT",
                    90,
                    EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.SYSTEM)),
            new NavItem(
                    "My Profile",
                    "/account/profile",
                    PermissionModule.ACCOUNT_PROFILE,
                    PermissionAction.VIEW,
                    "ACCOUNT",
                    90,
                    EnumSet.of(Role.TEACHER)),
            new NavItem(
                    "Settings",
                    "/account/settings",
                    PermissionModule.ACCOUNT_PROFILE,
                    PermissionAction.VIEW,
                    "ACCOUNT",
                    91,
                    EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER, Role.SYSTEM)));

    private NavigationCatalog() {}

    public record NavItem(
            String label,
            String route,
            PermissionModule module,
            PermissionAction action,
            String section,
            int order,
            Set<Role> applicableRoles) {}
}
