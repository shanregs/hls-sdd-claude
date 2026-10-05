package com.hls.notification.internal;

import com.hls.notification.api.NotificationType;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.recruitment.api.WonProspectOverdue;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the overdue-MoU event of spec 023 into one notification for the prospect's owner, the Zone Manager(s) of the
 * Zone, and every active Admin and Director. Runs inside the publisher's transaction.
 */
@Component
public class MarketingEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;
    private final ManagerQueries managers;

    public MarketingEventListener(NotificationService notifications, RecipientResolver recipients, ManagerQueries managers) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.managers = managers;
    }

    @EventListener
    void onOverdue(WonProspectOverdue event) {
        Set<UUID> to = new LinkedHashSet<>();
        if (event.ownerUserId() != null) {
            to.add(event.ownerUserId());
        }
        for (ManagerRef manager : managers.managersOfZone(event.zoneId())) {
            if (manager.userId() != null) {
                to.add(manager.userId());
            }
        }
        to.addAll(recipients.activeAdminsAndDirectors());
        MessageFactory.Text text = MessageFactory.mouNotRecorded(event.name(), event.days(), event.prospectId());
        for (UUID user : to) {
            notifications.create(user, NotificationType.MOU_NOT_RECORDED, text);
        }
    }
}
