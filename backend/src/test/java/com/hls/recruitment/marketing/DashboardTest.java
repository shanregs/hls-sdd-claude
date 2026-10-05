package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The dashboard counts match the lists; demand is vacant positions of won Schools, supply is "not available" here. */
class DashboardTest extends WonProspectTestBase {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @Test
    void visitsStagesWinRateAndWonSchoolsMatchTheDataOfAZoneManagerScope() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx manager = newManager(admin, zone);
        UUID p1 = prospect(admin, zone);
        UUID p2 = prospect(admin, zone);
        UUID p3 = prospect(admin, zone);
        UUID elsewhere = prospect(admin, zone(admin));
        post(M + "/prospects/" + p2 + "/stage", admin, Map.of("stage", "VISIT"));
        post(M + "/prospects/" + p3 + "/stage", admin, Map.of("stage", "LOST", "reason", "Not interested"));
        UUID visit = activity(admin, p1, today);
        complete(admin, visit, "Done", null);
        activity(admin, p2, today.plusDays(1).isAfter(YearMonth.from(today).atEndOfMonth()) ? today : today);
        activity(admin, elsewhere, today);

        Resp resp = get(M + "/dashboard?period=" + YearMonth.from(today), manager.token());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Map<String, Object> visits = map(resp.map().get("visits"));
        assertThat(((Number) visits.get("completed")).intValue()).isEqualTo(1);
        assertThat(((Number) visits.get("planned")).intValue() + ((Number) visits.get("missed")).intValue()).isEqualTo(1);
        Map<String, Object> byStage = map(resp.map().get("prospectsByStage"));
        assertThat(byStage).containsEntry("PROSPECT", 1).containsEntry("VISIT", 1).containsEntry("LOST", 1);
        assertThat(resp.map()).containsEntry("lost", 1).containsEntry("won", 0);
        assertThat(resp.map().get("supply")).isNull();
        assertThat(resp.map().get("shortfall")).isNull();
        // a Zone Manager's numbers cover only their Zone: the visit elsewhere is not counted
        Map<String, Object> all = get(M + "/dashboard?period=" + YearMonth.from(today), admin).map();
        assertThat(((Number) map(all.get("visits")).get("planned")).intValue() + ((Number) map(all.get("visits")).get("missed")).intValue())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void winRateIsWonOverWonPlusLostAndTheWonSchoolsAreCountedPerZoneAndOwner() {
        String admin = admin();
        Won won = wonProspect(admin);
        UUID lost = prospect(admin, won.zone());
        post(M + "/prospects/" + lost + "/stage", admin, Map.of("stage", "LOST", "reason", "Too expensive"));
        ManagerCtx manager = newManager(admin, won.zone());

        Map<String, Object> mine = get(M + "/dashboard", manager.token()).map();

        assertThat(mine).containsEntry("won", 1).containsEntry("lost", 1).containsEntry("winRate", "0.50");
        assertThat(mine.get("wonPerZone").toString()).contains("=1");
        assertThat(mine.get("wonPerOwner").toString()).contains("Tester");
    }

    @Test
    void demandIsTheVacantPositionsOfWonSchoolsWithALiveContract() {
        String admin = admin();
        Won first = wonProspect(admin);
        Won second = wonProspect(admin);
        UUID dir = signInAs(Role.DIRECTOR).userId();
        long before = ((Number) get(M + "/dashboard", directorToken()).map().get("demand")).longValue();
        int[] counts = {4, 5};
        List<Won> both = List.of(first, second);
        for (int i = 0; i < both.size(); i++) {
            Won w = both.get(i);
            assertThat(post(M + "/prospects/" + w.prospect() + "/win", admin, winBody(place(admin, w.zone()))).status()).isEqualTo(200);
            UUID school = schoolOf(admin, w.prospect());
            ManagerCtx manager = newManager(admin, w.zone());
            assignSchoolManager(admin, school, manager.managerId());
            assertThat(createContract(admin, school, startedDaysAgo(sameSalaryBody(fixtureOf(w.zone(), school, manager), dir, counts[i], "15000"), 2)).status())
                    .isEqualTo(201);
        }
        UUID teacher = teacher(admin);
        assertThat(assign(admin, teacher, schoolOf(admin, first.prospect()), null, today.minusDays(1)).status()).isEqualTo(200);

        long after = ((Number) get(M + "/dashboard", directorToken()).map().get("demand")).longValue();

        assertThat(after - before).isEqualTo(4 + 5 - 1);
    }

    @Test
    void anEmptyScopeReturnsZerosAndTeacherSystemAreRefused() {
        String admin = admin();
        ManagerCtx empty = newManager(admin, zone(admin));

        Map<String, Object> resp = get(M + "/dashboard", empty.token()).map();

        assertThat(resp).containsEntry("won", 0).containsEntry("lost", 0).containsEntry("demand", 0);
        assertThat(resp.get("winRate")).isNull();
        assertThat(get(M + "/dashboard?period=nonsense", admin).status()).isEqualTo(400);
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(get(M + "/dashboard", signInAs(role).token()).status()).isEqualTo(403);
        }
    }
}
