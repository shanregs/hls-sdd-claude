package com.hls.notification.internal;

import com.hls.notification.api.NotificationType;
import com.hls.substitution.api.SchoolInformed;
import com.hls.substitution.api.SubstitutionAssigned;
import com.hls.substitution.api.SubstitutionEnded;
import com.hls.substitution.api.SubstitutionNeeded;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the substitution events of spec 017a into notifications: the School's Manager (else Admin and Director) hears
 * that a substitute is needed and that the School was informed; the substitute hears about an assignment and its end.
 * Runs inside the publisher's transaction. Tasks (spec 025) are deliberately not used.
 */
@Component
public class SubstitutionEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;

    public SubstitutionEventListener(NotificationService notifications, RecipientResolver recipients) {
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @EventListener
    void onNeeded(SubstitutionNeeded event) {
        MessageFactory.Text text =
                MessageFactory.substituteNeeded(event.teacherName(), event.schoolName(), event.date(), event.requirementId());
        for (UUID user : recipients.leaveSupervisors(event.schoolId())) {
            notifications.create(user, NotificationType.SUBSTITUTE_NEEDED, text);
        }
    }

    @EventListener
    void onAssigned(SubstitutionAssigned event) {
        MessageFactory.Text text = MessageFactory.substituteAssigned(event.schoolName(), event.date());
        recipients
                .teacherUser(event.substituteTeacherId())
                .ifPresent(user -> notifications.create(user, NotificationType.SUBSTITUTE_ASSIGNED, text));
    }

    @EventListener
    void onEnded(SubstitutionEnded event) {
        MessageFactory.Text text = MessageFactory.substituteEnded(event.schoolName(), event.date(), event.reason());
        recipients
                .teacherUser(event.substituteTeacherId())
                .ifPresent(user -> notifications.create(user, NotificationType.SUBSTITUTE_ENDED, text));
    }

    @EventListener
    void onSchoolInformed(SchoolInformed event) {
        Set<UUID> to = new LinkedHashSet<>(recipients.leaveSupervisors(event.schoolId()));
        to.remove(event.informedByUserId());
        MessageFactory.Text text = MessageFactory.schoolInformed(event.schoolName(), event.date(), event.requirementId());
        for (UUID user : to) {
            notifications.create(user, NotificationType.SUBSTITUTE_SCHOOL_INFORMED, text);
        }
    }
}
