package com.hls.incentive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 030 US4 (T025, T026): notifications to the right people once, the dashboard tile and the Director's approvals inbox. */
class NotificationAndDashboardTest extends IncentiveTestBase {

    @Test
    void aNewIncentiveTellsTheDirectorsAndTheZoneManagerOnceAndNobodyElse() {
        Mou m = mou();
        Fixture other = fixture(m.admin());
        wonProspect(m.f().zoneId(), m.f().schoolId(), other.manager().signed().userId(), m.adminUserId());
        approveMou(m);
        awaitIncentive(m.f().schoolId());
        settle();

        assertThat(notifications(m.director().userId(), "INCENTIVE_PENDING")).isEqualTo(1);
        assertThat(notifications(m.f().manager().signed().userId(), "INCENTIVE_PENDING")).isEqualTo(1);
        assertThat(notifications(other.manager().signed().userId(), "INCENTIVE_PENDING")).isZero();
        assertThat(notifications(m.adminUserId(), "INCENTIVE_PENDING")).isZero();
    }

    @Test
    void everyZoneManagerOfTheSchoolsZoneIsToldOnce() {
        Mou m = mou();
        ManagerCtx second = newManager(m.admin(), m.f().zoneId());
        Fixture other = fixture(m.admin());
        wonProspect(m.f().zoneId(), m.f().schoolId(), other.manager().signed().userId(), m.adminUserId());
        approveMou(m);
        awaitIncentive(m.f().schoolId());
        settle();

        assertThat(notifications(m.f().manager().signed().userId(), "INCENTIVE_PENDING")).isEqualTo(1);
        assertThat(notifications(second.signed().userId(), "INCENTIVE_PENDING")).isEqualTo(1);
        assertThat(notifications(m.director().userId(), "INCENTIVE_PENDING")).isEqualTo(1);
        assertThat(notifications(other.manager().signed().userId(), "INCENTIVE_PENDING")).isZero();
    }

    @Test
    void whenTheEarnerIsTheZoneManagerTheyAreToldOnceThatTheyMayApprove() {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.f().manager().signed().userId(), m.adminUserId());
        approveMou(m);
        awaitIncentive(m.f().schoolId());
        settle();

        assertThat(notifications(m.f().manager().signed().userId(), "INCENTIVE_PENDING")).isEqualTo(1);
    }

    @Test
    void aDecisionTellsTheEarnerUnlessTheyDecidedItThemselves() {
        Mou m = mou();
        Fixture other = fixture(m.admin());
        wonProspect(m.f().zoneId(), m.f().schoolId(), other.manager().signed().userId(), m.adminUserId());
        approveMou(m);
        UUID id = idOf(awaitIncentive(m.f().schoolId()));
        long version = ((Number) incentiveOf(m.f().schoolId()).get("version")).longValue();

        assertThat(post(API + "/" + id + "/reject", m.director().token(), Map.of("version", version, "reason", "Not closed")).status()).isEqualTo(200);
        assertThat(notifications(other.manager().signed().userId(), "INCENTIVE_DECIDED")).isEqualTo(1);

        Mou own = mou();
        wonProspect(own.f().zoneId(), own.f().schoolId(), own.f().manager().signed().userId(), own.adminUserId());
        approveMou(own);
        UUID ownId = idOf(awaitIncentive(own.f().schoolId()));
        long ownVersion = ((Number) incentiveOf(own.f().schoolId()).get("version")).longValue();
        assertThat(post(API + "/" + ownId + "/approve", own.managerToken(), Map.of("version", ownVersion)).status()).isEqualTo(200);
        assertThat(notifications(own.f().manager().signed().userId(), "INCENTIVE_DECIDED")).isZero();
        assertThat(db.queryForObject("select message from notification where recipient_user_id = ? and type = 'INCENTIVE_DECIDED'", String.class,
                        other.manager().signed().userId()))
                .contains("rejected").contains("Not closed");
    }

    @SuppressWarnings("unchecked")
    @Test
    void theDashboardTileCountsWhatTheListShowsWithinScope() {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.f().manager().signed().userId(), m.adminUserId());
        approveMou(m);
        awaitIncentive(m.f().schoolId());

        Resp tile = get("/api/v1/dashboard/areas/PAYROLL/tiles/incentives.summary", m.managerToken());

        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        assertThat(tile.map().get("state")).isEqualTo("OK");
        List<Map<String, Object>> values = (List<Map<String, Object>>) tile.map().get("values");
        assertThat(values.get(0)).containsEntry("label", "Waiting for a decision");
        assertThat(((Number) values.get(0).get("value")).longValue()).isEqualTo(1);
        Resp director = get("/api/v1/dashboard/areas/PAYROLL/tiles/incentives.summary", m.director().token());
        assertThat(((Number) ((List<Map<String, Object>>) director.map().get("values")).get(0).get("value")).longValue())
                .isGreaterThanOrEqualTo(1);
        assertThat(get("/api/v1/dashboard/areas/PAYROLL/tiles/incentives.summary", signInAs(com.hls.identity.user.Role.TEACHER).token()).status())
                .isIn(403, 404);
    }

    @SuppressWarnings("unchecked")
    @Test
    void thePendingIncentivesAppearInTheApprovalsInboxAndLeaveItOnceDecided() {
        Mou m = mou();
        wonProspect(m.f().zoneId(), m.f().schoolId(), m.f().manager().signed().userId(), m.adminUserId());
        approveMou(m);
        UUID id = idOf(awaitIncentive(m.f().schoolId()));
        String inbox = "/api/v1/dashboard/inbox?kind=MANAGER_INCENTIVE&size=100";

        Resp pending = get(inbox, m.director().token());
        List<String> itemIds = ((List<Map<String, Object>>) pending.map().get("items")).stream().map(i -> (String) i.get("itemId")).toList();
        assertThat(itemIds).contains(id.toString());

        long version = ((Number) incentiveOf(m.f().schoolId()).get("version")).longValue();
        assertThat(post(API + "/" + id + "/approve", m.director().token(), Map.of("version", version)).status()).isEqualTo(200);
        List<String> after = ((List<Map<String, Object>>) get(inbox, m.director().token()).map().get("items")).stream()
                .map(i -> (String) i.get("itemId")).toList();
        assertThat(after).doesNotContain(id.toString());
    }
}
