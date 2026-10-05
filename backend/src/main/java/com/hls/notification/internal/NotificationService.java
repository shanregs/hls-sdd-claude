package com.hls.notification.internal;

import com.hls.notification.api.NotificationType;
import com.hls.notification.internal.MessageFactory.Text;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates notifications (called by the event listeners, inside the producer's transaction) and serves a
 * user's own notifications (spec 010). Every read and write is scoped by the recipient.
 */
@Service
public class NotificationService {

    /** Several attendance changes by one person to one Teacher within this time become one notification. */
    public static final Duration MERGE_WINDOW = Duration.ofMinutes(10);

    public static final int MAX_PAGE_SIZE = 100;

    public record NotificationView(
            UUID id,
            String type,
            String title,
            String message,
            String link,
            String channel,
            boolean read,
            Instant createdAt,
            Instant updatedAt) {}

    public record ListResponse(List<NotificationView> content, int page, int size, long totalElements, long unread) {}

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Stores one notification; text beyond the column limits is shortened, the link must be an app route. */
    @Transactional
    public Notification create(UUID recipientUserId, NotificationType type, Text text) {
        return notifications.save(new Notification(
                recipientUserId,
                type,
                MessageFactory.shorten(text.title(), MessageFactory.MAX_TITLE),
                MessageFactory.shorten(text.message(), MessageFactory.MAX_MESSAGE),
                checkedLink(text.link()),
                null,
                null,
                clock.instant()));
    }

    /**
     * Records that a supervisor changed one of the Teacher's days. Folds into the recipient's unread
     * notification for the same person, Teacher and month when its last activity is within the merge
     * window; otherwise starts a new one.
     */
    @Transactional
    public Notification attendanceChanged(
            UUID recipientUserId, UUID actorUserId, String actorName, UUID teacherId, LocalDate day) {
        YearMonth month = YearMonth.from(day);
        String key = "attendance:" + actorUserId + ":" + teacherId + ":" + month;
        Instant now = clock.instant();
        var existing = notifications.findMergeable(recipientUserId, key, now.minus(MERGE_WINDOW));
        if (existing.isEmpty()) {
            Text text = MessageFactory.attendanceChanged(actorName, new TreeSet<>(List.of(day)), month);
            return notifications.save(new Notification(
                    recipientUserId,
                    NotificationType.ATTENDANCE_CHANGED,
                    text.title(),
                    MessageFactory.shorten(text.message(), MessageFactory.MAX_MESSAGE),
                    text.link(),
                    key,
                    day.toString(),
                    now));
        }
        Notification merged = existing.get(0);
        TreeSet<LocalDate> days = Arrays.stream(merged.getDetail().split(","))
                .map(LocalDate::parse)
                .collect(Collectors.toCollection(TreeSet::new));
        days.add(day);
        Text text = MessageFactory.attendanceChanged(actorName, days, month);
        merged.merge(
                text.title(),
                MessageFactory.shorten(text.message(), MessageFactory.MAX_MESSAGE),
                days.stream().map(LocalDate::toString).collect(Collectors.joining(",")),
                now);
        return notifications.save(merged);
    }

    @Transactional(readOnly = true)
    public ListResponse list(UUID userId, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        var result = unreadOnly ? notifications.findUnreadOf(userId, pageable) : notifications.findAllOf(userId, pageable);
        return new ListResponse(
                result.getContent().stream().map(NotificationService::view).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                notifications.countByRecipientUserIdAndReadAtIsNull(userId));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByRecipientUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationView markRead(UUID userId, UUID id) {
        Notification notification = own(userId, id);
        notification.markRead(clock.instant());
        return view(notifications.save(notification));
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, clock.instant());
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        notifications.delete(own(userId, id));
    }

    @Transactional
    public int clearRead(UUID userId) {
        return notifications.deleteRead(userId);
    }

    /** Removes notifications created before the cutoff (spec 010 FR-010); returns how many. */
    @Transactional
    public int purgeCreatedBefore(Instant cutoff) {
        return notifications.deleteOlderThan(cutoff);
    }

    private Notification own(UUID userId, UUID id) {
        return notifications
                .findByIdAndRecipientUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found."));
    }

    private static String checkedLink(String link) {
        if (link == null) {
            return null;
        }
        if (!link.startsWith("/") || link.length() > MessageFactory.MAX_LINK) {
            throw new InvalidInputException("A notification link must be an app route.");
        }
        return link;
    }

    static NotificationView view(Notification n) {
        return new NotificationView(
                n.getId(),
                n.getType().name(),
                n.getTitle(),
                n.getMessage(),
                n.getLink(),
                n.getChannel().name(),
                n.getReadAt() != null,
                n.getCreatedAt(),
                n.getUpdatedAt());
    }
}
