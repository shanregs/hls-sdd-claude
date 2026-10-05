package com.hls.notification.internal;

import com.hls.attendance.api.AttendanceMarkChanged;
import com.hls.attendance.api.AttendanceMonthLocked;
import com.hls.attendance.api.AttendanceMonthReopened;
import com.hls.notification.api.NotificationType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns attendance events into notifications for the Teacher (spec 010). Runs synchronously inside the
 * publisher's transaction; several day changes by one supervisor merge into one notification.
 */
@Component
public class AttendanceEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;

    public AttendanceEventListener(NotificationService notifications, RecipientResolver recipients) {
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @EventListener
    void onMarkChanged(AttendanceMarkChanged event) {
        recipients
                .teacherUser(event.teacherId())
                .ifPresent(user -> notifications.attendanceChanged(
                        user, event.actorUserId(), event.actorName(), event.teacherId(), event.date()));
    }

    @EventListener
    void onMonthLocked(AttendanceMonthLocked event) {
        recipients
                .teacherUser(event.teacherId())
                .ifPresent(user -> notifications.create(
                        user, NotificationType.ATTENDANCE_MONTH_LOCKED, MessageFactory.monthLocked(event.month())));
    }

    @EventListener
    void onMonthReopened(AttendanceMonthReopened event) {
        recipients
                .teacherUser(event.teacherId())
                .ifPresent(user -> notifications.create(
                        user,
                        NotificationType.ATTENDANCE_MONTH_REOPENED,
                        MessageFactory.monthReopened(event.month(), event.reason())));
    }
}
