package com.hls.identity.permissions;

/**
 * The modules the permission matrix currently covers (data-model.md's "Permission Matrix Entry").
 * Additive only — later specs add new constants here as they add screens; no schema change is
 * needed since the column is stored as text (research.md §5).
 */
public enum PermissionModule {
    DASHBOARD,
    ACCOUNT_PROFILE,
    IDENTITY_PERMISSIONS,
    AUDIT_LOGS,
    AUDIT_LOGIN_HISTORY,
    AUDIT_CHANGE_HISTORY,
    AUDIT_USER_ACTIVITY,
    USER_MANAGEMENT,
    ZONES,
    SCHOOLS,
    MANAGERS,
    TEACHERS,
    TEACHER_SALARY,
    ATTENDANCE,
    TEACHER_ATTENDANCE,
    MY_ATTENDANCE,
    ATTENDANCE_SETUP
}
