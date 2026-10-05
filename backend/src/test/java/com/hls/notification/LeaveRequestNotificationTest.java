package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.leave.LeaveTestBase;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Spec 010 US2: submit and Teacher-cancel notify the Teacher's Manager, or Admin and Director without one. */
class LeaveRequestNotificationTest extends LeaveTestBase {

    private String nameOf(UUID teacherId) {
        return jdbc.queryForObject("select name from teacher where id = ?", String.class, teacherId);
    }

    /** Recipients of notifications of the type that mention the Teacher by name. */
    private Set<UUID> recipients(String type, UUID teacherId) {
        return jdbc
                .queryForList(
                        "select recipient_user_id from notification where type = ? and message like ?",
                        UUID.class,
                        type,
                        nameOf(teacherId) + "%")
                .stream()
                .collect(Collectors.toSet());
    }

    private Set<UUID> activeAdminsAndDirectors() {
        return jdbc
                .queryForList(
                        "select ra.user_id from role_assignment ra join app_user u on u.id = ra.user_id"
                                + " where ra.role in ('ADMIN', 'DIRECTOR') and u.active",
                        UUID.class)
                .stream()
                .collect(Collectors.toSet());
    }

    @Test
    void aTeacherWithAManagerNotifiesOnlyThatManager() {
        World w = newWorld();
        LocalDate mon = monday();
        UUID teacher = w.teacherA().teacherId();

        apply(w.teacherA(), mon, mon.plusDays(1));

        assertThat(recipients("LEAVE_REQUESTED", teacher)).containsExactly(w.managerA().signed().userId());
        var row = jdbc.queryForMap(
                "select title, message, link from notification where type = 'LEAVE_REQUESTED' and message like ?",
                nameOf(teacher) + "%");
        assertThat(row).containsEntry("title", "New leave request").containsEntry("link", "/operations/leave");
    }

    @Test
    void aSchoolWithoutAManagerNotifiesEveryActiveAdminAndDirectorButNotInactiveOnes() {
        World w = newWorld();
        String admin = w.admin();
        UUID[] bare = schoolInNewZone(admin);
        TeacherCtx teacher = newTeacher(admin, bare[2], 60);
        Signed inactive = signInAs(Role.ADMIN);
        jdbc.update("update app_user set active = false where id = ?", inactive.userId());
        LocalDate mon = monday();

        apply(teacher, mon, mon.plusDays(1));

        Set<UUID> got = recipients("LEAVE_REQUESTED", teacher.teacherId());
        assertThat(got).isNotEmpty().isEqualTo(activeAdminsAndDirectors());
        assertThat(got).doesNotContain(inactive.userId(), w.managerA().signed().userId());
    }

    @Test
    void cancellingAPendingRequestNotifiesTheSameRecipients() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));

        assertThat(post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of()).status()).isEqualTo(200);

        assertThat(recipients("LEAVE_CANCELLED", w.teacherA().teacherId()))
                .containsExactly(w.managerA().signed().userId());
        assertThat(jdbc.queryForObject(
                        "select title from notification where type = 'LEAVE_CANCELLED' and message like ?",
                        String.class,
                        nameOf(w.teacherA().teacherId()) + "%"))
                .isEqualTo("Leave request withdrawn");
    }

    @Test
    void cancellingAnApprovedRequestNotifiesTheSameRecipients() {
        World w = newWorld();
        LocalDate future = today().plusDays(10);
        String id = apply(w.teacherA(), future, future.plusDays(1));
        assertThat(post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0)).status())
                .isEqualTo(200);

        Resp cancelled = post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of());

        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(recipients("LEAVE_CANCELLED", w.teacherA().teacherId()))
                .containsExactly(w.managerA().signed().userId());
    }

    @Test
    void aFailedSubmitCreatesNoNotification() {
        World w = newWorld();
        LocalDate mon = monday();
        apply(w.teacherA(), mon, mon.plusDays(1));
        Integer before = jdbc.queryForObject("select count(*) from notification", Integer.class);

        Resp overlap = post(ME, w.teacherA().token(), draft(mon, mon.plusDays(1)));

        assertThat(overlap.status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from notification", Integer.class)).isEqualTo(before);
    }

    @Test
    void aLaterManagerReassignmentDoesNotChangeAStoredNotification() {
        World w = newWorld();
        LocalDate mon = monday();
        UUID teacher = w.teacherA().teacherId();
        apply(w.teacherA(), mon, mon.plusDays(1));

        assignZones(w.admin(), w.managerB().managerId(), w.zoneA(), w.zoneB());
        assignSchoolManager(w.admin(), w.schoolA(), w.managerB().managerId());

        assertThat(recipients("LEAVE_REQUESTED", teacher)).containsExactly(w.managerA().signed().userId());
    }

    @Test
    void twoManagersInDifferentSchoolsAreNotMixedUp() {
        World w = newWorld();
        LocalDate mon = monday();

        apply(w.teacherA(), mon, mon.plusDays(1));
        apply(w.teacherB(), mon, mon.plusDays(1));

        assertThat(recipients("LEAVE_REQUESTED", w.teacherA().teacherId()))
                .containsExactly(w.managerA().signed().userId());
        assertThat(recipients("LEAVE_REQUESTED", w.teacherB().teacherId()))
                .containsExactly(w.managerB().signed().userId());
        assertThat(jdbc.queryForObject(
                        "select count(*) from notification where recipient_user_id = ?",
                        Integer.class,
                        w.managerB().signed().userId()))
                .isEqualTo(1);
    }
}
