package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.notification.internal.MessageFactory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Spec 010 contracts/notifications-api.md: every title, message and link, as plain text. */
class MessageFactoryTest {

    private static final LocalDate A = LocalDate.of(2026, 10, 12);
    private static final LocalDate B = LocalDate.of(2026, 10, 13);

    @Test
    void leaveDecisionsNameTheDatesAndLinkToMyLeaveHistory() {
        var approved = MessageFactory.leaveApproved(A, B);
        assertThat(approved.title()).isEqualTo("Your leave was approved");
        assertThat(approved.message()).isEqualTo("Your leave 12/10/2026 to 13/10/2026 was approved.");
        assertThat(approved.link()).isEqualTo("/leave/history");

        assertThat(MessageFactory.leaveApproved(A, A).message()).isEqualTo("Your leave 12/10/2026 was approved.");
    }

    @Test
    void rejectionAndRevokeCarryTheReasonShortenedToTwoHundredCharacters() {
        assertThat(MessageFactory.leaveRejected(A, B, "Annual exams that week").message())
                .isEqualTo("Your leave 12/10/2026 to 13/10/2026 was rejected: Annual exams that week.");
        assertThat(MessageFactory.leaveRevoked(A, B, "Needed at school").title())
                .isEqualTo("Your approved leave was revoked");
        assertThat(MessageFactory.leaveRevoked(A, B, "Needed at school").message())
                .contains("revoked: Needed at school.");

        String long250 = "x".repeat(250);
        String message = MessageFactory.leaveRejected(A, B, long250).message();
        assertThat(message).contains("x".repeat(199) + "…");
        assertThat(message).doesNotContain("x".repeat(200));
        assertThat(message.length()).isLessThanOrEqualTo(MessageFactory.MAX_MESSAGE);
    }

    @Test
    void aMissingReasonLeavesNoDanglingColon() {
        assertThat(MessageFactory.leaveRejected(A, B, null).message()).endsWith("was rejected.");
        assertThat(MessageFactory.leaveRejected(A, B, "  ").message()).endsWith("was rejected.");
    }

    @Test
    void supervisorNoticesNameTheTeacherAndLinkToLeaveManagement() {
        var requested = MessageFactory.leaveRequested("Tara Teacher", A, B);
        assertThat(requested.title()).isEqualTo("New leave request");
        assertThat(requested.message()).isEqualTo("Tara Teacher asked for leave 12/10/2026 to 13/10/2026.");
        assertThat(requested.link()).isEqualTo("/operations/leave");

        var cancelled = MessageFactory.leaveCancelled("Tara Teacher", A, A);
        assertThat(cancelled.title()).isEqualTo("Leave request withdrawn");
        assertThat(cancelled.message()).isEqualTo("Tara Teacher cancelled their leave 12/10/2026.");
        assertThat(cancelled.link()).isEqualTo("/operations/leave");
    }

    @Test
    void attendanceChangesReadOneDayOrADayCount() {
        var one = MessageFactory.attendanceChanged(
                "Manoj Manager", new TreeSet<>(java.util.List.of(LocalDate.of(2026, 10, 5))), YearMonth.of(2026, 10));
        assertThat(one.title()).isEqualTo("Your attendance was updated");
        assertThat(one.message()).isEqualTo("Manoj Manager updated your attendance for 05/10/2026.");
        assertThat(one.link()).isEqualTo("/my-attendance?month=2026-10");

        var many = MessageFactory.attendanceChanged(
                "Manoj Manager",
                new TreeSet<>(java.util.List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7))),
                YearMonth.of(2026, 10));
        assertThat(many.message()).isEqualTo("Manoj Manager updated 3 days of your attendance in October 2026.");
    }

    @Test
    void monthLockAndReopenNameTheMonth() {
        assertThat(MessageFactory.monthLocked(YearMonth.of(2026, 9)).message())
                .isEqualTo("September 2026 is locked; changes now need a reopen.");
        assertThat(MessageFactory.monthReopened(YearMonth.of(2026, 9), "Correction for Meena").message())
                .isEqualTo("September 2026 was reopened: Correction for Meena.");
        assertThat(MessageFactory.monthReopened(YearMonth.of(2026, 9), null).link())
                .isEqualTo("/my-attendance?month=2026-09");
    }

    @Test
    void everyTextFitsItsColumnAndEveryLinkIsAnAppRoute() {
        var texts = java.util.List.of(
                MessageFactory.leaveApproved(A, B),
                MessageFactory.leaveRejected(A, B, "r".repeat(400)),
                MessageFactory.leaveRevoked(A, B, "r".repeat(400)),
                MessageFactory.leaveRequested("N".repeat(120), A, B),
                MessageFactory.leaveCancelled("N".repeat(120), A, B),
                MessageFactory.monthLocked(YearMonth.of(2026, 1)),
                MessageFactory.monthReopened(YearMonth.of(2026, 1), "r".repeat(400)));
        for (var text : texts) {
            assertThat(text.title().length()).isLessThanOrEqualTo(MessageFactory.MAX_TITLE);
            assertThat(text.link()).startsWith("/");
        }
    }

    @Test
    void shortenKeepsShortTextAndEndsLongTextWithAnEllipsis() {
        assertThat(MessageFactory.shorten("short", 10)).isEqualTo("short");
        assertThat(MessageFactory.shorten("0123456789", 5)).isEqualTo("0123…");
        assertThat(MessageFactory.shorten(null, 5)).isNull();
    }
}
