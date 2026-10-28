package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.teacher.api.TeacherMovementSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 027 T047 and T048: movement history for a Teacher and a School, and the Transferred and Replaced filters. */
class MovementHistoryTest extends TransferTestBase {

    @Autowired
    private TeacherMovementSource source;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lines(Resp resp) {
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (List<Map<String, Object>>) (List<?>) org.springframework.boot.json.JsonParserFactory.getJsonParser().parseList(resp.body());
    }

    @Test
    void aTeacherWithAnInitialPlacementAndATransferShowsBothWithKinds() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        List<Map<String, Object>> lines = lines(get(API + "/teachers/" + w.t1() + "/movements", w.director()));
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).get("kind")).isEqualTo("INITIAL");
        assertThat(lines.get(0).get("to")).isEqualTo(in(2).toString());
        assertThat(lines.get(1).get("kind")).isEqualTo("TRANSFER");
        assertThat(lines.get(1).get("from")).isEqualTo(in(3).toString());
        assertThat(lines.get(1).get("requestId")).isEqualTo(request.toString());
        assertThat(lines.get(1).get("reason")).asString().contains("MANAGER_REBALANCING");
        assertThat(lines.get(1).get("requestedBy")).isNotNull();
        assertThat(lines.get(1).get("approvedBy")).isNotNull();
    }

    @Test
    void aMoveWithNoRequestIsADirectMove() {
        World w = world();
        assertThat(assign(w.admin(), w.t1(), w.schoolB1(), null, in(2)).status()).isEqualTo(200);
        List<Map<String, Object>> lines = lines(get(API + "/teachers/" + w.t1() + "/movements", w.admin()));
        assertThat(lines).extracting(l -> l.get("kind")).containsExactly("INITIAL", "DIRECT");
    }

    @Test
    void replacementLinesNameTheOtherTeacherAndRejectedRequestsAreNotLines() {
        World w = world();
        UUID rejected = newReplacement(w.managerA().token(), w.t1(), w.t3(), in(3));
        assertThat(reject(w.director(), rejected, "No").status()).isEqualTo(200);
        assertThat(lines(get(API + "/teachers/" + w.t3() + "/movements", w.director()))).isEmpty();
        UUID request = newReplacement(w.managerA().token(), w.t1(), w.t3(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        List<Map<String, Object>> incoming = lines(get(API + "/teachers/" + w.t3() + "/movements", w.director()));
        assertThat(incoming).hasSize(1);
        assertThat(incoming.get(0).get("kind")).isEqualTo("REPLACEMENT");
        assertThat(((Map<?, ?>) incoming.get(0).get("otherTeacher")).get("id")).isEqualTo(w.t1().toString());
    }

    @Test
    void theSchoolListShowsArrivalsAndDeparturesWithTheOtherSideNamed() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        List<Map<String, Object>> departures = lines(get(API + "/schools/" + w.schoolA1() + "/movements", w.managerA().token()));
        assertThat(departures).hasSize(1);
        assertThat(departures.get(0).get("to")).isEqualTo(in(2).toString());
        assertThat(((Map<?, ?>) departures.get(0).get("otherSchool")).get("id")).isEqualTo(w.schoolB1().toString());
        List<Map<String, Object>> arrivals = lines(get(API + "/schools/" + w.schoolB1() + "/movements", w.managerB().token()));
        assertThat(arrivals).hasSize(1);
        assertThat(arrivals.get(0).get("from")).isEqualTo(in(3).toString());
    }

    @Test
    void aManagerOutsideScopeGetsNotFound() {
        World w = world();
        World other = world();
        assertThat(get(API + "/teachers/" + w.t1() + "/movements", other.managerA().token()).status()).isEqualTo(404);
        assertThat(get(API + "/schools/" + w.schoolA1() + "/movements", other.managerA().token()).status()).isEqualTo(404);
        assertThat(get(API + "/teachers/" + w.t1() + "/movements", w.managerA().token()).status()).isEqualTo(200);
    }

    @Test
    void theMovementSourceHoldsTransferredAndReplacedTeachers() {
        World w = world();
        UUID transfer = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(source.transferredTeacherIds()).doesNotContain(w.t1());
        assertThat(approve(w.director(), transfer).status()).isEqualTo(200);
        assertThat(source.transferredTeacherIds()).contains(w.t1());
        assertThat(source.replacedTeacherIds()).doesNotContain(w.t1());

        World r = world();
        UUID replacement = newReplacement(r.managerA().token(), r.t1(), r.t3(), in(3));
        assertThat(approve(r.director(), replacement).status()).isEqualTo(200);
        assertThat(source.replacedTeacherIds()).contains(r.t1()).doesNotContain(r.t3());

        World direct = world();
        assertThat(assign(direct.admin(), direct.t1(), direct.schoolB1(), null, in(2)).status()).isEqualTo(200);
        assertThat(source.transferredTeacherIds()).contains(direct.t1());
    }

    /** Spec 014c A5: counted in their effective month only; Draft and Rejected excluded; Applied counted. */
    @Test
    void movementsBetweenCountsApprovedAndAppliedRequestsInTheirMonthOnly() {
        World w = world();
        java.time.LocalDate day = in(3);
        UUID approved = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), day);
        assertThat(approve(w.director(), approved).status()).isEqualTo(200);
        World r = world();
        UUID replacement = newReplacement(r.managerA().token(), r.t1(), r.t3(), day);
        assertThat(approve(r.director(), replacement).status()).isEqualTo(200);
        World d = world();
        UUID draft = newTransfer(d.managerA().token(), d.t1(), d.schoolB1(), day);
        World x = world();
        UUID rejected = newTransfer(x.managerA().token(), x.t1(), x.schoolB1(), day);
        assertThat(reject(x.director(), rejected, "No").status()).isEqualTo(200);

        Map<UUID, TeacherMovementSource.Movements> inRange = source.movementsBetween(day, day);

        assertThat(inRange.get(w.t1())).isEqualTo(new TeacherMovementSource.Movements(1, 0));
        assertThat(inRange.get(r.t1())).isEqualTo(new TeacherMovementSource.Movements(0, 1));
        assertThat(inRange).doesNotContainKeys(r.t3(), d.t1(), x.t1());
        assertThat(source.movementsBetween(day.plusDays(1), day.plusDays(40))).doesNotContainKeys(w.t1(), r.t1());
        assertThat(source.movementsBetween(day.minusDays(40), day.minusDays(1))).doesNotContainKeys(w.t1(), r.t1());

        // an Approved request whose date has arrived is Applied, and still counted
        jdbc.update("update movement_request set effective_on = ? where id = ?", java.time.LocalDate.now(), approved);
        assertThat(source.movementsBetween(java.time.LocalDate.now(), java.time.LocalDate.now()).get(w.t1()))
                .isEqualTo(new TeacherMovementSource.Movements(1, 0));
    }

    @Test
    void theTeacherListFiltersByMovementInsideTheCallersScope() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        Resp admin = get("/api/v1/teachers?movement=TRANSFERRED&size=100", w.admin());
        assertThat(admin.status()).as(admin.body()).isEqualTo(200);
        assertThat(admin.body()).contains(w.t1().toString());
        // a Manager of a different zone sees none of them
        World other = world();
        assertThat(get("/api/v1/teachers?movement=TRANSFERRED&size=100", other.managerA().token()).body()).doesNotContain(w.t1().toString());
        assertThat(get("/api/v1/teachers?movement=REPLACED&size=100", w.admin()).body()).doesNotContain(w.t1().toString());
        assertThat(get("/api/v1/teachers?movement=BOGUS", w.admin()).status()).isEqualTo(400);
    }
}
