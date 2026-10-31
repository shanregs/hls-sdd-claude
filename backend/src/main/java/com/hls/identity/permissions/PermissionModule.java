package com.hls.identity.permissions;

import static com.hls.identity.permissions.PermissionAction.APPROVE;
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
    MY_SESSIONS(VIEW, DELETE),
    SESSION_MANAGEMENT(VIEW, DELETE),
    ZONES(VIEW, CREATE, EDIT, DELETE),
    SCHOOLS(VIEW, CREATE, EDIT, DELETE),
    MANAGERS(VIEW, CREATE, EDIT),
    DESIGNATIONS(VIEW, CREATE, EDIT),
    TEACHERS(VIEW, CREATE, EDIT),
    TEACHER_SALARY(VIEW, CREATE),
    ATTENDANCE(VIEW, CREATE, EDIT, DELETE, PROCESS, EXPORT),
    TEACHER_ATTENDANCE(VIEW, CREATE, EDIT),
    MY_ATTENDANCE(VIEW, CREATE, EDIT),
    ATTENDANCE_SETUP(VIEW, EDIT),
    HOLIDAY_CALENDAR(VIEW, EDIT),
    LEAVE_MANAGEMENT(VIEW, APPROVE),
    MY_LEAVE(VIEW, CREATE, DELETE),
    SUBSTITUTION(VIEW, EDIT),
    MY_SUBSTITUTIONS(VIEW),
    NOTIFICATIONS(VIEW, DELETE),
    SCHOOL_CONTRACTS(VIEW, CREATE, EDIT, APPROVE),
    RECRUITMENT(VIEW, CREATE, EDIT),
    OFFERS(VIEW, CREATE, EDIT),
    INDUCTION(VIEW, CREATE, EDIT),
    MARKETING(VIEW, CREATE, EDIT, APPROVE),
    MARKETING_SETTINGS(VIEW, EDIT),
    SALARY_STRUCTURES(VIEW, CREATE),
    SYSTEM_DASHBOARD(VIEW),
    SYSTEM_SETTINGS(VIEW, EDIT),
    SECURITY_SETTINGS(VIEW, EDIT),
    TASKS(VIEW, CREATE, EDIT, APPROVE),
    MANAGER_ATTENDANCE(VIEW, CREATE, EDIT, PROCESS),
    MANAGER_LEAVE(VIEW, APPROVE),
    MY_MANAGER_ATTENDANCE(VIEW, CREATE, DELETE),
    SCHOOL_WORKING_DAYS(VIEW, CREATE, EDIT, DELETE),
    PAYROLL(VIEW, PROCESS, APPROVE, EXPORT),
    ACTIVITY_CALENDAR(VIEW),
    TEACHER_PERFORMANCE(VIEW, CREATE, EDIT),
    EXPENSES(VIEW, CREATE, EDIT, APPROVE, PROCESS, EXPORT),
    MY_EXPENSES(VIEW, CREATE, EDIT, DELETE),
    TEACHER_TRANSFERS(VIEW, CREATE, EDIT, APPROVE),
    SCHOOL_BILLING(VIEW, CREATE, EDIT, APPROVE, EXPORT),

    TRAINING(VIEW, CREATE, EDIT, DELETE, EXPORT),
    TRAINING_ATTENDANCE(VIEW, EDIT),
    TRAINING_REGISTRATION(VIEW, CREATE, EDIT),
    CONTENT(VIEW, CREATE, EDIT, APPROVE),

    REPORT_ATTENDANCE(VIEW, EXPORT),
    REPORT_PAYROLL(VIEW, EXPORT),
    REPORT_RECRUITMENT(VIEW, EXPORT),
    REPORT_TEACHER(VIEW, EXPORT),
    REPORT_MANAGER(VIEW, EXPORT),
    REPORT_EXPENSES(VIEW, EXPORT),
    REPORT_MARGIN(VIEW, EXPORT),
    REPORT_COLLECTIONS(VIEW, EXPORT),
    REPORT_SCHOOL(VIEW, EXPORT),
    MY_REPORT(VIEW);

    private final Set<PermissionAction> actions;

    PermissionModule(PermissionAction first, PermissionAction... rest) {
        this.actions = EnumSet.of(first, rest);
    }

    /** The actions that apply to this module, in the fixed {@link PermissionAction} order. */
    public Set<PermissionAction> actions() {
        return EnumSet.copyOf(actions);
    }
}
