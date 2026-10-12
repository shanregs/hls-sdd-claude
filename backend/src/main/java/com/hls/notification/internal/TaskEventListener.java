package com.hls.notification.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.notification.api.NotificationType;
import com.hls.tasks.api.TaskAssigned;
import com.hls.tasks.api.TaskEscalated;
import com.hls.tasks.api.TaskOverdue;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the task events of spec 025 into notifications: the assignee hears of a new or overdue task, every active Director
 * hears of an escalated one. Runs inside the publisher's transaction.
 */
@Component
public class TaskEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;
    private final AppUserRepository users;

    public TaskEventListener(NotificationService notifications, RecipientResolver recipients, AppUserRepository users) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.users = users;
    }

    @EventListener
    void onAssigned(TaskAssigned event) {
        notifications.create(
                event.assigneeUserId(),
                NotificationType.TASK_ASSIGNED,
                MessageFactory.taskAssigned(event.title(), event.dueOn(), event.taskId()));
    }

    @EventListener
    void onOverdue(TaskOverdue event) {
        notifications.create(
                event.assigneeUserId(),
                NotificationType.TASK_OVERDUE,
                MessageFactory.taskOverdue(event.title(), event.dueOn(), event.taskId()));
    }

    @EventListener
    void onEscalated(TaskEscalated event) {
        String manager = users.findById(event.assigneeUserId()).map(AppUser::getDisplayName).orElse("A Manager");
        MessageFactory.Text text = MessageFactory.taskEscalated(event.title(), manager, event.dueOn(), event.daysOverdue(), event.taskId());
        for (UUID director : recipients.activeDirectors()) {
            notifications.create(director, NotificationType.TASK_ESCALATED, text);
        }
    }
}
