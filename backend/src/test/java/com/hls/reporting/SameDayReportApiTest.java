package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hls.identity.user.Role;
import com.hls.support.SubstitutionTestBase;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 040 US4: counts of same-day leave, covers, tasks and visits per Manager and School, in the caller's scope. */
class SameDayReportApiTest extends SubstitutionTestBase {

    private static final String REPORT = "/api/v1/reports/same-day";

    private World seeded() {
        World w = newWorld();
        Resp preview = post(ME + "/preview", w.teacherA().token(), draft(today(), today()));
        assumeTrue(preview.status() == 200 && preview.body().contains("\"problems\":[]"), "today is not a working day");
        assertThat(post(ME, w.teacherA().token(), draft(today(), today())).status()).isEqualTo(201);
        UUID requirement = requirementId(w.teacherA().teacherId(), today());
        assertThat(cover(w.managerA().token(), requirement).status()).isEqualTo(200);
        Resp task = post(
                "/api/v1/tasks/same-day",
                w.managerA().token(),
                Map.of("type", "SCHOOL_VISIT", "title", "Visit " + uniqueName("School"), "status", "IN_PROGRESS"));
        assertThat(task.status()).as(task.body()).isEqualTo(201);
        return w;
    }

    private String thisMonth() {
        return YearMonth.from(today()).toString();
    }

    @Test
    void adminSeesEverySectionWithTheSchoolAndTheManager() {
        World w = seeded();

        Resp resp = get(REPORT + "?month=" + thisMonth(), w.admin());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsKeys("leave", "covers", "tasks", "visits");
        assertThat(resp.body()).contains(w.schoolA().toString());
        Map<?, ?> leave = (Map<?, ?>) resp.map().get("leave");
        assertThat((List<?>) leave.get("bySchool")).isNotEmpty();
        Map<?, ?> tasks = (Map<?, ?>) resp.map().get("tasks");
        assertThat((List<?>) tasks.get("byManager")).isNotEmpty();
    }

    @Test
    void aManagerSeesTheirOwnSchoolAndTasksButNotAnotherManagersSchool() {
        World w = seeded();

        Resp mine = get(REPORT + "?month=" + thisMonth(), w.managerA().token());
        Resp other = get(REPORT + "?month=" + thisMonth(), w.managerB().token());

        assertThat(mine.status()).as(mine.body()).isEqualTo(200);
        assertThat(mine.body()).contains(w.schoolA().toString());
        assertThat(other.status()).isEqualTo(200);
        assertThat(other.body()).doesNotContain(w.schoolA().toString());
        Map<?, ?> otherTasks = (Map<?, ?>) other.map().get("tasks");
        assertThat((List<?>) otherTasks.get("byManager")).isEmpty();
    }

    @Test
    void teacherAndSystemCannotReadTheReportAndABadMonthIsRefused() {
        World w = newWorld();

        assertThat(get(REPORT, w.teacherA().token()).status()).isEqualTo(403);
        assertThat(get(REPORT, signInAs(Role.SYSTEM).token()).status()).isEqualTo(403);
        assertThat(get(REPORT + "?month=October", w.admin()).status()).isEqualTo(400);
    }

    @Test
    void ordinaryRecordsAreNotCounted() {
        World w = newWorld();
        String id = apply(w.teacherA(), monday(), monday());

        Resp resp = get(REPORT + "?month=" + YearMonth.from(monday()), w.admin());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.body()).doesNotContain(id);
        Map<?, ?> leave = (Map<?, ?>) resp.map().get("leave");
        assertThat(((List<?>) leave.get("bySchool")).toString()).doesNotContain(w.schoolA().toString());
    }

    @Test
    void zoneFilterNarrowsToThatZoneAndRefusesAZoneOutsideScope() {
        World w = seeded();

        Resp inZone = get(REPORT + "?month=" + thisMonth() + "&zoneId=" + w.zoneA(), w.admin());
        Resp otherZone = get(REPORT + "?month=" + thisMonth() + "&zoneId=" + w.zoneB(), w.admin());
        Resp mineAsManager = get(REPORT + "?month=" + thisMonth() + "&zoneId=" + w.zoneA(), w.managerA().token());
        Resp outsideScope = get(REPORT + "?month=" + thisMonth() + "&zoneId=" + w.zoneB(), w.managerA().token());
        Resp unknownZone = get(REPORT + "?month=" + thisMonth() + "&zoneId=" + UUID.randomUUID(), w.admin());
        Resp badZone = get(REPORT + "?month=" + thisMonth() + "&zoneId=north", w.admin());

        assertThat(inZone.status()).as(inZone.body()).isEqualTo(200);
        assertThat(inZone.body()).contains(w.schoolA().toString());
        assertThat(otherZone.status()).as(otherZone.body()).isEqualTo(200);
        assertThat(otherZone.body()).doesNotContain(w.schoolA().toString());
        assertThat((List<?>) ((Map<?, ?>) otherZone.map().get("tasks")).get("byManager")).isEmpty();
        assertThat(mineAsManager.status()).as(mineAsManager.body()).isEqualTo(200);
        assertThat(mineAsManager.body()).contains(w.schoolA().toString());
        assertThat(outsideScope.status()).isEqualTo(403);
        assertThat(outsideScope.body()).doesNotContain(w.schoolA().toString());
        assertThat(unknownZone.status()).isEqualTo(403);
        assertThat(badZone.status()).isEqualTo(400);
    }
}
