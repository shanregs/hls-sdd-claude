package com.hls.notification.internal;

import com.hls.notification.api.NotificationType;
import com.hls.performance.api.ObservationPlanned;
import com.hls.performance.api.ReferralRecorded;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the events of spec 026 into notifications: the Manager hears of a planned observation, every active Director hears
 * of a referral. Runs inside the publisher's transaction.
 */
@Component
public class PerformanceEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;

    public PerformanceEventListener(NotificationService notifications, RecipientResolver recipients) {
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @EventListener
    void onPlanned(ObservationPlanned event) {
        notifications.create(
                event.managerUserId(),
                NotificationType.OBSERVATION_PLANNED,
                MessageFactory.observationPlanned(event.teacherName(), event.plannedOn(), event.reason()));
    }

    @EventListener
    void onReferral(ReferralRecorded event) {
        MessageFactory.Text text = MessageFactory.referralRecorded(event.teacherName(), event.recommendation(), event.issueId());
        for (UUID director : recipients.activeDirectors()) {
            notifications.create(director, NotificationType.REFERRAL_RECORDED, text);
        }
    }
}
