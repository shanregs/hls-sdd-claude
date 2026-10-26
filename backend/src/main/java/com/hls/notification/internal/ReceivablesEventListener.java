package com.hls.notification.internal;

import com.hls.notification.api.NotificationType;
import com.hls.schoolbilling.api.MonthlyDueOverdue;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tells the Director and Admin about an overdue monthly due whose School has no Zone Manager to follow it up (spec 022
 * FR-016). When a Zone Manager exists the follow-up task of spec 025 already notifies them.
 */
@Component
public class ReceivablesEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;

    public ReceivablesEventListener(NotificationService notifications, RecipientResolver recipients) {
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @EventListener
    void onOverdue(MonthlyDueOverdue event) {
        if (!event.noManager()) {
            return;
        }
        MessageFactory.Text text = MessageFactory.schoolDueUnassigned(
                event.schoolName(), event.month().toString(), event.balance(), event.daysOverdue(), event.schoolId());
        for (UUID user : recipients.activeAdminsAndDirectors()) {
            notifications.create(user, NotificationType.SCHOOL_DUE_UNASSIGNED, text);
        }
    }
}
