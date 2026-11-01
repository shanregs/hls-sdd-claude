package com.hls.substitution.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 017b US6 (T045, T046): existing credits become day records at go-live, once, and the odd ones are listed. */
class MigrationTest extends SubstitutionTestBase {

    private static final String MIGRATE = "/api/v1/substitute-payouts/migration/run";

    /** A credit as spec 017a left it: an assigned requirement with no day record (the day row is removed). */
    private UUID creditWithoutDayRecord(World w, TeacherCtx substitute) {
        LocalDate day = monday();
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        UUID requirement = requirementId(w.teacherA().teacherId(), day);
        assertThat(assign(w.managerA().token(), requirement, substitute.teacherId()).status()).isEqualTo(200);
        jdbc.update("delete from substitute_day where requirement_id = ?", requirement);
        return requirement;
    }

    @Test
    void aPastCreditBecomesAPresentMigratedDayFlaggedForReviewAndASecondRunChangesNothing() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);
        UUID requirement = creditWithoutDayRecord(w, substitute);

        Resp first = post(MIGRATE, w.admin(), Map.of());

        assertThat(first.status()).as(first.body()).isEqualTo(200);
        Map<String, Object> day = jdbc.queryForMap(
                "select * from substitute_day where requirement_id = ? and substitute_teacher_id = ?",
                requirement, substitute.teacherId());
        assertThat(day.get("attendance")).isEqualTo("PRESENT");
        assertThat(day.get("source")).isEqualTo("MIGRATED");
        assertThat(day.get("needs_review")).isEqualTo(true);
        assertThat(day.get("zone_id")).isEqualTo(w.zoneA());
        assertThat(jdbc.queryForObject(
                        "select count(*) from substitute_day_event where day_id = ? and field = 'MIGRATED'",
                        Integer.class, day.get("id")))
                .isEqualTo(1);

        Resp second = post(MIGRATE, w.admin(), Map.of());
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body()).contains("\"migrated\":0");
        assertThat(jdbc.queryForObject(
                        "select count(*) from substitute_day where requirement_id = ?", Integer.class, requirement))
                .as("no double count")
                .isEqualTo(1);
    }

    @Test
    void aCreditOfSomeoneWhoIsNotASubstituteIsListedSettleFirstAndNotCreated() {
        World w = newWorld();
        TeacherCtx notASubstitute = benchTeacher(w);
        UUID requirement = creditWithoutDayRecord(w, notASubstitute);
        jdbc.update("update teacher set engagement = 'REGULAR' where id = ?", notASubstitute.teacherId());

        Resp resp = post(MIGRATE, w.admin(), Map.of());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.body()).contains(notASubstitute.teacherId().toString());
        assertThat(jdbc.queryForObject(
                        "select count(*) from substitute_day where requirement_id = ?", Integer.class, requirement))
                .isZero();
    }

    @Test
    void onlyAnAdminMayRunIt() {
        World w = newWorld();

        assertThat(post(MIGRATE, w.director(), Map.of()).status()).as("Director").isEqualTo(403);
        assertThat(post(MIGRATE, w.managerA().token(), Map.of()).status()).as("Manager").isEqualTo(403);
        assertThat(post(MIGRATE, w.teacherA().token(), Map.of()).status()).as("Teacher").isEqualTo(403);
        assertThat(post(MIGRATE, signInAs(Role.SYSTEM).token(), Map.of()).status()).as("System").isEqualTo(403);
    }
}
