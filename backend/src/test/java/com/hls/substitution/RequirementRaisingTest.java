package com.hls.substitution;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 017a US1: an absence raises one requirement by itself and the Manager is told once. */
class RequirementRaisingTest extends SubstitutionTestBase {

    @Test
    void anAbsenceRaisesOneOpenRequirementAndAlertsTheSchoolsManagerOnce() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();

        absent(w.managerA().token(), teacher, day);

        var row = requirementRow(teacher, day);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("school_id")).isEqualTo(w.schoolA());
        assertThat(row.get("kind")).isEqualTo("ABSENT");
        assertThat((java.math.BigDecimal) row.get("day_value")).isEqualByComparingTo("1");
        UUID managerUser = w.managerA().signed().userId();
        assertThat(notifications(managerUser, "SUBSTITUTE_NEEDED")).isEqualTo(1);
        String link = jdbc.queryForObject(
                "select link from notification where recipient_user_id = ? and type = 'SUBSTITUTE_NEEDED'", String.class, managerUser);
        assertThat(link).isEqualTo("/operations/substitutions/" + row.get("id"));
        assertThat(notifications(w.managerB().signed().userId(), "SUBSTITUTE_NEEDED")).isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from substitution_history where requirement_id = ? and action = 'RAISED'",
                        Integer.class,
                        row.get("id")))
                .isEqualTo(1);
    }

    @Test
    void theTeacherMarkingThemselvesAbsentRaisesItToo() {
        World w = newWorld();
        LocalDate day = today();

        Resp resp = put("/api/v1/attendance/me/marks/" + day, w.teacherA().token(), java.util.Map.of("statusCode", "A", "dayValue", 1));

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        if (!java.time.DayOfWeek.SUNDAY.equals(day.getDayOfWeek())) {
            assertThat(requirementRows(w.teacherA().teacherId(), day)).hasSize(1);
        }
    }

    @Test
    void repeatingTheSameAbsenceDoesNotRaiseOrAlertAgain() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();

        absent(w.managerA().token(), teacher, day);
        absent(w.managerA().token(), teacher, day);

        assertThat(requirementRows(teacher, day)).hasSize(1);
        assertThat(notifications(w.managerA().signed().userId(), "SUBSTITUTE_NEEDED")).isEqualTo(1);
    }

    @Test
    void aHalfDayRaisesAHalfDayRequirementAndChangingItUpdatesWithoutANewAlert() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();

        assertThat(markAbsent(w.managerA().token(), teacher, day, 0.5).status()).isEqualTo(200);
        assertThat((java.math.BigDecimal) requirementRow(teacher, day).get("day_value")).isEqualByComparingTo("0.5");

        Resp whole = put(
                "/api/v1/attendance/teachers/" + teacher + "/marks/" + day,
                w.managerA().token(),
                java.util.Map.of("statusCode", "A", "dayValue", 1, "version", 0));
        assertThat(whole.status()).as(whole.body()).isEqualTo(200);

        assertThat((java.math.BigDecimal) requirementRow(teacher, day).get("day_value")).isEqualByComparingTo("1");
        assertThat(notifications(w.managerA().signed().userId(), "SUBSTITUTE_NEEDED")).isEqualTo(1);
    }

    @Test
    void presentDaysRaiseNothing() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();

        markAs(w.managerA().token(), teacher, day, "P", 1);

        assertThat(requirementRows(teacher, day)).isEmpty();
        assertThat(notifications(w.managerA().signed().userId(), "SUBSTITUTE_NEEDED")).isZero();
    }

    @Test
    void aWithdrawnAbsenceCancelsTheRequirementAndAnotherOneReopensTheSameRow() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();
        absent(w.managerA().token(), teacher, day);
        UUID id = requirementId(teacher, day);

        markAs(w.managerA().token(), teacher, day, "P", 1);
        assertThat(status(id)).isEqualTo("CANCELLED");

        absent(w.managerA().token(), teacher, day);
        assertThat(requirementId(teacher, day)).isEqualTo(id);
        assertThat(status(id)).isEqualTo("OPEN");
        assertThat(notifications(w.managerA().signed().userId(), "SUBSTITUTE_NEEDED")).isEqualTo(2);
        assertThat(jdbc.queryForList("select action from substitution_history where requirement_id = ? order by at, id", String.class, id))
                .containsExactly("RAISED", "CANCELLED", "REOPENED");
    }

    @Test
    void aSchoolWithoutAnActiveManagerAlertsEveryAdminAndDirector() {
        String admin = signInAs(Role.ADMIN).token();
        Signed otherAdmin = signInAs(Role.ADMIN);
        Signed director = signInAs(Role.DIRECTOR);
        UUID[] school = schoolInNewZone(admin);
        TeacherCtx teacher = newTeacher(admin, school[2], 60);
        LocalDate day = monday();

        absent(admin, teacher.teacherId(), day);

        assertThat(notifications(otherAdmin.userId(), "SUBSTITUTE_NEEDED")).isEqualTo(1);
        assertThat(notifications(director.userId(), "SUBSTITUTE_NEEDED")).isEqualTo(1);
    }

    @Test
    void approvedLeaveRaisesOneRequirementPerWorkingDayAndRevokingCancelsThemAll() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(2));

        Resp approved = post(SUP + "/" + id + "/approve", w.managerA().token(), java.util.Map.of("version", 0));
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);

        for (int i = 0; i < 3; i++) {
            var row = requirementRow(teacher, mon.plusDays(i));
            assertThat(row.get("kind")).isEqualTo("LEAVE");
            assertThat(row.get("status")).isEqualTo("OPEN");
        }
        assertThat(notifications(w.managerA().signed().userId(), "SUBSTITUTE_NEEDED")).isEqualTo(3);

        Resp revoked = post(SUP + "/" + id + "/revoke", w.managerA().token(), java.util.Map.of("reason", "Needed", "version", 1));
        assertThat(revoked.status()).as(revoked.body()).isEqualTo(200);
        for (int i = 0; i < 3; i++) {
            assertThat(requirementRow(teacher, mon.plusDays(i)).get("status")).isEqualTo("CANCELLED");
        }
    }

    @Test
    void aWeeklyOffDayRaisesNothing() {
        World w = newWorld();
        LocalDate sunday = monday().minusDays(1);

        assertThat(markAbsent(w.managerA().token(), w.teacherA().teacherId(), sunday, 1).status()).isEqualTo(200);

        assertThat(requirementRows(w.teacherA().teacherId(), sunday)).isEmpty();
    }
}
