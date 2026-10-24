package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.transfer.internal.MovementEffectJob;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 027 T055: who is told when a request is raised, decided and takes effect. */
class TransferNotificationTest extends TransferTestBase {

    @Autowired
    private MovementEffectJob job;

    /** Links a Teacher user to the Teacher so the Teacher can be notified. */
    private Signed linkUser(World w, UUID teacher) {
        Signed signed = signInAs(Role.TEACHER);
        Resp linked = put("/api/v1/teachers/" + teacher + "/user", w.admin(), Map.of("userId", signed.userId()));
        assertThat(linked.status()).as(linked.body()).isEqualTo(200);
        return signed;
    }

    @Test
    void requestedTellsTheLeavingManagerAndDirectorsOnceAndNotTheRequester() {
        World w = world();
        int directorsBefore = notifications(w.directorSigned().userId(), "TRANSFER_REQUESTED");
        newTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        assertThat(notifications(w.managerA().signed().userId(), "TRANSFER_REQUESTED")).isEqualTo(1);
        assertThat(notifications(w.directorSigned().userId(), "TRANSFER_REQUESTED")).isEqualTo(directorsBefore + 1);
        // the receiving Manager is not a decider and hears nothing yet
        assertThat(notifications(w.managerB().signed().userId(), "TRANSFER_REQUESTED")).isZero();

        World own = world();
        newTransfer(own.managerA().token(), own.t1(), own.schoolB1(), in(3));
        assertThat(notifications(own.managerA().signed().userId(), "TRANSFER_REQUESTED")).isZero();
    }

    @Test
    void decidedTellsTheRequesterAndOnApprovalTheTeachersAndBothManagers() {
        World w = world();
        Signed teacher = linkUser(w, w.t1());
        UUID request = newTransfer(w.admin(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        assertThat(notifications(w.managerA().signed().userId(), "TRANSFER_DECIDED")).isEqualTo(1);
        assertThat(notifications(w.managerB().signed().userId(), "TRANSFER_DECIDED")).isEqualTo(1);
        assertThat(notifications(teacher.userId(), "TRANSFER_DECIDED")).isEqualTo(1);
        // the requester is an Admin user who also hears of it; the decider does not
        assertThat(notifications(w.directorSigned().userId(), "TRANSFER_DECIDED")).isZero();

        World r = world();
        UUID rejected = newTransfer(r.managerA().token(), r.t1(), r.schoolB1(), in(3));
        assertThat(reject(r.director(), rejected, "No").status()).isEqualTo(200);
        assertThat(notifications(r.managerA().signed().userId(), "TRANSFER_DECIDED")).isEqualTo(1);
        assertThat(notifications(r.managerB().signed().userId(), "TRANSFER_DECIDED")).isZero();
    }

    @Test
    void appliedTellsTheSameSetOnce() {
        World w = world();
        Signed teacher = linkUser(w, w.t1());
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.director(), request).status()).isEqualTo(200);
        jdbc.update("update movement_request set effective_on = current_date where id = ?", request);
        job.runNow();
        job.runNow();
        assertThat(notifications(teacher.userId(), "TRANSFER_APPLIED")).isEqualTo(1);
        assertThat(notifications(w.managerA().signed().userId(), "TRANSFER_APPLIED")).isEqualTo(1);
        assertThat(notifications(w.managerB().signed().userId(), "TRANSFER_APPLIED")).isEqualTo(1);
    }
}
