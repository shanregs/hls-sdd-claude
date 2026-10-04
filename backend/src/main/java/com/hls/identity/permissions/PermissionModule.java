package com.hls.identity.permissions;

import static com.hls.identity.permissions.PermissionAction.CREATE;
import static com.hls.identity.permissions.PermissionAction.DELETE;
import static com.hls.identity.permissions.PermissionAction.EDIT;
import static com.hls.identity.permissions.PermissionAction.EXPORT;
import static com.hls.identity.permissions.PermissionAction.PROCESS;
import static com.hls.identity.permissions.PermissionAction.VIEW;

import java.util.EnumSet;
import java.util.Set;

/**
 * The modules the permission matrix currently covers (data-model.md's "Permission Matrix Entry").
 * Additive only — later specs add new constants here as they add screens; no schema change is
 * needed since the column is stored as text (research.md §5).
 *
 * <p>Each module also declares the actions that make sense for it. The Role &amp; Permissions screen
 * shows exactly those as icons (granted or not), and a grant for any other action is refused
 * (spec 002 FR-005, FR-006).
 */
public enum PermissionModule {
    DASHBOARD(VIEW),
    ACCOUNT_PROFILE(VIEW, EDIT),
    IDENTITY_PERMISSIONS(VIEW, EDIT),
    AUDIT_LOGS(VIEW, EXPORT),
    AUDIT_LOGIN_HISTORY(VIEW, EXPORT),
    AUDIT_CHANGE_HISTORY(VIEW, EXPORT),
    AUDIT_USER_ACTIVITY(VIEW, EXPORT),
    AUDIT_API_ACCESS(VIEW, EXPORT),
    USER_MANAGEMENT(VIEW, CREATE, EDIT),
    SESSION_MANAGEMENT(VIEW, DELETE),
    ZONES(VIEW, CREATE, EDIT, DELETE),
    SCHOOLS(VIEW, CREATE, EDIT, DELETE),
    MANAGERS(VIEW, CREATE, EDIT),
    TEACHERS(VIEW, CREATE, EDIT),
    TEACHER_SALARY(VIEW, CREATE),
    ATTENDANCE(VIEW, CREATE, EDIT, DELETE, PROCESS, EXPORT),
    TEACHER_ATTENDANCE(VIEW, CREATE, EDIT),
    MY_ATTENDANCE(VIEW, CREATE, EDIT),
    ATTENDANCE_SETUP(VIEW, EDIT),
    HOLIDAY_CALENDAR(VIEW, EDIT);

    private final Set<PermissionAction> actions;

    PermissionModule(PermissionAction first, PermissionAction... rest) {
        this.actions = EnumSet.of(first, rest);
    }

    /** The actions that apply to this module, in the fixed {@link PermissionAction} order. */
    public Set<PermissionAction> actions() {
        return EnumSet.copyOf(actions);
    }
}
