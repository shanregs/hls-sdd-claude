package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 027 T021: raise, change, approve, reject and cancel of a transfer, and what each writes. */
class MovementServiceTransferTest extends TransferTestBase {

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void raiseWritesOneRequestOneParticipantOneHistoryRowAndChangesNoPlacement() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(count("select count(*) from movement_request where id = ? and status = 'PENDING'", request)).isEqualTo(1);
        assertThat(count("select count(*) from movement_participant where request_id = ? and open", request)).isEqualTo(1);
        assertThat(count("select count(*) from movement_request_history where request_id = ? and action = 'RAISED'", request)).isEqualTo(1);
        assertThat(rows(w.t1())).hasSize(1);
        assertThat(rows(w.t1()).get(0).get("ends_on")).isNull();
        assertChangeRecorded(w.admin(), "MOVEMENT_REQUEST", request, "created");
    }

    @Test
    void aSecondRequestForTheSameTeacherIsRefused() {
        World w = world();
        newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        Resp second = raiseTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(5));
        assertThat(second.status()).as(second.body()).isEqualTo(409);
        assertThat(second.body()).contains("already in another open");
    }

    @Test
    void refusalsCarryThePlainReason() {
        World w = world();
        // past date
        Resp past = raiseTransfer(w.admin(), w.t1(), w.schoolB1(), in(-1));
        assertThat(past.status()).isEqualTo(409);
        assertThat(past.body()).contains("has passed");
        // not placed anywhere
        Resp unplaced = raiseTransfer(w.admin(), w.t3(), w.schoolB1(), in(3));
        assertThat(unplaced.status()).isEqualTo(409);
        assertThat(unplaced.body()).contains("not placed");
        // same School
        Resp same = raiseTransfer(w.admin(), w.t1(), w.schoolA1(), in(3));
        assertThat(same.status()).isEqualTo(409);
        assertThat(same.body()).contains("different");
        // inactive receiving School
        jdbc.update("update school set active = false where id = ?", w.schoolB1());
        Resp inactive = raiseTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        jdbc.update("update school set active = true where id = ?", w.schoolB1());
        assertThat(inactive.status()).isEqualTo(409);
        assertThat(inactive.body()).contains("inactive");
    }

    @Test
    void aTeacherInTrainingIsRefused() {
        World w = world();
        Resp created = post("/api/v1/teachers", w.admin(), Map.of("name", uniqueName("Trainee"), "phone", "9444444445", "status", "IN_TRAINING"));
        UUID trainee = created.id();
        jdbc.update(
                "insert into contract_assignment (id, teacher_id, school_id, starts_on, status, created_at) values (?, ?, ?, current_date - 5, 'ACTIVE', now())",
                UUID.randomUUID(), trainee, w.schoolA1());
        Resp resp = raiseTransfer(w.admin(), trainee, w.schoolB1(), in(3));
        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("in training");
    }

    @Test
    void noVacancyAndOccupiedPositionAreRefused() {
        World w = world();
        Fixture mou = schoolWithMou(w, 1);
        UUID other = teacher(w.admin());
        placeTeacher(w.admin(), other, mou.schoolId());
        Resp full = raiseTransfer(w.admin(), w.t1(), mou.schoolId(), in(3));
        assertThat(full.status()).isEqualTo(409);
        assertThat(full.body()).contains("filled");

        Fixture roomy = schoolWithPerTeacherMou(w, 2);
        UUID taken = positionOf(roomy.schoolId(), 1);
        UUID filler = teacher(w.admin());
        assertThat(assign(w.admin(), filler, roomy.schoolId(), taken, null).status()).isEqualTo(200);
        Map<String, Object> body = transferBody(w.t1(), roomy.schoolId(), in(3));
        body.put("positionId", taken);
        Resp named = post(API + "/transfers", w.admin(), body);
        assertThat(named.status()).isEqualTo(409);
        assertThat(named.body()).contains("already filled");
        // a per-Teacher MoU needs a named position; the free one works
        assertThat(raiseTransfer(w.admin(), w.t1(), roomy.schoolId(), in(3)).status()).isEqualTo(409);
        body.put("positionId", positionOf(roomy.schoolId(), 2));
        assertThat(post(API + "/transfers", w.admin(), body).status()).isEqualTo(201);
    }

    @Test
    void rejectNeedsAReasonAndChangesNothing() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        Map<String, Object> blank = new HashMap<>();
        blank.put("version", versionOf(w.director(), request));
        blank.put("reason", "  ");
        assertThat(post(API + "/" + request + "/reject", w.director(), blank).status()).isEqualTo(400);
        Resp rejected = reject(w.director(), request, "Not needed");
        assertThat(rejected.status()).as(rejected.body()).isEqualTo(200);
        assertThat(rejected.map().get("status")).isEqualTo("REJECTED");
        assertThat(count("select count(*) from movement_participant where request_id = ? and open", request)).isZero();
        assertThat(rows(w.t1())).hasSize(1);
        // a new request is possible again
        assertThat(raiseTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(4)).status()).isEqualTo(201);
    }

    @Test
    void approveEndsTheOldRowTheDayBeforeAndStartsTheNewOneOnTheDate() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        Resp approved = approve(w.director(), request);
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(approved.map().get("status")).isEqualTo("APPROVED");
        List<Map<String, Object>> rows = rows(w.t1());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("school_id")).isEqualTo(w.schoolA1());
        assertThat(rows.get(0).get("ends_on").toString()).isEqualTo(in(2).toString());
        assertThat(rows.get(1).get("school_id")).isEqualTo(w.schoolB1());
        assertThat(rows.get(1).get("starts_on").toString()).isEqualTo(in(3).toString());
        assertThat(count("select count(*) from movement_assignment where request_id = ? and effect = 'ENDED'", request)).isEqualTo(1);
        assertThat(count("select count(*) from movement_assignment where request_id = ? and effect = 'CREATED'", request)).isEqualTo(1);
        assertThat(count("select count(*) from movement_request_history where request_id = ? and action = 'APPROVED'", request)).isEqualTo(1);
        assertThat(count("select count(*) from movement_participant where request_id = ? and open", request)).isZero();
    }

    @Test
    void approveWithAStaleVersionIsA409() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        Resp stale = post(API + "/" + request + "/approve", w.director(), Map.of("version", 99));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.body()).contains("changed");
    }

    @Test
    void approveRechecksAndRefusesWhenThingsChanged() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        // the Teacher gets a non-leave mark on the effective date after the request was raised
        mark(w.t1(), in(3), PRESENT, w.schoolA1());
        Resp refused = approve(w.director(), request);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("attendance marked");
        assertThat(rows(w.t1())).hasSize(1);
        assertThat(statusOfRequest(w.director(), request)).isEqualTo("PENDING");
    }

    @Test
    void cancelOfPendingAndOfAFutureApprovedRequestNeedsAReasonAndRestoresThePlacement() {
        World w = world();
        UUID pending = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        Map<String, Object> blank = new HashMap<>();
        blank.put("version", versionOf(w.managerA().token(), pending));
        assertThat(post(API + "/" + pending + "/cancel", w.managerA().token(), blank).status()).isEqualTo(400);
        assertThat(cancel(w.managerA().token(), pending, "Changed my mind").status()).isEqualTo(200);
        assertThat(statusOfRequest(w.director(), pending)).isEqualTo("CANCELLED");

        UUID second = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(4));
        assertThat(approve(w.director(), second).status()).isEqualTo(200);
        assertThat(rows(w.t1())).hasSize(2);
        Resp cancelled = cancel(w.managerA().token(), second, "School asked to keep the Teacher");
        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        List<Map<String, Object>> rows = rows(w.t1());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("school_id")).isEqualTo(w.schoolA1());
        assertThat(rows.get(0).get("ends_on")).isNull();
    }

    @Test
    void aRequestThatHasTakenEffectCannotBeCancelled() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        jdbc.update("update movement_request set effective_on = current_date where id = ?", request);
        assertThat(statusOfRequest(w.director(), request)).isEqualTo("APPLIED");
        Resp resp = cancel(w.director(), request, "Too late");
        assertThat(resp.status()).isEqualTo(409);
    }

    @Test
    void changePendingIsVersionCheckedAndWritesAChangedHistoryRow() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        Map<String, Object> stale = Map.of("version", 42, "effectiveOn", in(5).toString());
        assertThat(patch(request, w.managerA().token(), stale).status()).isEqualTo(409);
        Map<String, Object> body = Map.of("version", versionOf(w.managerA().token(), request), "effectiveOn", in(5).toString());
        Resp ok = patch(request, w.managerA().token(), body);
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        assertThat(ok.map().get("effectiveOn")).isEqualTo(in(5).toString());
        assertThat(count("select count(*) from movement_request_history where request_id = ? and action = 'CHANGED'", request)).isEqualTo(1);
        // moving the date into the past is refused
        Map<String, Object> past = Map.of("version", versionOf(w.managerA().token(), request), "effectiveOn", in(-1).toString());
        assertThat(patch(request, w.managerA().token(), past).status()).isEqualTo(409);
    }

    @Test
    void aPendingRequestWhoseDateHasPassedCannotBeApprovedUntilTheDateIsChanged() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        jdbc.update("update movement_request set effective_on = current_date - 1 where id = ?", request);
        Resp refused = approve(w.director(), request);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("has passed");
        Map<String, Object> body = Map.of("version", versionOf(w.managerA().token(), request), "effectiveOn", in(2).toString());
        assertThat(patch(request, w.managerA().token(), body).status()).isEqualTo(200);
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
    }

    @Test
    void anOtherReasonNeedsANote() {
        World w = world();
        Map<String, Object> body = transferBody(w.t1(), w.schoolB1(), in(3));
        body.put("reasonCategory", "OTHER");
        assertThat(post(API + "/transfers", w.admin(), body).status()).isEqualTo(400);
        body.put("note", "Family move");
        assertThat(post(API + "/transfers", w.admin(), body).status()).isEqualTo(201);
    }

    protected Resp patch(UUID request, String token, Object body) {
        return send(org.springframework.http.HttpMethod.PATCH, API + "/" + request, token, body);
    }
}
