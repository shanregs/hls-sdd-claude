package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.leave.LeaveTestBase;
import com.hls.notification.internal.NotificationDevSeeder;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Spec 010 demo data: the demo flag seeds notifications that match the demo leave requests, once. */
@TestPropertySource(properties = "hls.seed.demo-data=true")
class NotificationDevSeederTest extends LeaveTestBase {

    @Autowired
    private NotificationDevSeeder seeder;

    private UUID userOf(String phone) {
        return jdbc.queryForObject("select id from app_user where phone = ?", UUID.class, phone);
    }

    private int countFor(String phone, String messageLike) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and message like ?",
                Integer.class,
                userOf(phone),
                messageLike);
    }

    @Test
    void taraHasAReadApprovalAnUnreadRejectionAndAnUnreadAttendanceNotice() {
        Signed tara = signIn(null, "9800000004", "Password123!");

        String body = get("/api/v1/me/notifications?size=100", tara.token()).body();

        assertThat(body).contains("Your leave was approved").contains("Your leave was rejected");
        assertThat(body).contains("Annual exams are on that day").contains("Your attendance was updated");
        UUID id = userOf("9800000004");
        assertThat(jdbc.queryForObject(
                        "select count(*) from notification where recipient_user_id = ? and type = 'LEAVE_DECIDED'"
                                + " and title = 'Your leave was approved' and read_at is not null",
                        Integer.class,
                        id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from notification where recipient_user_id = ? and type = 'LEAVE_DECIDED'"
                                + " and title = 'Your leave was rejected' and read_at is null",
                        Integer.class,
                        id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from notification where recipient_user_id = ?"
                                + " and type = 'ATTENDANCE_CHANGED' and read_at is null",
                        Integer.class,
                        id))
                .isEqualTo(1);
        // The attendance seeder is quiet, so these three are all Tara starts with.
        assertThat(jdbc.queryForObject(
                        "select count(*) from notification where recipient_user_id = ?", Integer.class, id))
                .isEqualTo(3);
    }

    @Test
    void manojHasOneUnreadNoticeForMeenaAndOneForKarthik() {
        assertThat(countFor("9800000003", "Meena Selvi asked for leave%")).isEqualTo(1);
        assertThat(countFor("9800000003", "Karthik Raja asked for leave%")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from notification where recipient_user_id = ?"
                                + " and type = 'LEAVE_REQUESTED' and read_at is not null",
                        Integer.class,
                        userOf("9800000003")))
                .isZero();
    }

    @Test
    void ashaHasTheNoticeForLakshmiWhoseSchoolHasNoManager() {
        assertThat(countFor("9800000001", "Lakshmi Priya asked for leave%")).isEqualTo(1);
    }

    @Test
    void runningTheSeederAgainAddsNothing() {
        int before = jdbc.queryForObject("select count(*) from notification", Integer.class);

        seeder.run(null);

        assertThat(jdbc.queryForObject("select count(*) from notification", Integer.class)).isEqualTo(before);
    }
}
