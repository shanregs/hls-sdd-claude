package com.hls.substitution;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.substitution.api.SubstituteCredit;
import com.hls.substitution.api.SubstituteCredits;
import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 017a US2, US3, US5: candidates, assignment, release, Manager cover, School informed and the credits. */
class SubstitutionWorkflowTest extends SubstitutionTestBase {

    @Autowired
    private SubstituteCredits credits;

    private UUID raise(World w, LocalDate day) {
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        return requirementId(w.teacherA().teacherId(), day);
    }

    @Test
    void candidatesShowWhoIsFreeAndWhyTheOthersAreNot() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        LocalDate day = monday();
        UUID id = raise(w, day);

        Resp resp = get(SUBS + "/" + id + "/candidates", w.managerA().token());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.body()).doesNotContain(w.teacherA().teacherId().toString());
        String body = resp.body();
        int benchAt = body.indexOf(bench.teacherId().toString());
        assertThat(body.substring(benchAt, body.indexOf('}', benchAt))).contains("\"available\":true");
        int placedAt = body.indexOf(w.teacherB().teacherId().toString());
        String placed = body.substring(placedAt, body.indexOf('}', placedAt));
        assertThat(placed).contains("\"available\":false").contains("Teaching at");
    }

    @Test
    void assigningAFreeTeacherCreditsThemNotTheAbsentOneAndTellsThem() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        LocalDate day = monday();
        UUID id = raise(w, day);

        Resp resp = assign(w.managerA().token(), id, bench.teacherId());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(status(id)).isEqualTo("ASSIGNED");
        List<SubstituteCredit> mine = credits.creditsOf(bench.teacherId(), YearMonth.from(day));
        assertThat(mine).extracting(SubstituteCredit::date).contains(day);
        assertThat(mine.get(0).absentTeacherId()).isEqualTo(w.teacherA().teacherId());
        assertThat(mine.get(0).schoolId()).isEqualTo(w.schoolA());
        assertThat(credits.creditsOf(w.teacherA().teacherId(), YearMonth.from(day))).isEmpty();
        assertThat(notifications(bench.signed().userId(), "SUBSTITUTE_ASSIGNED")).isEqualTo(1);
        Map<UUID, List<SubstituteCredit>> bulk =
                credits.creditsOf(List.of(bench.teacherId(), w.teacherA().teacherId()), day, day);
        assertThat(bulk).containsOnlyKeys(bench.teacherId());
    }

    @Test
    void anUnavailableTeacherIsRefusedEvenWhenCalledDirectly() {
        World w = newWorld();
        LocalDate day = monday();
        UUID id = raise(w, day);

        Resp busy = assign(w.managerA().token(), id, w.teacherB().teacherId());
        assertThat(busy.status()).isEqualTo(409);
        assertThat(busy.body()).contains("not available");

        Resp self = assign(w.managerA().token(), id, w.teacherA().teacherId());
        assertThat(self.status()).isEqualTo(409);
        assertThat(assign(w.managerA().token(), id, UUID.randomUUID()).status()).isEqualTo(404);
        assertThat(status(id)).isEqualTo("OPEN");
    }

    @Test
    void aStaleVersionIsRefused() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        UUID id = raise(w, monday());

        Resp stale = post(SUBS + "/" + id + "/assign", w.managerA().token(),
                Map.of("substituteTeacherId", bench.teacherId(), "version", 99));

        assertThat(stale.status()).isEqualTo(409);
    }

    @Test
    void aTeacherCannotCoverMoreThanOneDayButTwoHalfDaysAreFine() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        TeacherCtx colleague = colleagueOfA(w);
        LocalDate day = monday();
        UUID first = raise(w, day);
        absent(w.managerA().token(), colleague.teacherId(), day);
        UUID second = requirementId(colleague.teacherId(), day);

        assertThat(assign(w.managerA().token(), first, bench.teacherId()).status()).isEqualTo(200);
        Resp over = assign(w.managerA().token(), second, bench.teacherId());
        assertThat(over.status()).isEqualTo(409);
        assertThat(over.body()).contains("Already covering");

        // half days
        LocalDate next = day.plusDays(1);
        assertThat(markAbsent(w.managerA().token(), w.teacherA().teacherId(), next, 0.5).status()).isEqualTo(200);
        assertThat(markAbsent(w.managerA().token(), colleague.teacherId(), next, 0.5).status()).isEqualTo(200);
        assertThat(assign(w.managerA().token(), requirementId(w.teacherA().teacherId(), next), bench.teacherId()).status())
                .isEqualTo(200);
        assertThat(assign(w.managerA().token(), requirementId(colleague.teacherId(), next), bench.teacherId()).status())
                .isEqualTo(200);
        assertThat(credits.creditsOf(bench.teacherId(), YearMonth.from(next)).stream()
                        .filter(c -> c.date().equals(next))
                        .count())
                .isEqualTo(2);
    }

    @Test
    void reassigningEndsTheFirstCreditAndTellsBothTeachers() {
        World w = newWorld();
        TeacherCtx first = benchTeacher(w);
        TeacherCtx second = benchTeacher(w);
        LocalDate day = monday();
        UUID id = raise(w, day);
        assign(w.managerA().token(), id, first.teacherId());
        assertThat(inform(w.managerA().token(), id).status()).isEqualTo(200);

        assertThat(assign(w.managerA().token(), id, second.teacherId()).status()).isEqualTo(200);

        assertThat(credits.creditsOf(first.teacherId(), YearMonth.from(day))).isEmpty();
        assertThat(credits.creditsOf(second.teacherId(), YearMonth.from(day))).hasSize(1);
        assertThat(notifications(first.signed().userId(), "SUBSTITUTE_ENDED")).isEqualTo(1);
        assertThat(notifications(second.signed().userId(), "SUBSTITUTE_ASSIGNED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select informed_at is null from substitution_requirement where id = ?", Boolean.class, id))
                .as("a new outcome needs the School told again")
                .isTrue();
        assertThat(jdbc.queryForList("select action from substitution_history where requirement_id = ? order by at, id", String.class, id))
                .containsExactly("RAISED", "ASSIGNED", "INFORMED", "REASSIGNED");
    }

    @Test
    void releaseNeedsAReasonAndEndsTheCredit() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        LocalDate day = monday();
        UUID id = raise(w, day);
        assertThat(release(w.managerA().token(), id, "x").status()).as("already open").isEqualTo(409);
        assign(w.managerA().token(), id, bench.teacherId());

        assertThat(release(w.managerA().token(), id, " ").status()).isEqualTo(400);
        Resp released = release(w.managerA().token(), id, "Teacher is back");

        assertThat(released.status()).as(released.body()).isEqualTo(200);
        assertThat(status(id)).isEqualTo("OPEN");
        assertThat(credits.creditsOf(bench.teacherId(), YearMonth.from(day))).isEmpty();
        assertThat(notifications(bench.signed().userId(), "SUBSTITUTE_ENDED")).isEqualTo(1);
    }

    @Test
    void theManagerCoversWithNoCreditAndCanStillAssignLater() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        LocalDate day = monday();
        UUID id = raise(w, day);

        Resp covered = cover(w.managerA().token(), id);

        assertThat(covered.status()).as(covered.body()).isEqualTo(200);
        assertThat(status(id)).isEqualTo("MANAGER_COVER");
        assertThat(covered.body()).contains("\"coverName\":\"");
        assertThat(jdbc.queryForObject("select cover_user_id from substitution_requirement where id = ?", UUID.class, id))
                .isEqualTo(w.managerA().signed().userId());
        assertThat(credits.creditsOf(List.of(bench.teacherId(), w.teacherA().teacherId()), day, day)).isEmpty();
        assertThat(cover(w.managerA().token(), id).status()).as("already covering").isEqualTo(409);

        assertThat(assign(w.managerA().token(), id, bench.teacherId()).status()).isEqualTo(200);
        assertThat(credits.creditsOf(bench.teacherId(), YearMonth.from(day))).hasSize(1);
    }

    @Test
    void whenAnAdminRecordsCoverTheSchoolsManagerIsTheCoveringPerson() {
        World w = newWorld();
        UUID id = raise(w, monday());

        assertThat(cover(w.admin(), id).status()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select cover_user_id from substitution_requirement where id = ?", UUID.class, id))
                .isEqualTo(w.managerA().signed().userId());
    }

    @Test
    void theSchoolCanOnlyBeInformedAfterAnOutcomeAndOnlyOncePerOutcome() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        UUID id = raise(w, monday());

        Resp early = inform(w.managerA().token(), id);
        assertThat(early.status()).isEqualTo(409);

        assign(w.managerA().token(), id, bench.teacherId());
        assertThat(post(SUBS + "/" + id + "/inform", w.managerA().token(),
                        Map.of("contact", " ", "method", "PHONE", "version", version(id))).status())
                .isEqualTo(400);
        assertThat(post(SUBS + "/" + id + "/inform", w.managerA().token(),
                        Map.of("contact", "P", "method", "PIGEON", "version", version(id))).status())
                .isEqualTo(400);
        assertThat(notifications(w.managerA().signed().userId(), "SUBSTITUTE_SCHOOL_INFORMED")).isZero();

        Resp done = inform(w.admin(), id);
        assertThat(done.status()).as(done.body()).isEqualTo(200);
        assertThat(done.body()).contains("\"method\":\"PHONE\"").contains("Principal");
        assertThat(inform(w.managerA().token(), id).status()).isEqualTo(409);
        assertThat(notifications(w.managerA().signed().userId(), "SUBSTITUTE_SCHOOL_INFORMED"))
                .as("told because somebody else recorded it")
                .isEqualTo(1);
    }

    @Test
    void aManagerWhoInformsTheSchoolIsNotNotifiedOfTheirOwnAction() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        UUID id = raise(w, monday());
        assign(w.managerA().token(), id, bench.teacherId());

        inform(w.managerA().token(), id);

        assertThat(notifications(w.managerA().signed().userId(), "SUBSTITUTE_SCHOOL_INFORMED")).isZero();
    }

    @Test
    void aWithdrawnAbsenceVoidsTheCreditTellsTheSubstituteAndBlocksFurtherChanges() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        LocalDate day = monday();
        UUID id = raise(w, day);
        assign(w.managerA().token(), id, bench.teacherId());

        markAs(w.managerA().token(), w.teacherA().teacherId(), day, "P", 1);

        assertThat(status(id)).isEqualTo("CANCELLED");
        assertThat(credits.creditsOf(bench.teacherId(), YearMonth.from(day))).isEmpty();
        assertThat(notifications(bench.signed().userId(), "SUBSTITUTE_ENDED")).isEqualTo(1);
        assertThat(assign(w.managerA().token(), id, bench.teacherId()).status()).isEqualTo(409);
        assertThat(cover(w.managerA().token(), id).status()).isEqualTo(409);
        assertThat(get(SUBS + "/" + id + "/candidates", w.managerA().token()).body()).isEqualTo("[]");
    }

    @Test
    void historyRowsCannotBeChangedOrRemoved() {
        World w = newWorld();
        UUID id = raise(w, monday());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("update substitution_history set note = 'x' where requirement_id = ?", id))
                .hasMessageContaining("append-only");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("delete from substitution_history where requirement_id = ?", id))
                .hasMessageContaining("append-only");
    }

    @Test
    void everyChangeIsAudited() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        UUID id = raise(w, monday());
        assign(w.managerA().token(), id, bench.teacherId());
        inform(w.managerA().token(), id);

        assertChangeRecorded(w.admin(), "SUBSTITUTION_REQUIREMENT", id, "created");
        assertChangeRecorded(w.admin(), "SUBSTITUTION_REQUIREMENT", id, "status");
        assertChangeRecorded(w.admin(), "SUBSTITUTION_REQUIREMENT", id, "substitute");
        assertChangeRecorded(w.admin(), "SUBSTITUTION_REQUIREMENT", id, "schoolInformed");
    }
}
