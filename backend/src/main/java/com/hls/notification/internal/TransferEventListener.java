package com.hls.notification.internal;

import com.hls.notification.api.NotificationType;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.transfer.api.TransferApplied;
import com.hls.transfer.api.TransferDecided;
import com.hls.transfer.api.TransferRequested;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the events of spec 027 into notifications: deciders hear of a new request, the requester of the decision, and the
 * Teachers and both Schools Managers of an approval and of the day it takes effect. Runs inside the publisher transaction.
 */
@Component
public class TransferEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;
    private final ManagerQueries managers;
    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;

    public TransferEventListener(
            NotificationService notifications,
            RecipientResolver recipients,
            ManagerQueries managers,
            TeacherDirectory teachers,
            SchoolDirectory schools) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.managers = managers;
        this.teachers = teachers;
        this.schools = schools;
    }

    @EventListener
    void onRequested(TransferRequested event) {
        Set<UUID> to = new LinkedHashSet<>(recipients.activeDirectors());
        managerUser(event.fromSchoolId()).ifPresent(to::add);
        to.remove(event.requestedBy());
        MessageFactory.Text text = MessageFactory.transferRequested(
                event.kind(), teacherName(event.teacherId()), schoolName(event.fromSchoolId()), event.effectiveOn());
        to.forEach(user -> notifications.create(user, NotificationType.TRANSFER_REQUESTED, text));
    }

    @EventListener
    void onDecided(TransferDecided event) {
        MessageFactory.Text text = MessageFactory.transferDecided(
                event.kind(), event.outcome(), teacherName(event.teacherId()), event.effectiveOn(), event.requestId());
        Set<UUID> to = new LinkedHashSet<>();
        to.add(event.requestedBy());
        if ("APPROVED".equals(event.outcome())) {
            to.addAll(affected(event.teacherId(), event.replacementTeacherId(), event.fromSchoolId(), event.toSchoolId()));
        }
        to.remove(event.decidedBy());
        to.forEach(user -> notifications.create(user, NotificationType.TRANSFER_DECIDED, text));
    }

    @EventListener
    void onApplied(TransferApplied event) {
        MessageFactory.Text text = MessageFactory.transferApplied(
                event.kind(), teacherName(event.teacherId()), event.effectiveOn(), event.requestId());
        affected(event.teacherId(), event.replacementTeacherId(), event.fromSchoolId(), event.toSchoolId())
                .forEach(user -> notifications.create(user, NotificationType.TRANSFER_APPLIED, text));
    }

    /** The Teachers own users and the Managers of both Schools. */
    private Set<UUID> affected(UUID teacherId, UUID replacementTeacherId, UUID fromSchoolId, UUID toSchoolId) {
        Set<UUID> users = new LinkedHashSet<>();
        recipients.teacherUser(teacherId).ifPresent(users::add);
        if (replacementTeacherId != null) {
            recipients.teacherUser(replacementTeacherId).ifPresent(users::add);
        }
        managerUser(fromSchoolId).ifPresent(users::add);
        managerUser(toSchoolId).ifPresent(users::add);
        return users;
    }

    private Optional<UUID> managerUser(UUID schoolId) {
        return managers.managerOfSchool(schoolId).filter(ManagerRef::active).map(ManagerRef::userId);
    }

    private String teacherName(UUID teacherId) {
        TeacherInfo info = teachers.teacherInfo(List.of(teacherId)).get(teacherId);
        return info == null ? "a Teacher" : info.name();
    }

    private String schoolName(UUID schoolId) {
        return schools.school(schoolId).map(SchoolInfo::name).orElse("a School");
    }
}
