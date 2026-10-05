package com.hls.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The induction rows of the role matrix: Admin and Director act; Zone Manager, Teacher and System are refused. */
class InductionApiTest extends TrainingTestBase {

    @Test
    void adminAndDirectorActAndEveryoneElseIs403() {
        String admin = admin();
        String director = directorToken();
        UUID batch = runningBatch(director, 5);
        UUID teacher = recruit(admin, director);
        UUID enrolment = enrolmentOf(batch, teacher);

        for (String token : List.of(admin, director)) {
            assertThat(get(IND + "/batches", token).status()).isEqualTo(200);
            assertThat(get(IND + "/to-be-enrolled", token).status()).isEqualTo(200);
            assertThat(get(IND + "/ready-to-deploy", token).status()).isEqualTo(200);
            assertThat(get(IND + "/batches/" + batch + "/roster", token).status()).isEqualTo(200);
        }
        assertThat(post(IND + "/batches", admin, batchBody(uniqueName("A"), today, today.plusDays(9), 2)).status()).isEqualTo(201);

        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get(IND + "/batches", token).status()).as(role + " list").isEqualTo(403);
            assertThat(get(IND + "/ready-to-deploy", token).status()).as(role + " ready").isEqualTo(403);
            assertThat(get(IND + "/batches/" + batch + "/roster", token).status()).as(role + " roster").isEqualTo(403);
            assertThat(post(IND + "/batches", token, batchBody("x", today, today.plusDays(2), 1)).status()).as(role + " create").isEqualTo(403);
            assertThat(enrol(token, batch, teacher).status()).as(role + " enrol").isEqualTo(403);
            assertThat(attend(token, enrolment, today.minusDays(1), "PRESENT", null).status()).as(role + " attendance").isEqualTo(403);
            assertThat(post(IND + "/enrolments/" + enrolment + "/signoff", token, Map.of("result", "COMPLETED")).status()).as(role + " signoff").isEqualTo(403);
        }
        assertThat(get(IND + "/batches", null).status()).isEqualTo(401);
    }
}
