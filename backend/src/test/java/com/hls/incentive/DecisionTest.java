package com.hls.incentive;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Spec 030 US2 (T018, SC-002, SC-004): who may decide an incentive, in and out of the Zone, and what the decision writes. */
class DecisionTest extends IncentiveTestBase {

    /** A pending incentive earned by the School's own Zone Manager. */
    private record Pending(Mou mou, UUID id) {
        UUID schoolId() {
            return mou.f().schoolId();
        }
    }

    private Pending pending(Mou m, UUID owner) {
        wonProspect(m.f().zoneId(), m.f().schoolId(), owner, m.adminUserId());
        approveMou(m);
        Map<String, Object> row = awaitIncentive(m.f().schoolId());
        return new Pending(m, idOf(row));
    }

    private Pending pendingOwnedByZoneManagerOf(Mou m) {
        return pending(m, m.f().manager().signed().userId());
    }

    private long version(String token, UUID id) {
        Resp resp = get(API + "/" + id, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return ((Number) ((Map<?, ?>) resp.map().get("incentive")).get("version")).longValue();
    }

    private Resp approveIncentive(String token, UUID id) {
        return post(API + "/" + id + "/approve", token, Map.of("version", version(token, id)));
    }

    private Resp rejectIncentive(String token, UUID id, String reason) {
        return post(API + "/" + id + "/reject", token, Map.of("version", version(token, id), "reason", reason));
    }

    @SuppressWarnings("unchecked")
    private static List<String> ids(Resp list) {
        return content(list).stream().map(r -> (String) r.get("id")).toList();
    }

    private static List<String> incentiveActions(Resp detail) {
        return (List<String>) ((Map<String, Object>) detail.map().get("incentive")).get("actions");
    }

    @Test
    void theDirectorApprovesAndTheAmountIsOfferedToPayrollOnce() {
        Mou m = mou();
        Pending p = pendingOwnedByZoneManagerOf(m);
        String director = m.director().token();
        assertThat(incentiveActions(get(API + "/" + p.id(), director))).containsExactly("approve", "reject");

        Resp approved = approveIncentive(director, p.id());

        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        Map<String, Object> row = incentiveOf(p.schoolId());
        assertThat(row.get("status")).isEqualTo("APPROVED");
        assertThat(row.get("decided_by")).isEqualTo(m.director().userId());
        assertThat(row.get("approved_by_earner")).isEqualTo(false);
        assertThat(db.queryForObject(
                        "select count(*) from payroll_adjustment where source_ref = ? and kind = 'INCENTIVE' and status = 'WAITING'",
                        Integer.class,
                        "INCENTIVE:" + p.id()))
                .isEqualTo(1);
        assertThat(eventKinds(p.id())).containsExactly("EARNED", "APPROVED", "OFFERED");
        assertThat(incentiveActions(get(API + "/" + p.id(), director))).isEmpty();
    }

    @Test
    void theZoneManagerOfTheSchoolApprovesAndAnotherZonesManagerGets404() {
        Mou m = mou();
        UUID otherOwner = fixture(m.admin()).manager().signed().userId();
        Pending p = pending(m, otherOwner);
        String outsider = fixture(m.admin()).manager().token();

        assertThat(get(API + "/" + p.id(), outsider).status()).isEqualTo(404);
        assertThat(post(API + "/" + p.id() + "/approve", outsider, Map.of("version", 0)).status()).isEqualTo(404);
        assertThat(post(API + "/" + p.id() + "/reject", outsider, Map.of("version", 0, "reason", "x")).status()).isEqualTo(404);
        assertThat(incentiveOf(p.schoolId()).get("status")).isEqualTo("PENDING_APPROVAL");

        Resp approved = approveIncentive(m.managerToken(), p.id());

        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(incentiveOf(p.schoolId()).get("decided_by")).isEqualTo(m.f().manager().signed().userId());
    }

    @Test
    void theEarnerWhoIsTheZoneManagerMayApproveOwnAndItIsFlaggedAndAudited() {
        Mou m = mou();
        Pending p = pendingOwnedByZoneManagerOf(m);

        Resp approved = approveIncentive(m.managerToken(), p.id());

        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> incentive = (Map<String, Object>) approved.map().get("incentive");
        assertThat(incentive).containsEntry("approvedByEarner", true).containsEntry("status", "APPROVED");
        assertThat(incentiveOf(p.schoolId()).get("approved_by_earner")).isEqualTo(true);
        assertThat(db.queryForObject(
                        "select count(*) from incentive_event where incentive_id = ? and kind = 'APPROVED' and detail = 'Approved by the earner'",
                        Integer.class,
                        p.id()))
                .isEqualTo(1);
        Resp detail = get(API + "/" + p.id(), m.admin());
        assertThat(detail.body()).contains("Approved by the earner");
    }

    @Test
    void anEarnerWhoIsNotTheZoneManagerSeesTheirIncentiveButCannotDecideIt() {
        Mou m = mou();
        Fixture other = fixture(m.admin());
        Pending p = pending(m, other.manager().signed().userId());
        String earner = other.manager().token();

        Resp detail = get(API + "/" + p.id(), earner);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(incentiveActions(detail)).isEmpty();
        assertThat(post(API + "/" + p.id() + "/approve", earner, Map.of("version", 0)).status()).isEqualTo(403);
        assertThat(post(API + "/" + p.id() + "/reject", earner, Map.of("version", 0, "reason", "x")).status()).isEqualTo(403);
        assertThat(incentiveOf(p.schoolId()).get("status")).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void adminTeacherAndSystemAreRefusedOnEveryDecision() {
        Mou m = mou();
        Pending p = pendingOwnedByZoneManagerOf(m);
        for (Role role : List.of(Role.ADMIN, Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            for (String path : List.of("/approve", "/reject", "/reopen")) {
                assertThat(post(API + "/" + p.id() + path, token, Map.of("version", 0, "reason", "x")).status())
                        .as(role + " " + path)
                        .isEqualTo(403);
            }
        }
        assertThat(get(API + "/" + p.id(), signInAs(Role.TEACHER).token()).status()).isEqualTo(403);
        assertThat(get(API + "/" + p.id(), signInAs(Role.SYSTEM).token()).status()).isEqualTo(403);
        assertThat(incentiveActions(get(API + "/" + p.id(), m.admin()))).isEmpty();
        assertThat(incentiveOf(p.schoolId()).get("status")).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void rejectingNeedsAReasonAndASecondDecisionNamesTheFirstDecider() {
        Mou m = mou();
        Pending p = pendingOwnedByZoneManagerOf(m);
        String director = m.director().token();

        assertThat(post(API + "/" + p.id() + "/reject", director, Map.of("version", version(director, p.id()))).status()).isEqualTo(400);
        assertThat(rejectIncentive(director, p.id(), "  ").status()).isEqualTo(400);
        assertThat(rejectIncentive(director, p.id(), "x".repeat(501)).status()).isEqualTo(400);
        Resp rejected = rejectIncentive(director, p.id(), "Not a closed School");
        assertThat(rejected.status()).as(rejected.body()).isEqualTo(200);
        assertThat(incentiveOf(p.schoolId()).get("status")).isEqualTo("REJECTED");
        assertThat(incentiveOf(p.schoolId()).get("reject_reason")).isEqualTo("Not a closed School");

        Resp second = post(API + "/" + p.id() + "/approve", m.managerToken(), Map.of("version", 1));
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body()).contains("rejected").contains("on ");
        assertThat(eventKinds(p.id())).containsExactly("EARNED", "REJECTED");
    }

    @Test
    void aStaleVersionIsRefusedWithConflict() {
        Mou m = mou();
        Pending p = pendingOwnedByZoneManagerOf(m);

        Resp stale = post(API + "/" + p.id() + "/approve", m.director().token(), Map.of("version", 99));

        assertThat(stale.status()).isEqualTo(409);
        assertThat(incentiveOf(p.schoolId()).get("status")).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void twoDecisionsAtTheSameTimeRecordExactlyOne() throws Exception {
        Mou m = mou();
        Pending p = pendingOwnedByZoneManagerOf(m);
        String director = m.director().token();
        long v = version(director, p.id());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Resp> approve = () -> post(API + "/" + p.id() + "/approve", director, Map.of("version", v));
            Callable<Resp> reject = () -> post(API + "/" + p.id() + "/reject", m.managerToken(), Map.of("version", v, "reason", "no"));
            List<Future<Resp>> results = pool.invokeAll(List.of(approve, reject));
            int ok = 0;
            int conflict = 0;
            for (Future<Resp> f : results) {
                int status = f.get().status();
                if (status == 200) {
                    ok++;
                } else if (status == 409) {
                    conflict++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(conflict).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(eventKinds(p.id()).stream().filter(k -> k.equals("APPROVED") || k.equals("REJECTED")).count()).isEqualTo(1);
    }

    @Test
    void aSchoolWithoutAZoneManagerCanBeDecidedByTheDirectorOnly() {
        Mou m = mou();
        Fixture other = fixture(m.admin());
        Pending p = pending(m, other.manager().signed().userId());
        // the School loses its Manager and its Zone loses its Zone Manager
        db.update("update zone_manager_assignment set ends_on = current_date where manager_id = ? and ends_on is null", m.f().manager().managerId());
        db.update("update school_manager_assignment set ends_on = current_date where school_id = ? and ends_on is null", m.f().schoolId());

        assertThat(get(API + "/" + p.id(), m.managerToken()).status()).isEqualTo(404);
        assertThat(approveIncentive(m.director().token(), p.id()).status()).isEqualTo(200);
    }

    @Test
    void aNewZoneManagerSeesAndDecidesWhatTheOldOneNoLongerSees() {
        Mou m = mou();
        Fixture other = fixture(m.admin());
        Pending p = pending(m, other.manager().signed().userId());
        ManagerCtx replacement = newManager(m.admin(), m.f().zoneId());
        assignSchoolManager(m.admin(), m.f().schoolId(), replacement.managerId());
        db.update("update zone_manager_assignment set ends_on = current_date where manager_id = ? and ends_on is null", m.f().manager().managerId());

        assertThat(get(API + "/" + p.id(), m.managerToken()).status()).isEqualTo(404);
        assertThat(get(API + "/" + p.id(), replacement.token()).status()).isEqualTo(200);
        assertThat(approveIncentive(replacement.token(), p.id()).status()).isEqualTo(200);
    }

    @Test
    void aRejectedIncentiveIsReopenedOnlyByTheDirectorWithAReason() {
        Mou m = mou();
        Pending p = pendingOwnedByZoneManagerOf(m);
        String director = m.director().token();
        assertThat(rejectIncentive(director, p.id(), "Wrong School").status()).isEqualTo(200);
        assertThat(incentiveActions(get(API + "/" + p.id(), director))).containsExactly("reopen");
        assertThat(incentiveActions(get(API + "/" + p.id(), m.managerToken()))).isEmpty();
        long v = version(director, p.id());

        assertThat(post(API + "/" + p.id() + "/reopen", director, Map.of("version", v)).status()).isEqualTo(400);
        // The earner, who is also the School's Zone Manager, cannot undo the Director's rejection.
        Resp refused = post(API + "/" + p.id() + "/reopen", m.managerToken(), Map.of("version", v, "reason", "Rejected by mistake"));
        assertThat(refused.status()).as(refused.body()).isEqualTo(403);
        assertThat(incentiveOf(p.schoolId()).get("status")).isEqualTo("REJECTED");
        Resp reopened = post(API + "/" + p.id() + "/reopen", director, Map.of("version", v, "reason", "Rejected by mistake"));

        assertThat(reopened.status()).as(reopened.body()).isEqualTo(200);
        assertThat(incentiveOf(p.schoolId()).get("status")).isEqualTo("PENDING_APPROVAL");
        assertThat(incentiveRows(p.schoolId())).isEqualTo(1);
        assertThat(eventKinds(p.id())).containsExactly("EARNED", "REJECTED", "REOPENED");
        assertThat(post(API + "/" + p.id() + "/reopen", director, Map.of("version", v + 1, "reason", "again")).status()).isEqualTo(409);
        assertThat(approveIncentive(director, p.id()).status()).isEqualTo(200);
    }

    @Test
    void aZoneManagerWhoIsNotTheEarnerCannotReopenEither() {
        Mou m = mou();
        Fixture other = fixture(m.admin());
        Pending p = pending(m, other.manager().signed().userId());
        assertThat(rejectIncentive(m.director().token(), p.id(), "No").status()).isEqualTo(200);
        long v = version(m.director().token(), p.id());

        assertThat(incentiveActions(get(API + "/" + p.id(), m.managerToken()))).isEmpty();
        assertThat(post(API + "/" + p.id() + "/reopen", m.managerToken(), Map.of("version", v, "reason", "x")).status()).isEqualTo(403);
        assertThat(incentiveOf(p.schoolId()).get("status")).isEqualTo("REJECTED");
    }

    @Test
    void aManagerOnlyAssignedToTheSchoolIsNotItsZoneManagerAndSeesOnlyTheirOwn() {
        Mou m = mou();
        ManagerCtx schoolOnly = newManager(m.admin());
        assignSchoolManager(m.admin(), m.f().schoolId(), schoolOnly.managerId());
        Pending others = pendingOwnedByZoneManagerOf(m);
        Mou second = mou();
        ManagerCtx earner = newManager(second.admin());
        assignSchoolManager(second.admin(), second.f().schoolId(), earner.managerId());
        Pending own = pending(second, earner.signed().userId());

        assertThat(get(API + "/" + others.id(), schoolOnly.token()).status()).isEqualTo(404);
        assertThat(post(API + "/" + others.id() + "/approve", schoolOnly.token(), Map.of("version", 0)).status()).isEqualTo(404);
        assertThat(get(API, schoolOnly.token()).body()).doesNotContain(others.id().toString());
        assertThat(get(API + "/summary", schoolOnly.token()).body()).doesNotContain(m.f().manager().managerId().toString());
        // an earner who is only the School's Manager sees their own incentive but cannot decide it
        Resp detail = get(API + "/" + own.id(), earner.token());
        assertThat(detail.status()).isEqualTo(200);
        assertThat(incentiveActions(detail)).isEmpty();
        assertThat(post(API + "/" + own.id() + "/approve", earner.token(), Map.of("version", 0)).status()).isEqualTo(403);
        assertThat(ids(get(API, earner.token()))).containsExactly(own.id().toString());
    }

    @Test
    void aReopenIsRefusedForAnOutsiderAndForTheEarnerWhoIsNotTheZoneManager() {
        Mou m = mou();
        Fixture other = fixture(m.admin());
        Pending p = pending(m, other.manager().signed().userId());
        assertThat(rejectIncentive(m.director().token(), p.id(), "No").status()).isEqualTo(200);
        String outsider = fixture(m.admin()).manager().token();

        assertThat(post(API + "/" + p.id() + "/reopen", outsider, Map.of("version", 1, "reason", "x")).status()).isEqualTo(404);
        assertThat(post(API + "/" + p.id() + "/reopen", other.manager().token(), Map.of("version", 1, "reason", "x")).status())
                .isEqualTo(403);
        assertThat(incentiveOf(p.schoolId()).get("status")).isEqualTo("REJECTED");
    }
}
