package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.leave.LeaveTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 010 US1: leave decisions notify the Teacher, atomically with the decision. */
class LeaveDecisionNotificationTest extends LeaveTestBase {

    private List<Map<String, Object>> notificationsOf(UUID userId) {
        return jdbc.queryForList(
                "select type, title, message, link, read_at from notification where recipient_user_id = ?"
                        + " order by created_at",
                userId);
    }

    @Test
    void approvingGivesTheTeacherOneUnreadNotification() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));

        assertThat(post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0)).status())
                .isEqualTo(200);

        var rows = notificationsOf(w.teacherA().signed().userId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("type", "LEAVE_DECIDED")
                .containsEntry("title", "Your leave was approved")
                .containsEntry("link", "/leave/history")
                .containsEntry("read_at", null);
        assertThat((String) rows.get(0).get("message")).contains("was approved");
    }

    @Test
    void rejectingPutsTheReasonInTheMessage() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));

        assertThat(post(SUP + "/" + id + "/reject", w.managerA().token(), Map.of("reason", "Exams that week", "version", 0))
                        .status())
                .isEqualTo(200);

        var rows = notificationsOf(w.teacherA().signed().userId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("title", "Your leave was rejected");
        assertThat((String) rows.get(0).get("message")).contains("was rejected: Exams that week.");
    }

    @Test
    void revokingNotifiesTheTeacherAgain() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        assertThat(post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0)).status())
                .isEqualTo(200);

        Resp revoked = post(SUP + "/" + id + "/revoke", w.managerA().token(), Map.of("reason", "Staff shortage", "version", 1));

        assertThat(revoked.status()).as(revoked.body()).isEqualTo(200);
        var rows = notificationsOf(w.teacherA().signed().userId());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsEntry("title", "Your approved leave was revoked");
        assertThat((String) rows.get(1).get("message")).contains("was revoked: Staff shortage.");
    }

    @Test
    void theTeacherSeesTheNotificationThroughTheApi() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0));

        Resp list = get("/api/v1/me/notifications", w.teacherA().token());

        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains("Your leave was approved").contains("\"unread\":1");
    }

    @Test
    void aRefusedApprovalLeavesNoNotification() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        lockMonth(w.teacherA().teacherId(), YearMonth.from(mon));

        Resp refused = post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0));

        assertThat(refused.status()).isEqualTo(409);
        assertThat(notificationsOf(w.teacherA().signed().userId())).isEmpty();
    }

    @Test
    void aSupervisorSetDayRefusesTheApprovalAndLeavesNoNotification() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(2));
        Resp mark = put(
                "/api/v1/attendance/teachers/" + w.teacherA().teacherId() + "/marks/" + mon.plusDays(1),
                w.managerA().token(),
                Map.of("statusCode", "T", "dayValue", 1));
        assertThat(mark.status()).as(mark.body()).isEqualTo(200);

        Resp refused = post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0));

        assertThat(refused.status()).isEqualTo(409);
        assertThat(notificationsOf(w.teacherA().signed().userId())).isEmpty();
    }

    @Test
    void theTeacherCancellingTheirOwnRequestNotifiesTheTeacherOfNothing() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));

        assertThat(post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of()).status()).isEqualTo(200);

        assertThat(notificationsOf(w.teacherA().signed().userId())).isEmpty();
    }

    @Test
    void aTeacherWithNoLinkedUserCreatesNothingAndTheDecisionStillSucceeds() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        jdbc.update("update teacher set user_id = null where id = ?", w.teacherA().teacherId());
        Integer before = jdbc.queryForObject("select count(*) from notification", Integer.class);

        Resp approved = post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0));

        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from notification", Integer.class)).isEqualTo(before);
    }
}
