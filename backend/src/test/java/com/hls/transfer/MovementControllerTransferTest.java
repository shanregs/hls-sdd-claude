package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 027 T022: per role and per endpoint for the transfer flow. */
class MovementControllerTransferTest extends TransferTestBase {

    @SuppressWarnings("unchecked")
    private static List<String> actions(Resp resp) {
        return (List<String>) resp.map().get("actions");
    }

    @Test
    void adminCanRaiseAndCancelButNotApprove() {
        World w = world();
        UUID request = newTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        assertThat(actions(get(API + "/" + request, w.admin()))).containsExactlyInAnyOrder("CANCEL", "CHANGE");
        Resp approve = post(API + "/" + request + "/approve", w.admin(), Map.of("version", versionOf(w.admin(), request)));
        assertThat(approve.status()).isEqualTo(403);
        assertThat(cancel(w.admin(), request, "Raised in error").status()).isEqualTo(200);
        assertThat(statusOfRequest(w.admin(), request)).isEqualTo("CANCELLED");
    }

    @Test
    void theRequesterCannotApproveButAnotherDeciderCan() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        Resp own = get(API + "/" + request, w.managerA().token());
        assertThat(actions(own)).doesNotContain("APPROVE", "REJECT");
        assertThat(approve(w.managerA().token(), request).status()).isEqualTo(403);
        assertThat(actions(get(API + "/" + request, w.director()))).contains("APPROVE", "REJECT");
        Resp done = approve(w.director(), request);
        assertThat(done.status()).as(done.body()).isEqualTo(200);
        assertThat(done.map().get("status")).isEqualTo("APPROVED");
    }

    @Test
    void theLeavingSchoolsManagerCanApproveARequestRaisedByAnAdmin() {
        World w = world();
        UUID request = newTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        Resp done = approve(w.managerA().token(), request);
        assertThat(done.status()).as(done.body()).isEqualTo(200);
    }

    @Test
    void aManagerOfTheReceivingSchoolOnlyReadsButDoesNotDecide() {
        World w = world();
        UUID request = newTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        Resp read = get(API + "/" + request, w.managerB().token());
        assertThat(read.status()).isEqualTo(200);
        assertThat(actions(read)).isEmpty();
        assertThat(approve(w.managerB().token(), request).status()).isEqualTo(403);
        assertThat(reject(w.managerB().token(), request, "No").status()).isEqualTo(403);
        assertThat(cancel(w.managerB().token(), request, "No").status()).isEqualTo(403);
    }

    @Test
    void aManagerOutsideBothSchoolsGetsNotFound() {
        World w = world();
        UUID request = newTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        World other = world();
        String stranger = other.managerA().token();
        assertThat(get(API + "/" + request, stranger).status()).isEqualTo(404);
        assertThat(post(API + "/" + request + "/approve", stranger, Map.of("version", 0)).status()).isEqualTo(404);
        assertThat(raiseTransfer(stranger, w.t1(), w.schoolB1(), in(4)).status()).isEqualTo(404);
        assertThat(content(get(API, stranger)).stream().map(m -> m.get("id"))).doesNotContain(request.toString());
    }

    @Test
    void teacherAndSystemAreRefusedEverywhere() {
        World w = world();
        UUID request = newTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get(API, token).status()).as(role.name()).isEqualTo(403);
            assertThat(get(API + "/" + request, token).status()).as(role.name()).isEqualTo(403);
            assertThat(raiseTransfer(token, w.t1(), w.schoolB1(), in(4)).status()).as(role.name()).isEqualTo(403);
            assertThat(post(API + "/check", token, Map.of("kind", "TRANSFER")).status()).as(role.name()).isEqualTo(403);
            assertThat(get(API + "/vacancies?schoolId=" + w.schoolB1() + "&on=" + in(3), token).status()).as(role.name()).isEqualTo(403);
            assertThat(approve(w.admin(), request).status()).isEqualTo(403);
        }
    }

    @Test
    void checkIsADryRunAndWritesNothing() {
        World w = world();
        Map<String, Object> body = new java.util.HashMap<>(transferBody(w.t1(), w.schoolB1(), in(3)));
        body.put("kind", "TRANSFER");
        Resp ok = post(API + "/check", w.managerA().token(), body);
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        assertThat(ok.map().get("ok")).isEqualTo(true);
        assertThat(jdbc.queryForObject("select count(*) from movement_request where teacher_id = ?", Integer.class, w.t1())).isZero();

        body.put("effectiveOn", in(-2).toString());
        Resp past = post(API + "/check", w.managerA().token(), body);
        assertThat(past.map().get("ok")).isEqualTo(false);
        assertThat(past.body()).contains("has passed");
    }

    @Test
    void vacanciesListTheFreePositionsOfAnMouSchoolAndNothingWithoutAnMou() {
        World w = world();
        Fixture mou = schoolWithMou(w, 2);
        Resp vacancies = get(API + "/vacancies?schoolId=" + mou.schoolId() + "&on=" + in(3), w.managerA().token());
        assertThat(vacancies.status()).as(vacancies.body()).isEqualTo(200);
        assertThat(vacancies.body()).contains("positionId");
        Resp none = get(API + "/vacancies?schoolId=" + w.schoolB1() + "&on=" + in(3), w.managerA().token());
        assertThat(none.body()).isEqualTo("[]");
    }

    @Test
    void anUnknownKindIsRefused() {
        World w = world();
        Resp resp = post(API + "/check", w.admin(), Map.of("kind", "SWAP", "teacherId", w.t1()));
        assertThat(resp.status()).isEqualTo(400);
        LocalDate unused = in(1);
        assertThat(unused).isNotNull();
    }
}
