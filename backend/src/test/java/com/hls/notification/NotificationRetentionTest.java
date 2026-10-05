package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.identity.user.Role;
import com.hls.notification.api.NotificationType;
import com.hls.notification.internal.MessageFactory;
import com.hls.notification.internal.NotificationService;
import com.hls.notification.internal.RetentionJob;
import com.hls.support.MasterDataTestBase;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Spec 010 US5: 90-day retention counted from creation, and the database guards the channel and the link. */
class NotificationRetentionTest extends MasterDataTestBase {

    @Autowired
    private RetentionJob job;

    @Autowired
    private NotificationService notifications;

    private UUID user() {
        return signInAs(Role.TEACHER).userId();
    }

    private UUID insertAt(UUID user, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into notification (id, recipient_user_id, type, title, message, link, channel, created_at, updated_at)"
                        + " values (?, ?, 'LEAVE_DECIDED', 'Title', 'Message', '/leave/history', 'IN_APP', ?, ?)",
                id,
                user,
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        return id;
    }

    private boolean exists(UUID id) {
        return jdbc.queryForObject("select count(*) from notification where id = ?", Integer.class, id) == 1;
    }

    @Test
    void onlyNotificationsOlderThanNinetyDaysAreRemoved() {
        UUID user = user();
        Instant now = Instant.now();
        UUID at89 = insertAt(user, now.minus(Duration.ofDays(89)));
        UUID justInside = insertAt(user, now.minus(Duration.ofDays(90)).plus(Duration.ofMinutes(5)));
        UUID justOutside = insertAt(user, now.minus(Duration.ofDays(90)).minus(Duration.ofMinutes(5)));
        UUID at91 = insertAt(user, now.minus(Duration.ofDays(91)));
        UUID fresh = insertAt(user, now);

        int removed = job.purge();

        assertThat(removed).isGreaterThanOrEqualTo(2);
        assertThat(exists(at89)).isTrue();
        assertThat(exists(justInside)).isTrue();
        assertThat(exists(fresh)).isTrue();
        assertThat(exists(justOutside)).isFalse();
        assertThat(exists(at91)).isFalse();
    }

    @Test
    void aMergedNotificationExpiresWithItsFirstEventNotItsLastUpdate() {
        UUID user = user();
        UUID actor = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        var first = notifications.attendanceChanged(user, actor, "Manoj", teacher, LocalDate.now().minusDays(2));
        java.sql.Timestamp createdBefore = jdbc.queryForObject(
                "select created_at from notification where id = ?", java.sql.Timestamp.class, first.getId());
        var merged = notifications.attendanceChanged(user, actor, "Manoj", teacher, LocalDate.now().minusDays(1));
        assertThat(merged.getId()).isEqualTo(first.getId());
        assertThat(jdbc.queryForObject(
                        "select created_at from notification where id = ?", java.sql.Timestamp.class, first.getId()))
                .isEqualTo(createdBefore);

        jdbc.update(
                "update notification set created_at = now() - interval '91 days' where id = ?", first.getId());
        job.purge();

        assertThat(exists(first.getId())).isFalse();
    }

    @Test
    void everyNotificationIsInApp() {
        UUID user = user();

        var created = notifications.create(
                user, NotificationType.LEAVE_DECIDED, MessageFactory.leaveApproved(LocalDate.now(), LocalDate.now()));

        assertThat(created.getChannel().name()).isEqualTo("IN_APP");
        assertThat(jdbc.queryForObject("select channel from notification where id = ?", String.class, created.getId()))
                .isEqualTo("IN_APP");
    }

    @Test
    void theDatabaseRejectsAnotherChannelAndALinkThatIsNotAnAppRoute() {
        UUID user = user();
        String insert = "insert into notification (id, recipient_user_id, type, title, message, link, channel,"
                + " created_at, updated_at) values (?, ?, 'LEAVE_DECIDED', 't', 'm', ?, ?, now(), now())";

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), user, "/leave/history", "SMS"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), user, "https://example.com", "IN_APP"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), user, "leave/history", "IN_APP"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
