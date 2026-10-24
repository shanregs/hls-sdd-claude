package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.transfer.internal.MovementEffectJob;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 027 US5 (T054 and T056) and the stale-request handling of T062: the request list and its filters, the
 * "took effect" job, and a request whose placement row was changed by another route.
 */
class MovementListAndEffectTest extends TransferTestBase {

    @Autowired
    private MovementEffectJob job;

    private List<String> ids(Resp resp) {
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return content(resp).stream().map(m -> (String) m.get("id")).toList();
    }

    // ---- list

    @Test
    void theListDefaultsToPendingAndApprovedAndFiltersByStatusSchoolAndDates() {
        World w = world();
        UUID pending = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        // only one open request per Teacher, so reject the first to raise the second
        assertThat(reject(w.director(), pending, "No").status()).isEqualTo(200);
        UUID second = newReplacement(w.managerA().token(), w.t1(), w.t3(), in(4));
        String token = w.managerA().token();
        List<String> defaults = ids(get(API + "?size=100", token));
        assertThat(defaults).contains(second.toString()).doesNotContain(pending.toString());
        assertThat(ids(get(API + "?status=REJECTED&size=100", token))).contains(pending.toString());
        assertThat(ids(get(API + "?status=PENDING&schoolId=" + w.schoolA1() + "&size=100", token))).contains(second.toString());
        assertThat(ids(get(API + "?status=PENDING&schoolId=" + UUID.randomUUID() + "&size=100", token))).isEmpty();
        assertThat(ids(get(API + "?status=PENDING&from=" + in(10) + "&size=100", token))).isEmpty();
        assertThat(ids(get(API + "?status=PENDING&to=" + in(10) + "&from=" + in(0) + "&size=100", token))).contains(second.toString());
        assertThat(get(API + "?status=NONSENSE", token).status()).isEqualTo(400);
    }

    @Test
    void theListIsScopedAndPagedAndRefusesTeacherAndSystem() {
        World w = world();
        UUID request = newTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        World other = world();
        assertThat(ids(get(API + "?size=100", other.managerA().token()))).doesNotContain(request.toString());
        assertThat(ids(get(API + "?size=100", w.managerB().token()))).contains(request.toString());
        Resp paged = get(API + "?size=1&page=0", w.director());
        assertThat(content(paged)).hasSizeLessThanOrEqualTo(1);
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(get(API, signInAs(role).token()).status()).isEqualTo(403);
        }
    }

    @Test
    void appliedShowsOnceTheDateArrivesWithNoJobRun() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        assertThat(statusOfRequest(w.director(), request)).isEqualTo("APPROVED");
        assertThat(ids(get(API + "?status=APPLIED&size=100", w.director()))).doesNotContain(request.toString());
        jdbc.update("update movement_request set effective_on = current_date where id = ?", request);
        assertThat(statusOfRequest(w.director(), request)).isEqualTo("APPLIED");
        assertThat(ids(get(API + "?status=APPLIED&size=100", w.director()))).contains(request.toString());
        assertThat(ids(get(API + "?size=100", w.director()))).doesNotContain(request.toString());
    }

    // ---- the daily job

    @Test
    void theJobSendsTheTookEffectNoticeOnceAndAMissedRunChangesNoStatus() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        job.runNow();
        assertThat(jdbc.queryForObject("select applied_notified_at from movement_request where id = ?", Object.class, request)).isNull();
        jdbc.update("update movement_request set effective_on = current_date where id = ?", request);
        int before = jdbc.queryForObject("select count(*) from movement_request_history where request_id = ? and action = 'APPLIED'", Integer.class, request);
        assertThat(before).isZero();
        MovementEffectJob.Result first = job.runNow();
        assertThat(first.applied()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from movement_request_history where request_id = ? and action = 'APPLIED'", Integer.class, request))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from movement_request where id = ? and applied_notified_at is not null", Integer.class, request))
                .isEqualTo(1);
        job.runNow();
        assertThat(jdbc.queryForObject("select count(*) from movement_request_history where request_id = ? and action = 'APPLIED'", Integer.class, request))
                .isEqualTo(1);
        // a late run leaves the status as it was derived
        assertThat(statusOfRequest(w.director(), request)).isEqualTo("APPLIED");
    }

    // ---- stale requests (T062)

    @Test
    void aDirectChangeThatCancelsACreatedRowMakesTheRequestReadAsCancelledAndTheJobClosesIt() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        // a direct placement made after approval replaces the scheduled move; the existing action itself is unchanged
        assertThat(assign(w.admin(), w.t1(), w.schoolA1(), null, in(5)).status()).isEqualTo(200);
        Map<String, Object> view = get(API + "/" + request, w.director()).map();
        assertThat(view.get("status")).isEqualTo("CANCELLED");
        assertThat(view.get("statusNote")).isEqualTo("Teacher no longer placed there");
        MovementEffectJob.Result result = job.runNow();
        assertThat(result.closedStale()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select status from movement_request where id = ?", String.class, request)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from movement_request_history where request_id = ? and action = 'CLOSED_STALE'", Integer.class, request))
                .isEqualTo(1);
    }
}
