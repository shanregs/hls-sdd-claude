package com.hls.notification.internal;

import com.hls.incentive.api.IncentiveDecided;
import com.hls.incentive.api.IncentiveEarned;
import com.hls.incentive.api.IncentiveMouChanged;
import com.hls.notification.api.NotificationType;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the incentive events of spec 030 into notifications: a new incentive tells the active Directors and the School's Zone
 * Manager once (the earner included when they are that Zone Manager, since they may approve); a decision tells the earner
 * unless they made it; a MoU that changes while the pay run awaits approval tells the Directors. Runs inside the publisher's
 * transaction.
 */
@Component
public class IncentiveEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;
    private final ManagerQueries managers;
    private final SchoolDirectory schools;

    public IncentiveEventListener(
            NotificationService notifications, RecipientResolver recipients, ManagerQueries managers, SchoolDirectory schools) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.managers = managers;
        this.schools = schools;
    }

    @EventListener
    void onEarned(IncentiveEarned event) {
        Set<UUID> to = new LinkedHashSet<>(recipients.activeDirectors());
        if (event.zoneManagerUserId() != null) {
            to.add(event.zoneManagerUserId());
        }
        MessageFactory.Text text = IncentiveMessages.pending(schoolName(event.schoolId()), personName(event.earnerUserId()), event.incentiveId());
        to.forEach(user -> notifications.create(user, NotificationType.INCENTIVE_PENDING, text));
    }

    @EventListener
    void onDecided(IncentiveDecided event) {
        if (event.earnerUserId().equals(event.decidedBy())) {
            return;
        }
        notifications.create(
                event.earnerUserId(),
                NotificationType.INCENTIVE_DECIDED,
                IncentiveMessages.decided(schoolName(event.schoolId()), event.outcome(), event.reason()));
    }

    @EventListener
    void onMouChanged(IncentiveMouChanged event) {
        MessageFactory.Text text =
                IncentiveMessages.mouChanged(schoolName(event.schoolId()), personName(event.earnerUserId()), event.incentiveId());
        recipients.activeDirectors().forEach(user -> notifications.create(user, NotificationType.INCENTIVE_MOU_CHANGED, text));
    }

    private String schoolName(UUID schoolId) {
        return schools.school(schoolId).map(SchoolInfo::name).orElse("a School");
    }

    private String personName(UUID userId) {
        return managers.managerByUserId(userId).map(ManagerRef::displayName).orElse("A Manager");
    }
}
