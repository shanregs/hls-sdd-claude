package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.notification.api.NotificationType;
import com.hls.notification.internal.MessageFactory;
import com.hls.notification.internal.NotificationService;
import com.hls.school.api.InvalidInputException;
import com.hls.support.MasterDataTestBase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 010 T008: storing, truncating, merging and the own-scope operations of the service. */
class NotificationServiceTest extends MasterDataTestBase {

    @Autowired
    protected NotificationService service;

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from notification where recipient_user_id = ?", Integer.class, user);
    }

    @Test
    void createStoresTheTextWithTheInAppChannelAndLeavesItUnread() {
        UUID user = UUID.randomUUID();

        var n = service.create(user, NotificationType.LEAVE_DECIDED, MessageFactory.leaveApproved(DAY, DAY));

        assertThat(n.getReadAt()).isNull();
        assertThat(n.getChannel().name()).isEqualTo("IN_APP");
        assertThat(service.unreadCount(user)).isEqualTo(1);
        assertThat(service.list(user, false, 0, 25).content()).singleElement().satisfies(v -> {
            assertThat(v.title()).isEqualTo("Your leave was approved");
            assertThat(v.read()).isFalse();
            assertThat(v.link()).isEqualTo("/leave/history");
        });
    }

    @Test
    void overLongTextIsShortenedAndALinkThatIsNotAnAppRouteIsRefused() {
        UUID user = UUID.randomUUID();
        var long1 = new MessageFactory.Text("T".repeat(120), "M".repeat(500), "/x");
        var n = service.create(user, NotificationType.LEAVE_REQUESTED, long1);
        assertThat(n.getTitle().length()).isEqualTo(MessageFactory.MAX_TITLE);
        assertThat(n.getMessage().length()).isEqualTo(MessageFactory.MAX_MESSAGE);

        assertThatThrownBy(() -> service.create(
                        user, NotificationType.LEAVE_REQUESTED, new MessageFactory.Text("t", "m", "https://evil.example")))
                .isInstanceOf(InvalidInputException.class);
        assertThat(rows(user)).isEqualTo(1);
    }

    @Test
    void changesByTheSamePersonWithinTenMinutesBecomeOneUnreadNotificationWithADayCount() {
        UUID teacherUser = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();

        service.attendanceChanged(teacherUser, actor, "Manoj Manager", teacher, DAY);
        service.attendanceChanged(teacherUser, actor, "Manoj Manager", teacher, DAY.plusDays(1));
        service.attendanceChanged(teacherUser, actor, "Manoj Manager", teacher, DAY.plusDays(1));
        service.attendanceChanged(teacherUser, actor, "Manoj Manager", teacher, DAY.plusDays(2));

        assertThat(rows(teacherUser)).isEqualTo(1);
        var view = service.list(teacherUser, true, 0, 25).content().get(0);
        assertThat(view.message()).isEqualTo("Manoj Manager updated 3 days of your attendance in October 2026.");
    }

    @Test
    void aChangeAfterTheWindowOrAfterReadingStartsANewNotification() {
        UUID teacherUser = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        service.attendanceChanged(teacherUser, actor, "Manoj Manager", teacher, DAY);

        // Last activity 11 minutes ago: outside the merge window.
        jdbc.update(
                "update notification set updated_at = ? where recipient_user_id = ?",
                java.sql.Timestamp.from(Instant.now().minus(11, ChronoUnit.MINUTES)),
                teacherUser);
        service.attendanceChanged(teacherUser, actor, "Manoj Manager", teacher, DAY.plusDays(1));
        assertThat(rows(teacherUser)).isEqualTo(2);

        // Read ones are never merged into.
        service.markAllRead(teacherUser);
        service.attendanceChanged(teacherUser, actor, "Manoj Manager", teacher, DAY.plusDays(2));
        assertThat(rows(teacherUser)).isEqualTo(3);
        assertThat(service.unreadCount(teacherUser)).isEqualTo(1);
    }

    @Test
    void differentActorsTeachersAndMonthsDoNotMerge() {
        UUID teacherUser = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        UUID manoj = UUID.randomUUID();
        UUID asha = UUID.randomUUID();

        service.attendanceChanged(teacherUser, manoj, "Manoj Manager", teacher, DAY);
        service.attendanceChanged(teacherUser, asha, "Asha Admin", teacher, DAY);
        service.attendanceChanged(teacherUser, manoj, "Manoj Manager", UUID.randomUUID(), DAY);
        service.attendanceChanged(teacherUser, manoj, "Manoj Manager", teacher, DAY.plusMonths(1));

        assertThat(rows(teacherUser)).isEqualTo(4);
    }

    @Test
    void theOwnScopeOperationsNeverTouchAnotherUsersNotifications() {
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        var a = service.create(mine, NotificationType.LEAVE_DECIDED, MessageFactory.leaveApproved(DAY, DAY));
        var b = service.create(theirs, NotificationType.LEAVE_DECIDED, MessageFactory.leaveApproved(DAY, DAY));

        assertThatThrownBy(() -> service.markRead(mine, b.getId())).isInstanceOf(com.hls.school.api.NotFoundException.class);
        assertThatThrownBy(() -> service.delete(mine, b.getId())).isInstanceOf(com.hls.school.api.NotFoundException.class);
        service.markAllRead(mine);
        assertThat(service.unreadCount(theirs)).isEqualTo(1);

        assertThat(service.markRead(mine, a.getId()).read()).isTrue();
        assertThat(service.clearRead(mine)).isEqualTo(1);
        assertThat(rows(mine)).isZero();
        assertThat(rows(theirs)).isEqualTo(1);
    }

    @Test
    void theListIsNewestFirstPagedAndFilteredToUnread() {
        UUID user = UUID.randomUUID();
        for (int i = 1; i <= 5; i++) {
            service.create(user, NotificationType.LEAVE_DECIDED, new MessageFactory.Text("N" + i, "m", "/x"));
            jdbc.update(
                    "update notification set updated_at = ?, created_at = ? where recipient_user_id = ? and title = ?",
                    java.sql.Timestamp.from(Instant.now().minus(10 - i, ChronoUnit.MINUTES)),
                    java.sql.Timestamp.from(Instant.now().minus(10 - i, ChronoUnit.MINUTES)),
                    user,
                    "N" + i);
        }
        service.markRead(user, service.list(user, false, 0, 1).content().get(0).id());

        var first = service.list(user, false, 0, 2);
        assertThat(first.content()).extracting(v -> v.title()).containsExactly("N5", "N4");
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.unread()).isEqualTo(4);
        assertThat(service.list(user, false, 1, 2).content()).extracting(v -> v.title()).containsExactly("N3", "N2");
        assertThat(service.list(user, false, 2, 2).content()).extracting(v -> v.title()).containsExactly("N1");
        assertThat(service.list(user, true, 0, 25).totalElements()).isEqualTo(4);
        assertThat(service.list(user, false, 0, 1000).size()).isEqualTo(NotificationService.MAX_PAGE_SIZE);
    }
}
