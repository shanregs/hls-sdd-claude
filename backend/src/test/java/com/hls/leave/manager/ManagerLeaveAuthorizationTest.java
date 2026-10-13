package com.hls.leave.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 032 US3: who may call the Manager leave endpoints. */
class ManagerLeaveAuthorizationTest extends ManagerLeaveTestBase {

    private static final String[] DECISIONS = {"list", "approve", "reject", "revoke"};

    private Resp decision(String name, String token, UUID id) {
        return switch (name) {
            case "list" -> get(LEAVE, token);
            case "approve" -> post(LEAVE + "/" + id + "/approve", token, Map.of());
            case "reject" -> post(LEAVE + "/" + id + "/reject", token, Map.of("reason", "No"));
            case "revoke" -> post(LEAVE + "/" + id + "/revoke", token, Map.of("reason", "No"));
            default -> throw new IllegalArgumentException(name);
        };
    }


    @Test
    void aManagerUsesTheirOwnEndpointsAndOnlyForTheirOwnRequests() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        ManagerCtx other = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();

        assertThat(get(MY_LEAVE + "/types", manager.token()).status()).isEqualTo(200);
        assertThat(get(MY_LEAVE, manager.token()).status()).isEqualTo(200);
        UUID id = ask(manager.token(), days[0], days[1]).id();
        assertThat(listOf(get(MY_LEAVE, other.token()))).isEmpty();
        assertThat(delete(MY_LEAVE + "/" + id, other.token()).status()).isEqualTo(404);
        assertThat(delete(MY_LEAVE + "/" + id, manager.token()).status()).isEqualTo(204);
    }

    @Test
    void anAdminAndADirectorSucceedOnTheDecisionEndpoints() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();

        for (String token : new String[] {admin, director}) {
            UUID toReject = ask(manager.token(), days[0], days[0]).id();
            assertThat(decision("list", token, toReject).status()).isEqualTo(200);
            assertThat(decision("reject", token, toReject).status()).isEqualTo(200);
            UUID toApprove = ask(manager.token(), days[0], days[0]).id();
            assertThat(decision("approve", token, toApprove).status()).isEqualTo(200);
            assertThat(decision("revoke", token, toApprove).status()).isEqualTo(200);
        }
    }

    @Test
    void aManagerATeacherAndASystemUserGet403OnEveryDecisionEndpoint() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        UUID id = ask(manager.token(), twoWorkingDays()[0], twoWorkingDays()[0]).id();
        Map<String, String> tokens = Map.of(
                "manager", manager.token(),
                "teacher", teacher().token(),
                "system", signInAs(Role.SYSTEM).token());

        for (String name : DECISIONS) {
            tokens.forEach((who, token) -> assertThat(decision(name, token, id).status()).as(who + " " + name).isEqualTo(403));
        }
        assertThat(leaveStatus(id)).isEqualTo("PENDING");
    }

    @Test
    void anAdminATeacherAndASystemUserGet403OnTheManagersOwnEndpoints() {
        String admin = signInAs(Role.ADMIN).token();
        Map<String, String> tokens = Map.of(
                "admin", admin,
                "teacher", teacher().token(),
                "system", signInAs(Role.SYSTEM).token());

        tokens.forEach((who, token) -> {
            assertThat(get(MY_LEAVE + "/types", token).status()).as(who + " types").isEqualTo(403);
            assertThat(get(MY_LEAVE, token).status()).as(who + " list").isEqualTo(403);
            assertThat(delete(MY_LEAVE + "/" + UUID.randomUUID(), token).status()).as(who + " cancel").isEqualTo(403);
        });
    }
}
