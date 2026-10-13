package com.hls.notification.internal;

import com.hls.attendance.api.ManagerDayCorrected;
import com.hls.attendance.api.ManagerRegularizationDecided;
import com.hls.attendance.api.ManagerRegularizationRequested;
import com.hls.leave.api.ManagerLeaveCancelled;
import com.hls.leave.api.ManagerLeaveDecided;
import com.hls.leave.api.ManagerLeaveRequested;
import com.hls.notification.api.NotificationType;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the Manager attendance events of spec 032 into notifications: the Manager hears of a correction and of the
 * decision on a forgotten check-in; every active Admin and Director hears of a new request. Runs inside the
 * publisher's transaction.
 */
@Component
public class ManagerAttendanceEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;

    public ManagerAttendanceEventListener(NotificationService notifications, RecipientResolver recipients) {
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @EventListener
    void onCorrected(ManagerDayCorrected event) {
        MessageFactory.Text text = MessageFactory.managerDayCorrected(event.date());
        managerUser(event.managerId(), event.actorUserId())
                .ifPresent(user -> notifications.create(user, NotificationType.MANAGER_DAY_CORRECTED, text));
    }

    @EventListener
    void onRequested(ManagerRegularizationRequested event) {
        String name = recipients.manager(event.managerId()).map(ManagerRef::displayName).orElse("A Manager");
        MessageFactory.Text text = MessageFactory.managerRegularizationRequested(name, event.date());
        for (UUID user : recipients.activeAdminsAndDirectors()) {
            notifications.create(user, NotificationType.MANAGER_REGULARIZATION_REQUESTED, text);
        }
    }

    @EventListener
    void onDecided(ManagerRegularizationDecided event) {
        boolean approved = ManagerRegularizationDecided.APPROVED.equals(event.decision());
        MessageFactory.Text text = MessageFactory.managerRegularizationDecided(event.date(), approved, event.reason());
        managerUser(event.managerId(), null)
                .ifPresent(user -> notifications.create(user, NotificationType.MANAGER_REGULARIZATION_DECIDED, text));
    }

    @EventListener
    void onLeaveRequested(ManagerLeaveRequested event) {
        ManagerRef manager = recipients.manager(event.managerId()).orElse(null);
        String name = manager == null ? "A Manager" : manager.displayName();
        MessageFactory.Text text = MessageFactory.managerLeaveRequested(name, event.firstDate(), event.lastDate());
        tellAdminsAndDirectors(manager, NotificationType.MANAGER_LEAVE_REQUESTED, text);
    }

    @EventListener
    void onLeaveCancelled(ManagerLeaveCancelled event) {
        ManagerRef manager = recipients.manager(event.managerId()).orElse(null);
        String name = manager == null ? "A Manager" : manager.displayName();
        MessageFactory.Text text = MessageFactory.managerLeaveCancelled(name, event.firstDate(), event.lastDate());
        tellAdminsAndDirectors(manager, NotificationType.MANAGER_LEAVE_CANCELLED, text);
    }

    @EventListener
    void onLeaveDecided(ManagerLeaveDecided event) {
        MessageFactory.Text text = MessageFactory.managerLeaveDecided(
                event.decision().name(), event.firstDate(), event.lastDate(), event.reason());
        managerUser(event.managerId(), null)
                .ifPresent(user -> notifications.create(user, NotificationType.MANAGER_LEAVE_DECIDED, text));
    }

    /** Every active Admin and Director, except the Manager when they hold one of those roles too. */
    private void tellAdminsAndDirectors(ManagerRef manager, NotificationType type, MessageFactory.Text text) {
        UUID own = manager == null ? null : manager.userId();
        for (UUID user : recipients.activeAdminsAndDirectors()) {
            if (!user.equals(own)) {
                notifications.create(user, type, text);
            }
        }
    }

    /** The Manager's user, unless it is the actor who made the change themselves. */
    private java.util.Optional<UUID> managerUser(UUID managerId, UUID actorUserId) {
        return recipients
                .manager(managerId)
                .map(ManagerRef::userId)
                .filter(user -> user != null && !user.equals(actorUserId));
    }
}
