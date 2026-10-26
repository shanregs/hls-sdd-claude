package com.hls.notification.internal;

import com.hls.notification.api.NotificationType;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.api.MouDecided;
import com.hls.schoolbilling.api.MouSubmitted;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the events of spec 031 into notifications. A submission tells the active Directors and the School Zone Manager,
 * never Admin; a decision tells the submitter and every active Admin. The person who acted is never told. Runs inside
 * the publisher transaction.
 */
@Component
public class MouEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;
    private final ManagerQueries managers;
    private final SchoolDirectory schools;

    public MouEventListener(
            NotificationService notifications,
            RecipientResolver recipients,
            ManagerQueries managers,
            SchoolDirectory schools) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.managers = managers;
        this.schools = schools;
    }

    @EventListener
    void onSubmitted(MouSubmitted event) {
        Set<UUID> to = new LinkedHashSet<>(recipients.activeDirectors());
        managers.managerOfSchool(event.schoolId()).filter(ManagerRef::active).map(ManagerRef::userId).ifPresent(to::add);
        to.remove(event.submittedBy());
        MessageFactory.Text base = MessageFactory.mouSubmitted(schoolName(event.schoolId()), event.resubmitted());
        MessageFactory.Text text = withSchoolLink(base, event.schoolId());
        to.forEach(user -> notifications.create(user, NotificationType.MOU_SUBMITTED, text));
    }

    @EventListener
    void onDecided(MouDecided event) {
        Set<UUID> to = new LinkedHashSet<>();
        if (event.submittedBy() != null) {
            to.add(event.submittedBy());
        }
        recipients.activeAdminsAndDirectors().stream()
                .filter(id -> !recipients.activeDirectors().contains(id))
                .forEach(to::add);
        to.remove(event.decidedBy());
        MessageFactory.Text base = MessageFactory.mouDecided(
                schoolName(event.schoolId()), event.outcome(), event.reason(), event.termsAmended());
        MessageFactory.Text text = withSchoolLink(base, event.schoolId());
        to.forEach(user -> notifications.create(user, NotificationType.MOU_DECIDED, text));
    }

    private static MessageFactory.Text withSchoolLink(MessageFactory.Text text, UUID schoolId) {
        return new MessageFactory.Text(text.title(), text.message(), text.link() + schoolId);
    }

    private String schoolName(UUID schoolId) {
        return schools.school(schoolId).map(SchoolInfo::name).orElse("a School");
    }
}
