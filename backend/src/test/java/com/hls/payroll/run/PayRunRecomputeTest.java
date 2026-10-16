package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 013b T019: compute again changes only the lines whose own inputs changed, and says what changed (FR-008). */
class PayRunRecomputeTest extends PayrollRunTestBase {

    @BeforeEach
    void clean() {
        resetRuns();
    }

    /** Asks for a recompute and waits until that computation is DONE. */
    private Map<String, Object> recompute(String token, UUID run) {
        Resp resp = post(RUNS + "/" + run + "/recompute", token, Map.of());
        assertThat(resp.status()).as(resp.body()).isEqualTo(202);
        Map<String, Object> done = awaitComputed(token, run);
        assertThat(done.get("computeState")).isEqualTo("DONE");
        return done;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> section(Map<String, Object> run, String name) {
        Map<String, Object> summary = (Map<String, Object>) run.get("recomputeSummary");
        assertThat(summary).as("recompute summary of " + run).isNotNull();
        return (List<Map<String, Object>>) summary.get(name);
    }

    private static boolean mentions(List<Map<String, Object>> entries, UUID person) {
        return entries.stream().anyMatch(e -> person.toString().equals(e.get("personId")));
    }

    @Test
    void lockingTheMissingMonthAndComputingAgainAddsThatLineAndLeavesEveryOtherRowUntouched() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID waiting = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, waiting, "20000", LocalDate.now().minusDays(300));
        UUID steady = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, steady, "22000", LocalDate.now().minusDays(300));
        lockTeacherMonth(steady);
        UUID run = startAndCompute(admin);
        Map<String, Object> waitingBefore = lineOf(admin, run, waiting);
        Map<String, Object> steadyBefore = lineOf(admin, run, steady);
        assertThat(waitingBefore.get("state")).isEqualTo("CANNOT_PAY");
        List<Map<String, Object>> everyoneBefore = allLines(admin, run);

        lockTeacherMonth(waiting);
        Map<String, Object> after = recompute(admin, run);

        Map<String, Object> waitingAfter = lineOf(admin, run, waiting);
        assertThat(waitingAfter.get("state")).isEqualTo("PAYABLE");
        assertThat(waitingAfter.get("id")).as("the row is kept, only its content changes").isEqualTo(waitingBefore.get("id"));
        assertThat(new BigDecimal((String) waitingAfter.get("net"))).isEqualByComparingTo("20000");
        // every other line: the same row, the same time it was computed, the same figures
        Map<String, Object> steadyAfter = lineOf(admin, run, steady);
        assertThat(steadyAfter).isEqualTo(steadyBefore);
        for (Map<String, Object> before : everyoneBefore) {
            if (!before.get("id").equals(waitingBefore.get("id"))) {
                Map<String, Object> now = lineOf(admin, run, UUID.fromString((String) before.get("personId")));
                assertThat(now).isEqualTo(before);
            }
        }
        // the diff names the person who moved, with the reason
        assertThat(mentions(section(after, "moved"), waiting)).isTrue();
        assertThat(mentions(section(after, "changed"), steady)).isFalse();
        assertThat(section(after, "added")).isEmpty();
        assertThat(section(after, "removed")).isEmpty();
        assertThat(get(RUNS + "/" + run, admin).body()).contains("RECOMPUTED");
    }

    @Test
    void aChangedSalaryChangesOnlyThatLineAndTheDiffListsOldAndNewNet() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID raised = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, raised, "24000", LocalDate.now().minusDays(300));
        lockTeacherMonth(raised);
        UUID same = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, same, "25000", LocalDate.now().minusDays(300));
        lockTeacherMonth(same);
        UUID run = startAndCompute(admin);
        Map<String, Object> sameBefore = lineOf(admin, run, same);
        assertThat(new BigDecimal((String) lineOf(admin, run, raised).get("net"))).isEqualByComparingTo("24000");

        // backdated: effective the whole month; the draft must not move until it is computed again
        teacherSalary(admin, raised, "30000", month.atDay(1));
        assertThat(new BigDecimal((String) lineOf(admin, run, raised).get("net")))
                .as("a draft does not change by itself")
                .isEqualByComparingTo("24000");
        Map<String, Object> after = recompute(admin, run);

        Map<String, Object> raisedAfter = lineOf(admin, run, raised);
        assertThat(new BigDecimal((String) raisedAfter.get("net"))).isEqualByComparingTo("30000");
        assertThat(lineOf(admin, run, same)).isEqualTo(sameBefore);
        Map<String, Object> entry = section(after, "changed").stream()
                .filter(e -> raised.toString().equals(e.get("personId")))
                .findFirst()
                .orElseThrow();
        assertThat(new BigDecimal((String) entry.get("oldNet"))).isEqualByComparingTo("24000");
        assertThat(new BigDecimal((String) entry.get("newNet"))).isEqualByComparingTo("30000");
        assertThat(mentions(section(after, "changed"), same)).isFalse();
    }

    @Test
    void aPersonWhoIsNoLongerInTheRunIsRemovedAndANewOneIsAdded() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID leaving = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, leaving, "20000", LocalDate.now().minusDays(300));
        lockTeacherMonth(leaving);
        UUID run = startAndCompute(admin);
        assertThat(lineOf(admin, run, leaving)).isNotNull();
        UUID joining = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, joining, "21000", LocalDate.now().minusDays(300));
        lockTeacherMonth(joining);
        // the placement ended before the month: no payable date any more
        jdbc.update(
                "update contract_assignment set ends_on = ? where teacher_id = ? and status = 'ACTIVE'", month.atDay(1).minusDays(1), leaving);

        Map<String, Object> after = recompute(admin, run);

        assertThat(lineOf(admin, run, leaving)).isNull();
        assertThat(lineOf(admin, run, joining)).isNotNull();
        assertThat(mentions(section(after, "removed"), leaving)).isTrue();
        assertThat(mentions(section(after, "added"), joining)).isTrue();
    }

    @Test
    void computeAgainWhileComputingIsRefusedUnlessTheHeartbeatIsStale() {
        String admin = signInAs(Role.ADMIN).token();
        UUID run = startAndCompute(admin);

        jdbc.update("update payroll_run set compute_state = 'RUNNING', heartbeat_at = now() where id = ?", run);
        Resp busy = post(RUNS + "/" + run + "/recompute", admin, Map.of());
        assertThat(busy.status()).isEqualTo(409);
        assertThat(busy.body()).contains("being computed");

        jdbc.update(
                "update payroll_run set heartbeat_at = now() - interval '11 minutes' where id = ?", run);
        Map<String, Object> stalled = get(RUNS + "/" + run, admin).map();
        assertThat(stalled.get("stalled")).isEqualTo(true);
        Resp resumed = post(RUNS + "/" + run + "/recompute", admin, Map.of());
        assertThat(resumed.status()).as(resumed.body()).isEqualTo(202);
        assertThat(awaitComputed(admin, run).get("computeState")).isEqualTo("DONE");
    }

    @Test
    void aFailedRunCanBeComputedAgain() {
        String admin = signInAs(Role.ADMIN).token();
        UUID run = startAndCompute(admin);
        jdbc.update("update payroll_run set compute_state = 'FAILED' where id = ?", run);

        Resp again = post(RUNS + "/" + run + "/recompute", admin, Map.of());

        assertThat(again.status()).as(again.body()).isEqualTo(202);
        assertThat(awaitComputed(admin, run).get("computeState")).isEqualTo("DONE");
    }
}
