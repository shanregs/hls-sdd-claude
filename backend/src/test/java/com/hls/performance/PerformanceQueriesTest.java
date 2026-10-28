package com.hls.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.performance.api.PerformanceSummary;
import com.hls.support.PerformanceTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 026 US6: the lists with their filters, the Teacher page, the Planned list and the summary for the dashboard. */
class PerformanceQueriesTest extends PerformanceTestBase {

    @Autowired
    PerformanceSummary summary;

    @Test
    void listsFilterByManagerSchoolZoneResultAndDate() {
        Setup a = setup();
        Setup b = setup();
        Map<String, Object> goodA = observe(a.managerToken(), a.teacherId(), "GOOD", 5);
        Map<String, Object> needsA = observe(a.managerToken(), anotherTeacher(a), "NEEDS_IMPROVEMENT", 3);
        Map<String, Object> goodB = observe(b.managerToken(), b.teacherId(), "GOOD", 4);

        String director = a.director();
        List<String> byManager = ids(get(PERF + "/observations?managerId=" + a.managerUserId(), director));
        assertThat(byManager).contains(goodA.get("id").toString(), needsA.get("id").toString()).doesNotContain(goodB.get("id").toString());

        assertThat(ids(get(PERF + "/observations?schoolId=" + b.schoolId(), director))).containsExactly(goodB.get("id").toString());
        assertThat(ids(get(PERF + "/observations?zoneId=" + a.zoneId(), director)))
                .containsExactlyInAnyOrder(goodA.get("id").toString(), needsA.get("id").toString());
        assertThat(ids(get(PERF + "/observations?zoneId=" + a.zoneId() + "&result=NEEDS_IMPROVEMENT", director)))
                .containsExactly(needsA.get("id").toString());
        assertThat(ids(get(PERF + "/observations?zoneId=" + a.zoneId() + "&status=DRAFT", director))).isEmpty();
        assertThat(ids(get(PERF + "/observations?zoneId=" + a.zoneId() + "&from=" + today().plusDays(1), director))).isEmpty();
        assertThat(ids(get(PERF + "/observations?zoneId=" + a.zoneId() + "&from=" + today() + "&to=" + today(), director))).hasSize(2);
        Resp counted = get(PERF + "/observations?zoneId=" + a.zoneId(), director);
        assertThat(total(counted)).isEqualTo(2);
        assertThat(get(PERF + "/observations?result=NONSENSE", director).status()).isEqualTo(400);

        // each Manager sees only their own
        assertThat(ids(get(PERF + "/observations", a.managerToken()))).doesNotContain(goodB.get("id").toString());
        assertThat(ids(get(PERF + "/observations", b.managerToken()))).doesNotContain(goodA.get("id").toString(), needsA.get("id").toString());
    }

    @Test
    void theIssueListsShowOpenAndReferredIssues() {
        Setup s = setup();
        UUID second = anotherTeacher(s);
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        observe(s.managerToken(), second, "UNSUITABLE", 1);
        UUID referred = openIssueOf(second);
        Object version = get(PERF + "/issues/" + referred, s.managerToken()).map().get("version");
        post(PERF + "/issues/" + referred + "/referral", s.managerToken(),
                Map.of("recommendation", "TRANSFER", "reason", "The Teacher would do better at another School", "version", version));

        List<String> open = ids(get(PERF + "/issues?view=open&zoneId=" + s.zoneId(), s.director()));
        assertThat(open).containsExactly(openIssueOf(s.teacherId()).toString());
        List<String> refs = ids(get(PERF + "/issues?view=referred&zoneId=" + s.zoneId(), s.director()));
        assertThat(refs).containsExactly(referred.toString());
        assertThat(ids(get(PERF + "/issues?zoneId=" + s.zoneId() + "&status=REFERRED", s.managerToken()))).containsExactly(referred.toString());
    }

    @Test
    void theTeacherPageListsEverythingInDateOrderLatestResultFirst() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        post(PERF + "/feedback", s.managerToken(), Map.of("teacherId", s.teacherId(), "principalName", "Mrs Rao", "givenOn", today().toString(),
                "category", "ATTENDANCE", "comments", "Arrives late most Mondays and Tuesdays", "actionRequired", false));
        observe(s.managerToken(), s.teacherId(), "GOOD", 4);
        plan(s.director(), s.teacherId(), today().plusDays(7), "MANAGEMENT_PLAN");

        Resp page = get(PERF + "/teachers/" + s.teacherId(), s.director());
        assertThat(page.status()).as(page.body()).isEqualTo(200);
        Map<String, Object> view = page.map();
        assertThat(view.get("latestResult")).isEqualTo("GOOD");
        assertThat(view.get("openPlan")).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) view.get("entries");
        assertThat(entries).extracting(e -> e.get("type")).contains("OBSERVATION", "FEEDBACK", "ISSUE");
        List<String> dates = entries.stream().map(e -> (String) e.get("date")).toList();
        assertThat(dates).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat((List<?>) view.get("observations")).hasSize(2);
        assertThat((List<?>) view.get("feedback")).hasSize(1);
        assertThat((List<?>) view.get("issues")).hasSize(1);

        // another Manager gets "not found", a Teacher is refused
        Setup other = setup();
        assertThat(get(PERF + "/teachers/" + s.teacherId(), other.managerToken()).status()).isEqualTo(404);
        assertThat(get(PERF + "/teachers/" + s.teacherId(), s.managerToken()).status()).isEqualTo(200);
    }

    @Test
    void theSummaryMatchesTheLists() {
        Setup s = setup();
        UUID second = anotherTeacher(s);
        observe(s.managerToken(), s.teacherId(), "GOOD", 4);
        observe(s.managerToken(), second, "NEEDS_IMPROVEMENT", 3);
        plan(s.director(), s.teacherId(), today().plusDays(3), "MANAGEMENT_PLAN");
        UUID third = anotherTeacher(s);
        plan(s.director(), third, today().plusDays(1), "MANAGEMENT_PLAN");
        jdbc.update("update observation_plan set planned_on = ? where teacher_id = ?", today().minusDays(2), third);

        Map<UUID, PerformanceSummary.Counts> byManager = summary.byManager(today().minusDays(7), today());
        PerformanceSummary.Counts counts = byManager.get(s.managerUserId());
        assertThat(counts).isNotNull();
        assertThat(counts.observationsDone()).isEqualTo(2);
        assertThat(counts.observationsPlanned()).isEqualTo(2);
        assertThat(counts.observationsOverdue()).isEqualTo(1);
        assertThat(counts.openIssues()).isEqualTo(1);
        assertThat(counts.referrals()).isZero();
        assertThat(counts.resolvedIssues()).isZero();
        assertThat(summary.byZone(today().minusDays(7), today()).get(zoneOf(s)).observationsDone()).isEqualTo(2);

        Resp api = get(PERF + "/summary?from=" + today().minusDays(7) + "&to=" + today(), s.managerToken());
        assertThat(api.status()).as(api.body()).isEqualTo(200);
        assertThat(api.body()).contains("\"observationsDone\":2");
        // a Manager sees only themselves and no Zone totals
        assertThat(new java.util.ArrayList<Object>(((Map<?, ?>) api.map().get("byManager")).keySet())).containsExactly(s.managerUserId().toString());
        assertThat((Map<?, ?>) api.map().get("byZone")).isEmpty();
        assertThat(get(PERF + "/summary?from=" + today() + "&to=" + today().minusDays(1), s.director()).status()).isEqualTo(400);
    }

    /** Spec 014c A4: per-Teacher counts equal the Teacher screen and add up to the Manager's row. */
    @Test
    void thePerTeacherSummaryMatchesTheTeacherScreenAndAddsUpToTheManager() {
        Setup s = setup();
        UUID second = anotherTeacher(s);
        UUID third = anotherTeacher(s);
        observe(s.managerToken(), s.teacherId(), "GOOD", 4);
        observe(s.managerToken(), second, "NEEDS_IMPROVEMENT", 3);
        observe(s.managerToken(), second, "GOOD", 5);
        plan(s.director(), s.teacherId(), today().plusDays(3), "MANAGEMENT_PLAN");
        plan(s.director(), third, today().plusDays(1), "MANAGEMENT_PLAN");
        jdbc.update("update observation_plan set planned_on = ? where teacher_id = ?", today().minusDays(2), third);

        Map<UUID, PerformanceSummary.Counts> byTeacher = summary.byTeacher(today().minusDays(7), today());

        assertThat(byTeacher.get(s.teacherId()).observationsDone()).isEqualTo(1);
        assertThat(byTeacher.get(second).observationsDone()).isEqualTo(2);
        assertThat(byTeacher.get(second).openIssues()).isEqualTo(1);
        assertThat(byTeacher.get(third).observationsOverdue()).isEqualTo(1);
        assertThat(byTeacher.get(third).observationsDone()).isZero();
        for (UUID teacher : List.of(s.teacherId(), second)) {
            Map<String, Object> view = get(PERF + "/teachers/" + teacher, s.managerToken()).map();
            assertThat((List<?>) view.get("observations")).hasSize((int) byTeacher.get(teacher).observationsDone());
            assertThat(((List<?>) view.get("issues")).size()).isGreaterThanOrEqualTo((int) byTeacher.get(teacher).openIssues());
        }
        PerformanceSummary.Counts manager = summary.byManager(today().minusDays(7), today()).get(s.managerUserId());
        List<UUID> mine = List.of(s.teacherId(), second, third);
        assertThat(mine.stream().mapToLong(t -> byTeacher.get(t).observationsDone()).sum()).isEqualTo(manager.observationsDone());
        assertThat(mine.stream().mapToLong(t -> byTeacher.get(t).observationsPlanned()).sum()).isEqualTo(manager.observationsPlanned());
        assertThat(mine.stream().mapToLong(t -> byTeacher.get(t).observationsOverdue()).sum()).isEqualTo(manager.observationsOverdue());
        assertThat(mine.stream().mapToLong(t -> byTeacher.get(t).openIssues()).sum()).isEqualTo(manager.openIssues());
    }

    private UUID zoneOf(Setup s) {
        return s.zoneId();
    }

    @Test
    void listsAnswerQuicklyForTheExpectedVolume() {
        Setup s = setup();
        UUID observer = s.managerUserId();
        jdbc.update("""
                insert into observation (id, root_id, version_no, current, teacher_id, school_id, observer_user_id, observer_name, manager_user_id,
                                         observed_on, status, overall_result, average_rating, version, created_at, updated_at)
                select g, g, 1, true, ?, ?, ?, 'Load test', ?, current_date - (g_i % 300), 'SUBMITTED', 'GOOD', 4.0, 0, now(), now()
                from (select gen_random_uuid() as g, i as g_i from generate_series(1, 2000) i) x
                """, s.teacherId(), s.schoolId(), observer, observer);
        jdbc.update("""
                insert into performance_issue (id, teacher_id, school_id, manager_user_id, source_type, source_root_id, category, status, resolution,
                                               correction_rounds, opened_by, opened_on, closed_on, version, created_at, updated_at)
                select gen_random_uuid(), gen_random_uuid(), ?, ?, 'OBSERVATION', gen_random_uuid(), 'NEEDS_IMPROVEMENT', 'RESOLVED', 'IMPROVED',
                       1, ?, current_date - (i % 200), current_date, 0, now(), now()
                from generate_series(1, 500) i
                """, s.schoolId(), observer, observer);
        try {
            long start = System.nanoTime();
            Resp observations = get(PERF + "/observations?schoolId=" + s.schoolId() + "&size=50", s.director());
            Resp issues = get(PERF + "/issues?schoolId=" + s.schoolId() + "&size=50", s.director());
            Resp mine = get(PERF + "/observations?size=50", s.managerToken());
            long millis = (System.nanoTime() - start) / 1_000_000;
            assertThat(observations.status()).isEqualTo(200);
            assertThat(total(observations)).isGreaterThanOrEqualTo(2000);
            assertThat(total(issues)).isGreaterThanOrEqualTo(500);
            assertThat(mine.status()).isEqualTo(200);
            assertThat(millis).as("three lists with 2,000 observations and 500 issues").isLessThan(2000);
        } finally {
            jdbc.update("delete from observation where observer_name = 'Load test'");
            jdbc.update("delete from performance_issue where school_id = ? and closing_reason is null and resolution = 'IMPROVED'", s.schoolId());
        }
    }
}
