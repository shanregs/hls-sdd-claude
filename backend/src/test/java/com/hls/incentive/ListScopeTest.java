package com.hls.incentive;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 030 US4 (T023, T024, T029, SC-004): lists, filters, totals, export and the no-earner list, for every role and scope. */
class ListScopeTest extends IncentiveTestBase {

    /** An incentive in its own Zone, earned by that Zone's Manager. */
    private record Own(Mou mou, UUID id) {
        UUID schoolId() {
            return mou.f().schoolId();
        }
    }

    private Own earned() {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.f().manager().signed().userId(), m.adminUserId());
        approveMou(m);
        return new Own(m, idOf(awaitIncentive(m.f().schoolId())));
    }

    private List<String> ids(Resp list) {
        return content(list).stream().map(r -> (String) r.get("id")).toList();
    }

    private void rename(UUID schoolId, String name) {
        db.update("update school set name = ? where id = ?", name, schoolId);
    }

    @Test
    void theDirectorAndAdminSeeEveryZoneAndAZoneManagerOnlyTheirOwn() {
        Own a = earned();
        Own b = earned();
        String director = a.mou().director().token();

        assertThat(ids(get(API + "?size=100", director))).contains(a.id().toString(), b.id().toString());
        assertThat(ids(get(API + "?size=100", a.mou().admin()))).contains(a.id().toString(), b.id().toString());
        assertThat(ids(get(API + "?size=100", a.mou().managerToken()))).containsExactly(a.id().toString());
        assertThat(ids(get(API + "?size=100", b.mou().managerToken()))).containsExactly(b.id().toString());
        assertThat(get(API + "/" + b.id(), a.mou().managerToken()).status()).isEqualTo(404);
        assertThat(get(API + "/" + a.id(), b.mou().managerToken()).status()).isEqualTo(404);
    }

    @Test
    void filtersByStatusManagerZoneMonthAndText() {
        Own a = earned();
        Own b = earned();
        String director = a.mou().director().token();
        long version = ((Number) incentiveOf(b.schoolId()).get("version")).longValue();
        assertThat(post(API + "/" + b.id() + "/approve", director, Map.of("version", version)).status()).isEqualTo(200);
        String month = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
        rename(a.schoolId(), "Zebra Valley School " + UUID.randomUUID().toString().substring(0, 6));

        assertThat(ids(get(API + "?status=PENDING_APPROVAL&size=100", director))).contains(a.id().toString()).doesNotContain(b.id().toString());
        assertThat(ids(get(API + "?status=APPROVED&size=100", director))).contains(b.id().toString()).doesNotContain(a.id().toString());
        assertThat(ids(get(API + "?managerId=" + a.mou().f().manager().managerId() + "&size=100", director))).containsExactly(a.id().toString());
        assertThat(ids(get(API + "?zoneId=" + b.mou().f().zoneId() + "&size=100", director))).containsExactly(b.id().toString());
        assertThat(ids(get(API + "?month=" + month + "&size=100", director))).contains(a.id().toString(), b.id().toString());
        assertThat(ids(get(API + "?month=2001-01&size=100", director))).isEmpty();
        assertThat(ids(get(API + "?q=zebra+valley&size=100", director))).containsExactly(a.id().toString());
        assertThat(get(API + "?status=NOPE", director).status()).isEqualTo(400);
        assertThat(get(API + "?month=October", director).status()).isEqualTo(400);
        Resp sorted = get(API + "?sort=amount&dir=asc&size=1&page=0", director);
        assertThat(sorted.status()).isEqualTo(200);
        assertThat(content(sorted)).hasSize(1);
        assertThat(total(sorted)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void mineShowsOnlyTheCallersOwnIncentives() {
        Own a = earned();
        Own b = earned();

        assertThat(ids(get(API + "/mine", a.mou().managerToken()))).containsExactly(a.id().toString());
        assertThat(ids(get(API + "/mine", b.mou().managerToken()))).containsExactly(b.id().toString());
        assertThat(ids(get(API + "/mine", a.mou().director().token()))).isEmpty();
    }

    @Test
    void teacherAndSystemAreRefusedEverywhereAndAManagerCannotExportOrSeeNoEarner() {
        Own a = earned();
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            for (String path : List.of("", "/mine", "/summary", "/no-earner", "/export.csv", "/" + a.id())) {
                assertThat(get(API + path, token).status()).as(role + " " + path).isEqualTo(403);
            }
        }
        assertThat(get(API + "/export.csv", a.mou().managerToken()).status()).isEqualTo(403);
        assertThat(get(API + "/no-earner", a.mou().managerToken()).status()).isEqualTo(403);
        assertThat(get(API + "/no-earner", a.mou().admin()).status()).isEqualTo(200);
        assertThat(get(API + "/no-earner", a.mou().director().token()).status()).isEqualTo(200);
        assertThat(get(API, null).status()).isEqualTo(401);
    }

    @Test
    void theNoEarnerListNamesTheReasonForEachSchool() {
        Mou m = mou();
        approveMou(m);
        awaitNoEarner(m.f().schoolId());

        Resp list = get(API + "/no-earner", m.director().token());

        assertThat(list.status()).isEqualTo(200);
        List<Map<String, Object>> rows = readRows(list);
        Map<String, Object> mine = rows.stream().filter(r -> m.f().schoolId().toString().equals(r.get("schoolId"))).findFirst().orElseThrow();
        assertThat(mine).containsEntry("reason", "NO_PROSPECT").containsEntry("resolved", false);
        assertThat((String) mine.get("reasonText")).contains("No won prospect");
    }

    private static List<Map<String, Object>> readRows(Resp resp) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(resp.body(), new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aSchoolThatMovesZoneWhilePendingFollowsItsNewZoneInListSummaryExportAndFilter() {
        Own a = earned();
        String admin = a.mou().admin();
        String director = a.mou().director().token();
        ManagerCtx oldZoneManager = newManager(admin, a.mou().f().zoneId());
        Fixture newZone = fixture(admin);
        String month = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
        String name = "Mover " + UUID.randomUUID().toString().substring(0, 6);
        rename(a.schoolId(), name);
        assertThat(ids(get(API + "?size=100", oldZoneManager.token()))).containsExactly(a.id().toString());
        assertThat(ids(get(API + "?size=100", newZone.manager().token()))).doesNotContain(a.id().toString());

        db.update("update school set place_id = (select place_id from school where id = ?) where id = ?", newZone.schoolId(), a.schoolId());

        assertThat(ids(get(API + "?size=100", oldZoneManager.token()))).doesNotContain(a.id().toString());
        assertThat(ids(get(API + "?size=100", newZone.manager().token()))).contains(a.id().toString());
        assertThat(get(API + "/" + a.id(), oldZoneManager.token()).status()).isEqualTo(404);
        assertThat(get(API + "/" + a.id(), newZone.manager().token()).status()).isEqualTo(200);
        String url = API + "/summary?from=" + month + "&to=" + month;
        assertThat(get(url, oldZoneManager.token()).body()).doesNotContain(a.mou().f().manager().managerId().toString());
        assertThat(get(url, newZone.manager().token()).body()).contains(a.mou().f().manager().managerId().toString());
        // the Zone filter and the Zone column read the same live Zone, in the list and in the export
        assertThat(ids(get(API + "?zoneId=" + newZone.zoneId() + "&size=100", director))).contains(a.id().toString());
        assertThat(ids(get(API + "?zoneId=" + a.mou().f().zoneId() + "&size=100", director))).doesNotContain(a.id().toString());
        assertThat(get(API + "/export.csv?zoneId=" + newZone.zoneId(), director).body()).contains(name);
        assertThat(get(API + "/export.csv?zoneId=" + a.mou().f().zoneId(), director).body()).doesNotContain(name);
    }

    @Test
    void theSummaryTotalsPerStatusManagerAndMonthFollowTheScope() {
        Own a = earned();
        Own b = earned();
        String director = a.mou().director().token();
        String month = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Kolkata")).toString();

        Resp all = get(API + "/summary?from=" + month + "&to=" + month, director);
        Resp zoneA = get(API + "/summary?from=" + month + "&to=" + month, a.mou().managerToken());

        assertThat(all.status()).as(all.body()).isEqualTo(200);
        assertThat(all.body()).contains(a.mou().f().manager().managerId().toString()).contains(b.mou().f().manager().managerId().toString());
        assertThat(zoneA.body()).contains(a.mou().f().manager().managerId().toString()).doesNotContain(b.mou().f().manager().managerId().toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> perMonth = (List<Map<String, Object>>) zoneA.map().get("byManagerMonth");
        assertThat(perMonth).hasSize(1);
        assertThat(perMonth.get(0)).containsEntry("month", month).containsEntry("count", 1);
        assertThat(new java.math.BigDecimal(perMonth.get(0).get("amount").toString())).isEqualByComparingTo("5000");
    }

    @Test
    void theCsvMatchesTheListNeutralisesFormulasAndStaysInScope() {
        Own a = earned();
        Own b = earned();
        rename(a.schoolId(), "=HYPERLINK(\"http://x\")");
        String director = a.mou().director().token();

        Resp csv = get(API + "/export.csv", director);

        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.body()).contains("School,Zone,Manager,Closure date,Amount,Status");
        assertThat(csv.body()).contains("'=HYPERLINK").doesNotContain("\n=HYPERLINK").doesNotContain(",=HYPERLINK");
        long lines = csv.body().lines().filter(l -> !l.isBlank()).count() - 1;
        assertThat(lines).isEqualTo(total(get(API + "?size=1", director)));
        // exports follow the same filter
        Resp filtered = get(API + "/export.csv?zoneId=" + b.mou().f().zoneId(), director);
        assertThat(filtered.body().lines().filter(l -> !l.isBlank()).count()).isEqualTo(2);
        // Admin may export, a Manager may not
        assertThat(get(API + "/export.csv", a.mou().admin()).status()).isEqualTo(200);
    }
}
