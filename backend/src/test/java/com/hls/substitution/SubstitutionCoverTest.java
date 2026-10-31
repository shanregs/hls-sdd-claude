package com.hls.substitution;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.substitution.api.SubstitutionCover;
import com.hls.substitution.api.SubstitutionCover.SchoolCover;
import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 014b A3: cover by School equals the Substitutions list for the same School and range; cancelled ones count for nothing. */
class SubstitutionCoverTest extends SubstitutionTestBase {

    @Autowired
    private SubstitutionCover cover;

    private void requirement(UUID teacher, UUID school, LocalDate day, String status, String dayValue, UUID substitute) {
        jdbc.update(
                "insert into substitution_requirement (id, teacher_id, school_id, duty_date, day_value, kind, status,"
                        + " substitute_teacher_id, cover_user_id, raised_at, updated_at, version)"
                        + " values (gen_random_uuid(), ?, ?, ?, ?::numeric, 'ABSENT', ?, ?, null, now(), now(), 0)",
                teacher, school, day, dayValue, status, substitute);
    }

    private long listed(String token, String status, UUID school, LocalDate from, LocalDate to) {
        return ((Number) get(SUBS + "?status=" + status + "&from=" + from + "&to=" + to + "&size=1&schoolId=" + school, token)
                        .map().get("totalElements")).longValue();
    }

    @Test
    void coverSplitsDaysBySubstituteManagerAndOpenAndLeavesCancelledOut() {
        World w = newWorld();
        LocalDate from = today().minusDays(3);
        LocalDate to = today();
        requirement(w.teacherA().teacherId(), w.schoolA(), from, "ASSIGNED", "1.0", w.teacherB().teacherId());
        requirement(newTeacher(w.admin(), w.schoolA(), 60).teacherId(), w.schoolA(), from.plusDays(1), "ASSIGNED", "0.5", w.teacherB().teacherId());
        requirement(newTeacher(w.admin(), w.schoolA(), 60).teacherId(), w.schoolA(), from.plusDays(1), "MANAGER_COVER", "1.0", null);
        requirement(newTeacher(w.admin(), w.schoolA(), 60).teacherId(), w.schoolA(), to, "OPEN", "1.0", null);
        requirement(newTeacher(w.admin(), w.schoolA(), 60).teacherId(), w.schoolA(), to, "CANCELLED", "1.0", null);
        requirement(w.teacherB().teacherId(), w.schoolB(), to, "OPEN", "0.5", null);

        Map<UUID, SchoolCover> result = cover.coverBySchool(Set.of(w.schoolA(), w.schoolB()), from, to);

        SchoolCover a = result.get(w.schoolA());
        assertThat(a.bySubstitute()).isEqualByComparingTo("1.5");
        assertThat(a.byManager()).isEqualByComparingTo("1.0");
        assertThat(a.open()).isEqualByComparingTo("1.0");
        assertThat(result.get(w.schoolB()).open()).isEqualByComparingTo("0.5");
        // the same requirements as the Substitutions screen lists (one entry per requirement there)
        assertThat(listed(w.admin(), "ASSIGNED", w.schoolA(), from, to)).isEqualTo(2);
        assertThat(listed(w.admin(), "MANAGER_COVER", w.schoolA(), from, to)).isEqualTo(1);
        assertThat(listed(w.admin(), "OPEN", w.schoolA(), from, to)).isEqualTo(1);
    }

    @Test
    void aSchoolWithoutRequirementsAndAnEmptySetHaveNoEntry() {
        World w = newWorld();
        assertThat(cover.coverBySchool(Set.of(w.schoolA()), today().minusDays(3), today())).doesNotContainKey(w.schoolA());
        assertThat(cover.coverBySchool(List.of(), today().minusDays(3), today())).isEmpty();
        assertThat(cover.coverBySchool(Set.of(w.schoolA()), today(), today().minusDays(1))).isEmpty();
    }
}
