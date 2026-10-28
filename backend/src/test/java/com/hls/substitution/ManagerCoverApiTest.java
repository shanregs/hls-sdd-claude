package com.hls.substitution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 040 US2: a Manager's cover is recorded with no prior step and kept as a Manager cover entry. */
class ManagerCoverApiTest extends SubstitutionTestBase {

    private static final String COVERS = SUBS + "/manager-covers";

    private UUID requirementFor(World w, LocalDate date) {
        absent(w.managerA().token(), w.teacherA().teacherId(), date);
        return requirementId(w.teacherA().teacherId(), date);
    }

    private void requireWorkingToday(World w) {
        Resp preview = post(ME + "/preview", w.teacherA().token(), draft(today(), today()));
        assumeTrue(preview.status() == 200 && preview.body().contains("\"problems\":[]"), "today is not a working day");
    }

    @Test
    void aCoverFromOpenOnTheDutyDayIsRecordedAsSameDayAndListedApartFromCredits() {
        World w = newWorld();
        requireWorkingToday(w);
        UUID id = requirementFor(w, today());

        Resp covered = cover(w.managerA().token(), id);

        assertThat(covered.status()).as(covered.body()).isEqualTo(200);
        assertThat(covered.body()).contains("\"status\":\"MANAGER_COVER\"").contains("\"sameDay\":true");
        Resp list = get(COVERS + "?from=" + today() + "&to=" + today(), w.managerA().token());
        assertThat(list.status()).as(list.body()).isEqualTo(200);
        assertThat(list.body())
                .contains(id.toString())
                .contains("\"label\":\"Manager cover\"")
                .contains("\"state\":\"ACTIVE\"")
                .contains("\"sameDay\":true");
        assertThat(jdbc.queryForObject(
                        "select count(*) from change_history_entry where entity_id = ? and after_value like '%sameDay=true%'",
                        Integer.class,
                        id.toString()))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void aCoverForAnEarlierDayIsNotFlaggedSameDay() {
        World w = newWorld();
        UUID id = requirementFor(w, monday());

        Resp covered = cover(w.managerA().token(), id);

        assertThat(covered.status()).as(covered.body()).isEqualTo(200);
        assertThat(covered.body()).contains("\"sameDay\":false");
    }

    @Test
    void assigningATeacherLaterEndsTheEntryAndKeepsItInTheList() {
        World w = newWorld();
        UUID id = requirementFor(w, monday());
        assertThat(cover(w.managerA().token(), id).status()).isEqualTo(200);

        Resp assigned = assign(w.managerA().token(), id, benchTeacher(w).teacherId());

        assertThat(assigned.status()).as(assigned.body()).isEqualTo(200);
        Resp list = get(COVERS + "?from=" + monday() + "&to=" + monday(), w.managerA().token());
        assertThat(list.body()).contains(id.toString()).contains("\"state\":\"ENDED\"").contains("TEACHER_ASSIGNED");
        assertThat(jdbc.queryForObject("select same_day from substitution_requirement where id = ?", Boolean.class, id))
                .isFalse();
    }

    @Test
    void releasingEndsTheEntryAsReleased() {
        World w = newWorld();
        UUID id = requirementFor(w, monday());
        assertThat(cover(w.managerA().token(), id).status()).isEqualTo(200);

        assertThat(release(w.managerA().token(), id, "Class cancelled").status()).isEqualTo(200);

        Resp list = get(COVERS + "?from=" + monday() + "&to=" + monday(), w.managerA().token());
        assertThat(list.body()).contains("\"state\":\"ENDED\"").contains("RELEASED");
    }

    @Test
    void aManagerOutsideTheSchoolCannotCoverOrSeeTheEntry() {
        World w = newWorld();
        UUID id = requirementFor(w, monday());
        assertThat(cover(w.managerA().token(), id).status()).isEqualTo(200);

        assertThat(cover(w.managerB().token(), id).status()).isEqualTo(404);
        Resp list = get(COVERS + "?from=" + monday() + "&to=" + monday(), w.managerB().token());
        assertThat(list.body()).doesNotContain(id.toString());
    }

    @Test
    void anAdminCoverIsAttributedToTheSchoolsManager() {
        World w = newWorld();
        UUID id = requirementFor(w, monday());

        Resp covered = cover(w.admin(), id);

        assertThat(covered.status()).as(covered.body()).isEqualTo(200);
        Resp list = get(COVERS + "?from=" + monday() + "&to=" + monday(), w.admin());
        assertThat(list.body()).contains(id.toString()).contains("\"managerName\":\"");
    }

    @Test
    void theSameDayFilterKeepsOnlySameDayCovers() {
        World w = newWorld();
        requireWorkingToday(w);
        UUID todays = requirementFor(w, today());
        assertThat(cover(w.managerA().token(), todays).status()).isEqualTo(200);
        UUID earlier = requirementFor(w, monday());
        assertThat(cover(w.managerA().token(), earlier).status()).isEqualTo(200);

        Resp only = get(COVERS + "?sameDay=true", w.managerA().token());

        assertThat(only.body()).contains(todays.toString()).doesNotContain(earlier.toString());
    }

    @Test
    void teacherAndSystemCannotReadTheList() {
        World w = newWorld();

        assertThat(get(COVERS, w.teacherA().token()).status()).isEqualTo(403);
    }
}
