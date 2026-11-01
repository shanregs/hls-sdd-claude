package com.hls.substitution.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 017b US5 (T047, T048): the repair job fixes what a missed event left, once, and never twice. */
class ReconcileTest extends SubstitutionTestBase {

    private static final String PAYOUTS = "/api/v1/substitute-payouts";

    @Autowired
    private PayoutReconcileJob job;

    private UUID allocate(World w, TeacherCtx substitute) {
        LocalDate day = monday();
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        UUID requirement = requirementId(w.teacherA().teacherId(), day);
        assertThat(assign(w.managerA().token(), requirement, substitute.teacherId()).status()).isEqualTo(200);
        return requirement;
    }

    private Resp reconcile(String token, LocalDate date) {
        return post(PAYOUTS + "/reconcile?month=" + YearMonth.from(date), token, Map.of());
    }

    private int days(UUID requirement) {
        return jdbc.queryForObject("select count(*) from substitute_day where requirement_id = ?", Integer.class, requirement);
    }

    @Test
    void aMissingDayRecordIsRecreatedOnceAndASecondRunChangesNothing() {
        World w = newWorld();
        UUID requirement = allocate(w, benchTeacher(w));
        jdbc.update("delete from substitute_day where requirement_id = ?", requirement);

        Resp first = reconcile(w.admin(), monday());

        assertThat(first.status()).as(first.body()).isEqualTo(200);
        assertThat(days(requirement)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from substitute_day_event e join substitute_day d on d.id = e.day_id"
                                + " where d.requirement_id = ? and e.cause = 'JOB'",
                        Integer.class, requirement))
                .as("the repair is an event with the cause JOB")
                .isGreaterThanOrEqualTo(1);

        Resp second = reconcile(w.admin(), monday());
        assertThat(second.body()).contains("\"created\":0").contains("\"cancelled\":0");
        assertThat(days(requirement)).isEqualTo(1);
    }

    @Test
    void aDayWhoseAllocationEndedIsCancelledAndCannotBeMarked() {
        World w = newWorld();
        TeacherCtx substitute = benchTeacher(w);
        UUID requirement = allocate(w, substitute);
        UUID day = jdbc.queryForObject("select id from substitute_day where requirement_id = ?", UUID.class, requirement);
        // the end event was missed: the requirement is released but the day still reads live
        jdbc.update("update substitution_requirement set status = 'OPEN', substitute_teacher_id = null where id = ?", requirement);
        assertThat(jdbc.queryForObject("select live from substitute_day where id = ?", Boolean.class, day)).isTrue();

        Resp resp = reconcile(w.admin(), monday());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select live from substitute_day where id = ?", Boolean.class, day)).isFalse();
        long version = jdbc.queryForObject("select version from substitute_day where id = ?", Long.class, day);
        Resp marked = put(PAYOUTS + "/days/" + day + "/attendance", w.managerA().token(),
                Map.of("attendance", "PRESENT", "version", version));
        assertThat(marked.status()).isEqualTo(409);
    }

    @Test
    void aStatusThatDriftedIsWorkedOutAgain() {
        World w = newWorld();
        UUID requirement = allocate(w, benchTeacher(w));
        UUID day = jdbc.queryForObject("select id from substitute_day where requirement_id = ?", UUID.class, requirement);
        jdbc.update("update substitute_day set attendance = 'PRESENT', marked_by = ?, marked_at = now(), status = 'ALLOCATED'"
                        + " where id = ?", UUID.randomUUID(), day);

        Resp resp = reconcile(w.admin(), monday());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from substitute_day where id = ?", String.class, day))
                .isEqualTo("ATTENDED");
    }

    @Test
    void onlyTheAdminAndTheDirectorMayRepair() {
        World w = newWorld();

        assertThat(reconcile(w.managerA().token(), monday()).status()).as("Manager").isEqualTo(403);
        assertThat(reconcile(w.teacherA().token(), monday()).status()).as("Teacher").isEqualTo(403);
        assertThat(reconcile(w.director(), monday()).status()).as("Director").isEqualTo(200);
        assertThat(reconcile(w.admin(), monday()).status()).as("Admin").isEqualTo(200);
    }

    @Test
    void twoRunsAtOnceNeverCreateAnExtraDay() throws Exception {
        World w = newWorld();
        UUID requirement = allocate(w, benchTeacher(w));
        jdbc.update("delete from substitute_day where requirement_id = ?", requirement);
        YearMonth month = YearMonth.from(monday());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<PayoutReconcileJob.Result> run = () -> job.runForMonth(month);
            List<Future<PayoutReconcileJob.Result>> both = pool.invokeAll(List.of(run, run));
            for (Future<PayoutReconcileJob.Result> f : both) {
                assertThat(f.get()).isNotNull();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(days(requirement)).isEqualTo(1);
    }
}
