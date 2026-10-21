package com.hls.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PerformanceTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Spec 026 SC-005: every endpoint refuses Teacher and System, answers 401 without a token, and hides other Managers' records. */
class PerformanceAuthorizationTest extends PerformanceTestBase {

    private record Route(HttpMethod method, String path, Object body) {}

    private List<Route> routes() {
        UUID id = UUID.randomUUID();
        Map<String, Object> empty = Map.of();
        return List.of(
                new Route(HttpMethod.GET, PERF + "/options", null),
                new Route(HttpMethod.GET, PERF + "/summary?from=2026-01-01&to=2026-01-31", null),
                new Route(HttpMethod.GET, PERF + "/teachers/" + id, null),
                new Route(HttpMethod.GET, PERF + "/observations", null),
                new Route(HttpMethod.POST, PERF + "/observations", Map.of("teacherId", id)),
                new Route(HttpMethod.POST, PERF + "/observations/preview", Map.of("ratings", empty)),
                new Route(HttpMethod.GET, PERF + "/observations/" + id, null),
                new Route(HttpMethod.PUT, PERF + "/observations/" + id, empty),
                new Route(HttpMethod.POST, PERF + "/observations/" + id + "/submit", empty),
                new Route(HttpMethod.POST, PERF + "/observations/" + id + "/not-held", empty),
                new Route(HttpMethod.POST, PERF + "/observations/" + id + "/correct", empty),
                new Route(HttpMethod.GET, PERF + "/observations/" + id + "/attachments/" + id, null),
                new Route(HttpMethod.DELETE, PERF + "/observations/" + id + "/attachments/" + id + "?reason=x", null),
                new Route(HttpMethod.GET, PERF + "/feedback", null),
                new Route(HttpMethod.POST, PERF + "/feedback", empty),
                new Route(HttpMethod.GET, PERF + "/feedback/" + id, null),
                new Route(HttpMethod.POST, PERF + "/feedback/" + id + "/correct", empty),
                new Route(HttpMethod.POST, PERF + "/feedback/" + id + "/withdraw", empty),
                new Route(HttpMethod.POST, PERF + "/feedback/" + id + "/issue", empty),
                new Route(HttpMethod.POST, PERF + "/feedback/" + id + "/plan", empty),
                new Route(HttpMethod.GET, PERF + "/plans", null),
                new Route(HttpMethod.POST, PERF + "/plans", empty),
                new Route(HttpMethod.GET, PERF + "/plans/" + id, null),
                new Route(HttpMethod.PUT, PERF + "/plans/" + id, empty),
                new Route(HttpMethod.DELETE, PERF + "/plans/" + id + "?reason=x", null),
                new Route(HttpMethod.GET, PERF + "/issues", null),
                new Route(HttpMethod.GET, PERF + "/issues/" + id, null),
                new Route(HttpMethod.POST, PERF + "/issues/" + id + "/corrective-actions", empty),
                new Route(HttpMethod.POST, PERF + "/issues/" + id + "/escalate", empty),
                new Route(HttpMethod.POST, PERF + "/issues/" + id + "/referral", empty),
                new Route(HttpMethod.POST, PERF + "/issues/" + id + "/close", empty));
    }

    @Test
    void teacherAndSystemAreRefusedOnEveryRouteAndAnonymousCallersAreNotSignedIn() {
        String teacher = signInAs(Role.TEACHER).token();
        String system = signInAs(Role.SYSTEM).token();
        for (Route route : routes()) {
            assertThat(send(route.method(), route.path(), teacher, route.body()).status()).as("Teacher " + route.method() + " " + route.path()).isEqualTo(403);
            assertThat(send(route.method(), route.path(), system, route.body()).status()).as("System " + route.method() + " " + route.path()).isEqualTo(403);
            assertThat(send(route.method(), route.path(), null, route.body()).status()).as("anonymous " + route.method() + " " + route.path()).isEqualTo(401);
        }
    }

    @Test
    void aManagerOutsideTheSchoolGetsNotFoundOnEveryRecordRoute() {
        Setup s = setup();
        Setup other = setup();
        Map<String, Object> observation = observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        UUID observationId = id(observation);
        UUID issue = openIssueOf(s.teacherId());
        Resp feedback = post(PERF + "/feedback", s.managerToken(), Map.of("teacherId", s.teacherId(), "principalName", "Mrs Rao", "givenOn",
                today().toString(), "category", "TEACHING", "comments", "The lessons are hard to follow for the little ones", "actionRequired", true));
        UUID feedbackId = UUID.fromString((String) feedback.map().get("id"));
        UUID plan = UUID.fromString((String) plan(s.director(), anotherTeacher(s), today().plusDays(2), "MANAGEMENT_PLAN").map().get("id"));

        String token = other.managerToken();
        List<Route> hidden = List.of(
                new Route(HttpMethod.GET, PERF + "/teachers/" + s.teacherId(), null),
                new Route(HttpMethod.GET, PERF + "/observations/" + observationId, null),
                new Route(HttpMethod.PUT, PERF + "/observations/" + observationId, Map.of("version", 0)),
                new Route(HttpMethod.POST, PERF + "/observations/" + observationId + "/correct", Map.of("reason", "x", "form", Map.of("version", 0))),
                new Route(HttpMethod.GET, PERF + "/feedback/" + feedbackId, null),
                new Route(HttpMethod.POST, PERF + "/feedback/" + feedbackId + "/withdraw", Map.of("reason", "wrong person", "version", 0)),
                new Route(HttpMethod.POST, PERF + "/feedback/" + feedbackId + "/issue", Map.of()),
                new Route(HttpMethod.GET, PERF + "/plans/" + plan, null),
                new Route(HttpMethod.DELETE, PERF + "/plans/" + plan + "?reason=nope", null),
                new Route(HttpMethod.GET, PERF + "/issues/" + issue, null),
                new Route(HttpMethod.POST, PERF + "/issues/" + issue + "/escalate", Map.of("note", "not mine to escalate")),
                new Route(HttpMethod.POST, PERF + "/issues/" + issue + "/close", Map.of("outcome", "CANCELLED", "reason", "x", "version", 0)));
        for (Route route : hidden) {
            int status = send(route.method(), route.path(), token, route.body()).status();
            // close is Admin and Director only, so it may answer 403 before it looks the issue up; nothing else may differ from 404
            assertThat(status).as(route.method() + " " + route.path()).isIn(404, 403);
            if (!route.path().endsWith("/close")) {
                assertThat(status).as(route.method() + " " + route.path()).isEqualTo(404);
            }
        }
        // the owner and the Director can read the same records
        assertThat(get(PERF + "/observations/" + observationId, s.managerToken()).status()).isEqualTo(200);
        assertThat(get(PERF + "/observations/" + observationId, s.director()).status()).isEqualTo(200);
        assertThat(get(PERF + "/issues/" + issue, s.admin()).status()).isEqualTo(200);
    }

    @Test
    void aManagerWhoWroteARecordStillSeesItAfterTheTeacherMovesAway() {
        Setup s = setup();
        Map<String, Object> observation = observe(s.managerToken(), s.teacherId(), "GOOD", 4);
        UUID[] where = schoolInNewZone(s.admin());
        ManagerCtx second = newManager(s.admin(), where[0]);
        assignSchoolManager(s.admin(), where[2], second.managerId());
        placeTeacher(s.admin(), s.teacherId(), where[2]);

        // the Teacher is no longer theirs, but the record they wrote is
        assertThat(get(PERF + "/observations/" + observation.get("id"), s.managerToken()).status()).isEqualTo(200);
        assertThat(startObservation(s.managerToken(), s.teacherId()).status()).isEqualTo(404);
        assertThat(get(PERF + "/observations/" + observation.get("id"), second.token()).status()).isEqualTo(200);
    }
}
