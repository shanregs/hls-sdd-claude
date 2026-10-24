package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.api.SchoolContracts;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 027 T031, T032 and T033: replacement requests through the API, their roles and the day-by-day invariant. */
class MovementReplacementTest extends TransferTestBase {

    @Autowired
    private SchoolContracts contracts;

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void raiseNeedsAnActiveUnplacedReplacementWithNoOpenRequest() {
        World w = world();
        UUID placed = teacher(w.admin());
        placeTeacher(w.admin(), placed, w.schoolB1());
        Resp refused = post(API + "/replacements", w.admin(), replacementBody(w.t1(), placed, in(3)));
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("already placed");

        Resp trainee = post("/api/v1/teachers", w.admin(), Map.of("name", uniqueName("Trainee"), "phone", "9444444446", "status", "IN_TRAINING"));
        Resp inTraining = post(API + "/replacements", w.admin(), replacementBody(w.t1(), trainee.id(), in(3)));
        assertThat(inTraining.status()).isEqualTo(409);
        assertThat(inTraining.body()).contains("in training");

        UUID request = newReplacement(w.managerA().token(), w.t1(), w.t3(), in(3));
        assertThat(count("select count(*) from movement_participant where request_id = ? and open", request)).isEqualTo(2);
        // the replacement Teacher is now in an open request, so cannot be named again
        World other = world();
        Resp busy = post(API + "/replacements", other.admin(), replacementBody(other.t1(), w.t3(), in(3)));
        assertThat(busy.status()).isEqualTo(409);
        assertThat(busy.body()).contains("already in another open");
    }

    @Test
    void approveAppliesAndLinksTheRows() {
        World w = world();
        UUID request = newReplacement(w.managerA().token(), w.t1(), w.t3(), in(3));
        Resp approved = approve(w.director(), request);
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(schoolOn(w.t1(), in(3))).isNull();
        assertThat(schoolOn(w.t3(), in(3))).isEqualTo(w.schoolA1());
        assertThat(count("select count(*) from movement_assignment where request_id = ?", request)).isEqualTo(2);
    }

    @Test
    void aDestinationMovesTheOutgoingTeacherAndItIsNamedOnTheRequest() {
        World w = world();
        Map<String, Object> body = new java.util.HashMap<>(replacementBody(w.t1(), w.t3(), in(3)));
        body.put("outgoingDestinationSchoolId", w.schoolB1());
        Resp created = post(API + "/replacements", w.admin(), body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(approve(w.director(), created.id()).status()).isEqualTo(200);
        assertThat(schoolOn(w.t1(), in(3))).isEqualTo(w.schoolB1());
        assertThat(schoolOn(w.t3(), in(3))).isEqualTo(w.schoolA1());
        assertThat(((Map<?, ?>) get(API + "/" + created.id(), w.director()).map().get("outgoingDestinationSchool")).get("id"))
                .isEqualTo(w.schoolB1().toString());
    }

    @Test
    void aConflictingMouRemappingRefusesApprovalWithNothingPartlyApplied() {
        World w = world();
        UUID request = newReplacement(w.managerA().token(), w.t1(), w.t3(), in(3));
        // the replacement Teacher is placed elsewhere on the same date by another route after the request was raised
        assertThat(assign(w.admin(), w.t3(), w.schoolB1(), null, in(2)).status()).isEqualTo(200);
        Resp refused = approve(w.director(), request);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(rows(w.t1())).hasSize(1);
        assertThat(rows(w.t1()).get(0).get("ends_on")).isNull();
    }

    @Test
    void rejectAndCancelWorkAsForTransfers() {
        World w = world();
        UUID first = newReplacement(w.managerA().token(), w.t1(), w.t3(), in(3));
        assertThat(reject(w.director(), first, "Not needed").status()).isEqualTo(200);
        UUID second = newReplacement(w.managerA().token(), w.t1(), w.t3(), in(3));
        assertThat(approve(w.director(), second).status()).isEqualTo(200);
        assertThat(cancel(w.managerA().token(), second, "Changed plan").status()).isEqualTo(200);
        assertThat(rows(w.t1())).hasSize(1);
        assertThat(rows(w.t1()).get(0).get("ends_on")).isNull();
        assertThat(rows(w.t3())).isEmpty();
    }

    @Test
    void candidatesAreActiveUnplacedAndFree() {
        World w = world();
        Resp resp = get(API + "/candidates?schoolId=" + w.schoolA1() + "&on=" + in(3), w.managerA().token());
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.body()).contains(w.t3().toString()).doesNotContain(w.t1().toString());
        newReplacement(w.managerA().token(), w.t1(), w.t3(), in(3));
        assertThat(get(API + "/candidates?schoolId=" + w.schoolA1() + "&on=" + in(3), w.managerA().token()).body())
                .doesNotContain(w.t3().toString());
        // a Manager of another School gets not-found, Teacher and System are refused
        assertThat(get(API + "/candidates?schoolId=" + w.schoolA1() + "&on=" + in(3), w.managerB().token()).status()).isEqualTo(404);
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(get(API + "/candidates?schoolId=" + w.schoolA1() + "&on=" + in(3), signInAs(role).token()).status()).isEqualTo(403);
            assertThat(post(API + "/replacements", signInAs(role).token(), replacementBody(w.t1(), w.t3(), in(3))).status()).isEqualTo(403);
        }
    }

    @Test
    void replacementCheckIsADryRun() {
        World w = world();
        Map<String, Object> body = new java.util.HashMap<>(replacementBody(w.t1(), w.t3(), in(3)));
        body.put("kind", "REPLACEMENT");
        Resp ok = post(API + "/check", w.managerA().token(), body);
        assertThat(ok.map().get("ok")).isEqualTo(true);
        assertThat(count("select count(*) from movement_request where teacher_id = ?", w.t1())).isZero();
    }

    private void invariantFor(int daysAhead) {
        World w = world();
        Fixture mou = schoolWithMou(w, 2);
        UUID outgoing = teacher(w.admin());
        placeTeacher(w.admin(), outgoing, mou.schoolId());
        UUID incoming = teacher(w.admin());
        LocalDate date = in(daysAhead);
        int filled = contracts.occupancyOf(mou.schoolId(), date.minusDays(1)).filled();
        UUID request = newReplacement(w.admin(), outgoing, incoming, date);
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        for (int d = -2; d <= 2; d++) {
            if (date.plusDays(d).isBefore(LocalDate.now())) {
                continue;
            }
            assertThat(contracts.occupancyOf(mou.schoolId(), date.plusDays(d)).filled()).as("filled on " + date.plusDays(d)).isEqualTo(filled);
        }
        assertThat(schoolOn(outgoing, date)).isNull();
        assertThat(schoolOn(incoming, date)).isEqualTo(mou.schoolId());
    }

    @Test
    void filledCountIsUnchangedAroundATomorrowMonthEndAndYearBoundaryDate() {
        LocalDate now = LocalDate.now();
        invariantFor(1);
        invariantFor((int) java.time.temporal.ChronoUnit.DAYS.between(now, now.withDayOfMonth(now.lengthOfMonth()).plusDays(1)));
        invariantFor((int) java.time.temporal.ChronoUnit.DAYS.between(now, LocalDate.of(now.getYear() + 1, 1, 1)));
    }
}
