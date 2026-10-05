package com.hls.notification.api;

/** What a notification is about (spec 010 data-model.md). */
public enum NotificationType {
    LEAVE_DECIDED,
    LEAVE_REQUESTED,
    LEAVE_CANCELLED,
    ATTENDANCE_CHANGED,
    ATTENDANCE_MONTH_LOCKED,
    ATTENDANCE_MONTH_REOPENED,
    MOU_NOT_RECORDED
}
