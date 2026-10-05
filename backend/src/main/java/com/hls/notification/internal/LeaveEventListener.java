package com.hls.notification.internal;

import com.hls.leave.api.LeaveCancelled;
import com.hls.leave.api.LeaveDecided;
import com.hls.leave.api.LeaveRequested;
import com.hls.notification.api.NotificationType;
import com.hls.notification.internal.MessageFactory.Text;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns leave events into notifications (spec 010). The listeners run synchronously inside the
 * publisher's transaction, so a notification commits or rolls back with the decision itself.
 */
@Component
public class LeaveEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;

    public LeaveEventListener(NotificationService notifications, RecipientResolver recipients) {
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @EventListener
    void onDecided(LeaveDecided event) {
        Text text =
                switch (event.decision()) {
                    case APPROVED -> MessageFactory.leaveApproved(event.firstDate(), event.lastDate());
                    case REJECTED -> MessageFactory.leaveRejected(event.firstDate(), event.lastDate(), event.reason());
                    case REVOKED -> MessageFactory.leaveRevoked(event.firstDate(), event.lastDate(), event.reason());
                };
        recipients
                .teacherUser(event.teacherId())
                .ifPresent(user -> notifications.create(user, NotificationType.LEAVE_DECIDED, text));
    }

    @EventListener
    void onRequested(LeaveRequested event) {
        Text text = MessageFactory.leaveRequested(event.teacherName(), event.firstDate(), event.lastDate());
        recipients
                .leaveSupervisors(event.schoolId())
                .forEach(user -> notifications.create(user, NotificationType.LEAVE_REQUESTED, text));
    }

    @EventListener
    void onCancelled(LeaveCancelled event) {
        Text text = MessageFactory.leaveCancelled(event.teacherName(), event.firstDate(), event.lastDate());
        recipients
                .leaveSupervisors(event.schoolId())
                .forEach(user -> notifications.create(user, NotificationType.LEAVE_CANCELLED, text));
    }
}
