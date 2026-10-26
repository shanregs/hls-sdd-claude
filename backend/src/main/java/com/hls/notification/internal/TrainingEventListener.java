package com.hls.notification.internal;

import com.hls.notification.api.NotificationType;
import com.hls.training.api.TrainingAssigned;
import com.hls.training.api.TrainingAssignmentRemoved;
import com.hls.training.api.TrainingCannotAttend;
import com.hls.training.api.TrainingExceptionRaised;
import com.hls.training.api.TrainingRegistered;
import com.hls.training.api.TrainingReminderDue;
import com.hls.training.api.TrainingSessionChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the events of spec 024 into notifications: assigned, removed, moved or cancelled people and reminded people hear
 * about their session, a registering Teacher gets a confirmation, and Admin and Director hear of a new exception or a
 * "cannot attend" note. Runs inside the publisher transaction, like the other listeners.
 */
@Component
public class TrainingEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;

    public TrainingEventListener(NotificationService notifications, RecipientResolver recipients) {
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @EventListener
    void onAssigned(TrainingAssigned event) {
        MessageFactory.Text text = MessageFactory.trainingAssigned(event.topic(), event.date());
        event.userIds().forEach(user -> notifications.create(user, NotificationType.TRAINING_ASSIGNED, text));
    }

    @EventListener
    void onRemoved(TrainingAssignmentRemoved event) {
        MessageFactory.Text text = MessageFactory.trainingAssignmentRemoved(event.topic(), event.date());
        event.userIds().forEach(user -> notifications.create(user, NotificationType.TRAINING_ASSIGNMENT_REMOVED, text));
    }

    @EventListener
    void onChanged(TrainingSessionChanged event) {
        MessageFactory.Text text = "CANCELLED".equals(event.change())
                ? MessageFactory.trainingCancelled(event.topic(), event.oldDate())
                : MessageFactory.trainingMoved(event.topic(), event.oldDate(), event.newDate());
        event.userIds().forEach(user -> notifications.create(user, NotificationType.TRAINING_CHANGED, text));
    }

    @EventListener
    void onReminder(TrainingReminderDue event) {
        notifications.create(
                event.userId(),
                NotificationType.TRAINING_REMINDER,
                MessageFactory.trainingReminder(event.topic(), event.date(), event.daysBefore()));
    }

    @EventListener
    void onRegistered(TrainingRegistered event) {
        notifications.create(
                event.userId(),
                NotificationType.TRAINING_REGISTERED,
                MessageFactory.trainingRegistered(event.topic(), event.date()));
    }

    @EventListener
    void onException(TrainingExceptionRaised event) {
        MessageFactory.Text text = MessageFactory.trainingException(event.personName(), event.date(), event.sessionId());
        recipients.activeAdminsAndDirectors()
                .forEach(user -> notifications.create(user, NotificationType.TRAINING_EXCEPTION, text));
    }

    @EventListener
    void onCannotAttend(TrainingCannotAttend event) {
        MessageFactory.Text text = MessageFactory.trainingCannotAttend(event.personName(), event.note(), event.sessionId());
        recipients.activeAdminsAndDirectors()
                .forEach(user -> notifications.create(user, NotificationType.TRAINING_CANNOT_ATTEND, text));
    }
}
