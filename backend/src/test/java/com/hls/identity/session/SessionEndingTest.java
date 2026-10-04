package com.hls.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 001 FR-015 and FR-015a: a user ends one or all of their own sessions (the current one
 * included), and System lists and ends anyone's, one at a time, all of one user's, or everyone's.
 */
class SessionEndingTest extends IntegrationTestBase {

    private static final String ME = "/api/v1/me/sessions";
    private static final String ADMIN = "/api/v1/admin/sessions";

    @Autowired
    private PermissionMatrixService matrix;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mySessions(String token) {
        Resp resp = get(ME, token);
        assertThat(resp.status()).isEqualTo(200);
        return (List<Map<String, Object>>)
                (List<?>) org.springframework.boot.json.JsonParserFactory.getJsonParser().parseList(resp.body());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> adminSessions(String token, String query) {
        Resp resp = get(ADMIN + query, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (List<Map<String, Object>>) resp.map().get("content");
    }

    @Test
    void aUserSeesTheirSessionsOldestFirstWithTheCurrentOneMarked() {
        Signed first = signInAs(Role.MANAGER);
        Signed second = signIn(first.userId(), first.phone(), PASSWORD);

        List<Map<String, Object>> sessions = mySessions(second.token());

        assertThat(sessions).hasSize(2);
        assertThat(sessions).allSatisfy(s -> assertThat(s)
                .containsKeys("id", "signedInAt", "deviceDescription", "clientType"));
        assertThat(sessions.get(0).get("signedInAt").toString())
                .isLessThanOrEqualTo(sessions.get(1).get("signedInAt").toString());
        assertThat(sessions.stream().filter(s -> Boolean.TRUE.equals(s.get("current")))).hasSize(1);
    }

    @Test
    void aUserCanEndTheirCurrentSessionAndNotSomeoneElses() {
        Signed me = signInAs(Role.TEACHER);
        Signed other = signInAs(Role.TEACHER);
        String otherSession = (String) mySessions(other.token()).get(0).get("id");
        String mySession = (String) mySessions(me.token()).get(0).get("id");

        assertThat(delete(ME + "/" + otherSession, me.token()).status()).isEqualTo(403);
        assertThat(delete(ME + "/" + mySession, me.token()).status()).isEqualTo(204);

        // The ended session's token stops working at once.
        assertThat(get(ME, me.token()).status()).isEqualTo(401);
        assertThat(mySessions(other.token())).hasSize(1);
    }

    @Test
    void endAllEndsEverySessionOfTheCallerIncludingTheCurrentOneAndNoOneElses() {
        Signed me = signInAs(Role.MANAGER);
        signIn(me.userId(), me.phone(), PASSWORD);
        Signed other = signInAs(Role.MANAGER);

        Resp resp = delete(ME, me.token());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("ended", 2);
        assertThat(get(ME, me.token()).status()).isEqualTo(401);
        assertThat(mySessions(other.token())).hasSize(1);
        // Signing in again starts a fresh session, so nothing is lost but the old sessions.
        Signed again = signIn(me.userId(), me.phone(), PASSWORD);
        assertThat(mySessions(again.token())).hasSize(1);
    }

    @Test
    void systemListsEveryonesSessionsWithTheOwnerAndCanFilterByUser() {
        Signed system = signInAs(Role.SYSTEM);
        Signed teacher = signInAs(Role.TEACHER);

        List<Map<String, Object>> all = adminSessions(system.token(), "?size=100");
        List<Map<String, Object>> onlyTeacher = adminSessions(system.token(), "?userId=" + teacher.userId());

        assertThat(all.stream().map(s -> s.get("userId")).distinct().count()).isGreaterThanOrEqualTo(2);
        assertThat(onlyTeacher).hasSize(1);
        assertThat(onlyTeacher.get(0))
                .containsEntry("userId", teacher.userId().toString())
                .containsEntry("userPhone", teacher.phone());
        assertThat(onlyTeacher.get(0).get("userName").toString()).startsWith("Tester");
        assertThat(all.stream().filter(s -> Boolean.TRUE.equals(s.get("current")))).hasSize(1);
    }

    @Test
    void systemEndsOneSessionOfAnotherUserWithoutSigningItselfOut() {
        Signed system = signInAs(Role.SYSTEM);
        Signed teacher = signInAs(Role.TEACHER);
        String teacherSession = (String) adminSessions(system.token(), "?userId=" + teacher.userId())
                .get(0)
                .get("id");

        Resp resp = delete(ADMIN + "/" + teacherSession, system.token());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("ended", 1).containsEntry("includesCurrent", false);
        assertThat(adminSessions(system.token(), "?userId=" + teacher.userId())).isEmpty();
        assertThat(mySessions(system.token())).hasSize(1);
        assertThat(delete(ADMIN + "/" + teacherSession, system.token()).status()).isEqualTo(404);
    }

    @Test
    void systemEndsAllSessionsOfOneUser() {
        Signed system = signInAs(Role.SYSTEM);
        Signed teacher = signInAs(Role.TEACHER);
        signIn(teacher.userId(), teacher.phone(), PASSWORD);
        Signed bystander = signInAs(Role.TEACHER);

        Resp resp = delete(ADMIN + "?userId=" + teacher.userId(), system.token());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("ended", 2).containsEntry("includesCurrent", false);
        assertThat(adminSessions(system.token(), "?userId=" + teacher.userId())).isEmpty();
        assertThat(adminSessions(system.token(), "?userId=" + bystander.userId())).hasSize(1);
    }

    @Test
    void systemEndsEveryonesSessionsIncludingItsOwn() {
        Signed system = signInAs(Role.SYSTEM);
        Signed teacher = signInAs(Role.TEACHER);

        Resp resp = delete(ADMIN, system.token());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("includesCurrent", true);
        assertThat(((Number) resp.map().get("ended")).intValue()).isGreaterThanOrEqualTo(2);
        assertThat(get(ME, teacher.token()).status()).isEqualTo(401);
        assertThat(get(ME, system.token()).status()).isEqualTo(401);
    }

    @Test
    void everyRoleMayViewAndDeleteItsOwnSessionsByDefault() {
        for (Role role : Role.values()) {
            for (PermissionAction action : List.of(PermissionAction.VIEW, PermissionAction.DELETE)) {
                assertThat(matrix.isGranted(role, PermissionModule.MY_SESSIONS, action))
                        .as("%s %s", role, action)
                        .isTrue();
            }
            Signed me = signInAs(role);
            assertThat(get(ME, me.token()).status()).as("%s list", role).isEqualTo(200);
        }
    }

    @Test
    void takingDeleteAwayFromARoleStopsItEndingSessionsButNotListingThem() {
        Signed teacher = signInAs(Role.TEACHER);
        String sessionId = (String) mySessions(teacher.token()).get(0).get("id");
        UUID actor = UUID.randomUUID();
        matrix.updateGrant(Role.TEACHER, PermissionModule.MY_SESSIONS, PermissionAction.DELETE, false, actor);
        try {
            assertThat(get(ME, teacher.token()).status()).isEqualTo(200);
            assertThat(delete(ME + "/" + sessionId, teacher.token()).status()).isEqualTo(403);
            assertThat(delete(ME, teacher.token()).status()).isEqualTo(403);
            // Another role is unaffected.
            Signed manager = signInAs(Role.MANAGER);
            assertThat(delete(ME, manager.token()).status()).isEqualTo(200);
        } finally {
            matrix.updateGrant(Role.TEACHER, PermissionModule.MY_SESSIONS, PermissionAction.DELETE, true, actor);
        }
        assertThat(delete(ME + "/" + sessionId, teacher.token()).status()).isEqualTo(204);
    }

    @Test
    void takingViewAwayStopsListingAndHidesTheSessionsMenuItem() {
        Signed director = signInAs(Role.DIRECTOR);
        UUID actor = UUID.randomUUID();
        matrix.updateGrant(Role.DIRECTOR, PermissionModule.MY_SESSIONS, PermissionAction.VIEW, false, actor);
        try {
            assertThat(get(ME, director.token()).status()).isEqualTo(403);
            Resp access = get("/api/v1/me/access-model", director.token());
            assertThat(access.body()).doesNotContain("/account/sessions");
        } finally {
            matrix.updateGrant(Role.DIRECTOR, PermissionModule.MY_SESSIONS, PermissionAction.VIEW, true, actor);
        }
        assertThat(get(ME, director.token()).status()).isEqualTo(200);
    }

    @Test
    void onlySystemMaySeeOrEndEveryonesSessions() {
        Signed target = signInAs(Role.TEACHER);
        String targetSession = (String) mySessions(target.token()).get(0).get("id");

        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER}) {
            String token = signInAs(role).token();
            assertThat(get(ADMIN, token).status()).as("%s list", role).isEqualTo(403);
            assertThat(delete(ADMIN + "/" + targetSession, token).status()).as("%s end one", role).isEqualTo(403);
            assertThat(delete(ADMIN, token).status()).as("%s end all", role).isEqualTo(403);
        }
        assertThat(get(ADMIN, null).status()).isEqualTo(401);
        assertThat(delete(ADMIN, null).status()).isEqualTo(401);
        assertThat(mySessions(target.token())).hasSize(1);
        assertThat(UUID.fromString(targetSession)).isNotNull();
    }
}
