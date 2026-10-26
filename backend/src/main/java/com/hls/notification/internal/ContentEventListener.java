package com.hls.notification.internal;

import com.hls.content.api.ContentAssigned;
import com.hls.content.api.ContentNoReviewer;
import com.hls.content.api.ContentReviewed;
import com.hls.content.api.ContentSubmitted;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.notification.api.NotificationType;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the content events of spec 029 into notifications: the author hears of an assignment and of a decision, every
 * reviewer hears of a submission, and Admins and Directors hear when nobody else can review. Runs inside the publisher
 * transaction.
 */
@Component
public class ContentEventListener {

    private final NotificationService notifications;
    private final RecipientResolver recipients;
    private final AppUserRepository users;

    public ContentEventListener(NotificationService notifications, RecipientResolver recipients, AppUserRepository users) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.users = users;
    }

    @EventListener
    void onAssigned(ContentAssigned event) {
        notifications.create(
                event.authorUserId(),
                NotificationType.CONTENT_ASSIGNED,
                MessageFactory.contentAssigned(event.title(), event.dueOn(), event.contentId()));
    }

    @EventListener
    void onSubmitted(ContentSubmitted event) {
        String author = users.findById(event.authorUserId()).map(AppUser::getDisplayName).orElse("An author");
        MessageFactory.Text text = MessageFactory.contentSubmitted(event.title(), event.versionNumber(), author, event.contentId());
        Set<UUID> reviewers = new LinkedHashSet<>(recipients.contentReviewers());
        reviewers.remove(event.authorUserId());
        reviewers.remove(event.submittedByUserId());
        for (UUID reviewer : reviewers) {
            notifications.create(reviewer, NotificationType.CONTENT_SUBMITTED, text);
        }
    }

    @EventListener
    void onReviewed(ContentReviewed event) {
        boolean approved = "APPROVED".equals(event.decision());
        MessageFactory.Text text = approved
                ? MessageFactory.contentApproved(event.title(), event.versionNumber(), event.contentId())
                : MessageFactory.contentReturned(event.title(), event.versionNumber(), event.comment(), event.contentId());
        NotificationType type = approved ? NotificationType.CONTENT_APPROVED : NotificationType.CONTENT_RETURNED;
        Set<UUID> told = new LinkedHashSet<>();
        told.add(event.authorUserId());
        told.add(event.submittedByUserId());
        told.remove(event.reviewerUserId());
        for (UUID user : told) {
            notifications.create(user, type, text);
        }
    }

    @EventListener
    void onNoReviewer(ContentNoReviewer event) {
        MessageFactory.Text text = MessageFactory.contentNoReviewer(event.title(), event.versionNumber(), event.contentId());
        for (UUID user : recipients.activeAdminsAndDirectors()) {
            notifications.create(user, NotificationType.CONTENT_NO_REVIEWER, text);
        }
    }
}
