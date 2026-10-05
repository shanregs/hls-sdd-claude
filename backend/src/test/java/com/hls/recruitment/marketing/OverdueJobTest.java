package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.recruitment.api.WonProspectOverdue;
import com.hls.recruitment.marketing.internal.OverdueJob;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** A won prospect with no MoU after the allowed days is reported once per limit; recording the MoU clears it. */
@Import(OverdueJobTest.Recorder.class)
class OverdueJobTest extends WonProspectTestBase {

    @TestConfiguration
    static class Recorder {
        final List<WonProspectOverdue> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(WonProspectOverdue event) {
            seen.add(event);
        }
    }

    @Autowired
    Recorder events;

    @Autowired
    OverdueJob job;

    private long reportsFor(UUID prospect) {
        return events.seen.stream().filter(e -> e.prospectId().equals(prospect)).count();
    }

    private void wonDaysAgo(UUID prospect, int days) {
        jdbc.update("update marketing_prospect set won_at = now() - (? * interval '1 day') where id = ?", days, prospect);
    }

    @Test
    void aProspectPastTheLimitIsReportedOnceAndAgainOnlyWhenTheLimitChangesAndIsPassedAgain() {
        String admin = admin();
        Won won = wonProspect(admin);
        wonDaysAgo(won.prospect(), 20);

        job.runNow();
        assertThat(reportsFor(won.prospect())).isEqualTo(1);
        job.runNow();
        assertThat(reportsFor(won.prospect())).isEqualTo(1);

        Map<String, Object> current = get(M + "/settings", directorToken()).map();
        Resp changed = put(M + "/settings", directorToken(), Map.of("mouOverdueDays", 10, "version", current.get("version")));
        assertThat(changed.status()).as(changed.body()).isEqualTo(200);
        job.runNow();
        assertThat(reportsFor(won.prospect())).isEqualTo(2);
        assertThat(events.seen.get(events.seen.size() - 1).days()).isEqualTo(10);
        // restore the default for the other tests
        Map<String, Object> after = get(M + "/settings", directorToken()).map();
        put(M + "/settings", directorToken(), Map.of("mouOverdueDays", 14, "version", after.get("version")));
    }

    @Test
    void aProspectWithinTheLimitOrWithAnMouRecordedIsNotReported() {
        String admin = admin();
        Won fresh = wonProspect(admin);
        wonDaysAgo(fresh.prospect(), 2);
        Won recorded = wonProspect(admin);
        assertThat(post(M + "/prospects/" + recorded.prospect() + "/win", admin, winBody(place(admin, recorded.zone()))).status()).isEqualTo(200);
        UUID school = schoolOf(admin, recorded.prospect());
        ManagerCtx manager = newManager(admin, recorded.zone());
        assignSchoolManager(admin, school, manager.managerId());
        UUID dir = signInAs(Role.DIRECTOR).userId();
        assertThat(createContract(admin, school, startedDaysAgo(sameSalaryBody(fixtureOf(recorded.zone(), school, manager), dir, 3, "15000"), 3)).status())
                .isEqualTo(201);
        wonDaysAgo(recorded.prospect(), 30);

        job.runNow();

        assertThat(reportsFor(fresh.prospect())).isZero();
        assertThat(reportsFor(recorded.prospect())).isZero();
    }

    @Test
    void theBoardFlagsAnOverdueProspectEvenWithTheJobOffAndTheNotificationReachesTheOwnerOnce() {
        String admin = admin();
        Won won = wonProspect(admin);
        wonDaysAgo(won.prospect(), 25);

        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) get(M + "/prospects/" + won.prospect(), admin).map().get("row");
        assertThat(row).containsEntry("mouOverdue", true);
        job.runNow();
        job.runNow();

        Integer notified = jdbc.queryForObject(
                "select count(*) from notification where type = 'MOU_NOT_RECORDED' and link = ?", Integer.class, "/marketing/prospects/" + won.prospect());
        assertThat(notified).isGreaterThanOrEqualTo(1);
        Integer perUser = jdbc.queryForObject(
                "select max(c) from (select count(*) c from notification where type = 'MOU_NOT_RECORDED' and link = ? group by recipient_user_id) t",
                Integer.class,
                "/marketing/prospects/" + won.prospect());
        assertThat(perUser).isEqualTo(1);
    }
}
