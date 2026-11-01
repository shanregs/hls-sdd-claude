package com.hls.substitution.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.audit.support.AuditVisibility;
import com.hls.identity.user.Role;
import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 017b T049 (Principle I): every mark, lock and engagement change is in the Change History with old and new values. */
class PayoutAuditTest extends SubstitutionTestBase {

    private static final String PAYOUTS = "/api/v1/substitute-payouts";

    @Autowired
    private AuditVisibility visibility;

    /** The Change History is written after the request returns, so wait for the expected number of entries. */
    private List<Map<String, Object>> history(String type, Object id, int atLeast) {
        await().atMost(java.time.Duration.ofSeconds(15))
                .until(() -> history(type, id).size() >= atLeast);
        return history(type, id);
    }

    private List<Map<String, Object>> history(String type, Object id) {
        return jdbc.queryForList(
                "select actor_user_id, field, before_value, after_value from change_history_entry"
                        + " where entity_type = ? and entity_id = ? order by occurred_at",
                type, String.valueOf(id));
    }

    @Test
    void markingADayWritesTheOldAndNewValueAndWhoDidIt() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);
        LocalDate day = monday();
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        UUID requirement = requirementId(w.teacherA().teacherId(), day);
        assign(w.managerA().token(), requirement, substitute.teacherId());
        UUID dayId = jdbc.queryForObject("select id from substitute_day where requirement_id = ?", UUID.class, requirement);
        long version = jdbc.queryForObject("select version from substitute_day where id = ?", Long.class, dayId);

        assertThat(put(PAYOUTS + "/days/" + dayId + "/attendance", w.managerA().token(),
                        Map.of("attendance", "PRESENT", "version", version))
                .status()).isEqualTo(200);

        List<Map<String, Object>> entries = history("SUBSTITUTE_DAY", dayId, 1);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("field")).isEqualTo("attendance");
        assertThat(entries.get(0).get("before_value")).isEqualTo("NOT_MARKED");
        assertThat(entries.get(0).get("after_value")).isEqualTo("PRESENT");
        assertThat(entries.get(0).get("actor_user_id")).isEqualTo(w.managerA().signed().userId());
    }

    @Test
    void lockingAndReopeningAMonthAreInTheHistory() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);
        YearMonth month = YearMonth.from(today()).minusMonths(1);

        assertThat(post(PAYOUTS + "/months/" + substitute.teacherId() + "/" + month + "/lock", w.admin(), Map.of()).status())
                .isEqualTo(200);
        assertThat(post(PAYOUTS + "/months/" + substitute.teacherId() + "/" + month + "/reopen", w.admin(),
                        Map.of("reason", "Wrong day marked"))
                .status()).isEqualTo(200);

        List<Map<String, Object>> entries = history("SUBSTITUTE_MONTH", substitute.teacherId(), 2);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).get("after_value")).isEqualTo("locked");
        assertThat(entries.get(1).get("after_value").toString()).contains("Wrong day marked");
    }

    @Test
    void anEngagementChangeIsInTheTeachersHistory() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);

        List<Map<String, Object>> entries = history("TEACHER", substitute.teacherId(), 4);

        assertThat(entries).anySatisfy(e -> {
            assertThat(e.get("field")).isEqualTo("engagement");
            assertThat(e.get("before_value")).isEqualTo("REGULAR");
            assertThat(e.get("after_value")).isEqualTo("SUBSTITUTE");
        });
    }

    @Test
    void theHistoryOfDaysAndRatesIsHiddenFromWhoCannotSeeThem() {
        assertThat(visibility.hiddenEntityTypes(Set.of(Role.TEACHER)))
                .contains("SUBSTITUTE_DAY", "SUBSTITUTE_MONTH", "DESIGNATION_DAY_RATE");
        assertThat(visibility.hiddenEntityTypes(Set.of(Role.SYSTEM)))
                .contains("SUBSTITUTE_DAY", "SUBSTITUTE_MONTH", "DESIGNATION_DAY_RATE");
        assertThat(visibility.hiddenEntityTypes(Set.of(Role.MANAGER)))
                .contains("DESIGNATION_DAY_RATE")
                .doesNotContain("SUBSTITUTE_DAY", "SUBSTITUTE_MONTH");
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR}) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role)))
                    .as(role.name())
                    .doesNotContain("SUBSTITUTE_DAY", "SUBSTITUTE_MONTH", "DESIGNATION_DAY_RATE");
        }
    }
}
