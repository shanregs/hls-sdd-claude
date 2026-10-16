package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Spec 013b T024, grown in later phases: the authorization matrix of contracts/payroll-run-api.md on every endpoint built
 * so far. A role that holds the permission must get past the permission check (any status but 401/403, here 404 for an
 * unknown id or 400 for an invalid body); a role that does not must get 403 and no figure in the body.
 */
class PayrollAuthorizationTest extends PayrollRunTestBase {

    private static final Set<Role> VIEW = EnumSet.of(Role.ADMIN, Role.DIRECTOR);
    private static final Set<Role> PROCESS = EnumSet.of(Role.ADMIN);

    private record Endpoint(String name, HttpMethod method, String path, Object body, Set<Role> allowed) {}

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private List<Endpoint> endpoints(UUID run, UUID line) {
        String unknown = UUID.randomUUID().toString();
        List<Endpoint> list = new ArrayList<>();
        list.add(new Endpoint("list runs", HttpMethod.GET, RUNS, null, VIEW));
        // an invalid month makes an allowed caller get 400 and so never creates a run
        list.add(new Endpoint("start run", HttpMethod.POST, RUNS, Map.of("month", "not-a-month"), PROCESS));
        list.add(new Endpoint("read run", HttpMethod.GET, RUNS + "/" + run, null, VIEW));
        list.add(new Endpoint("recompute", HttpMethod.POST, RUNS + "/" + unknown + "/recompute", Map.of(), PROCESS));
        list.add(new Endpoint("cancel", HttpMethod.POST, RUNS + "/" + unknown + "/cancel", Map.of("reason", "x"), PROCESS));
        list.add(new Endpoint("lines", HttpMethod.GET, RUNS + "/" + run + "/lines", null, VIEW));
        list.add(new Endpoint("one line", HttpMethod.GET, RUNS + "/" + run + "/lines/" + line, null, VIEW));
        return list;
    }

    @Test
    void everyEndpointFollowsTheMatrixForEveryRole() {
        String admin = signInAs(Role.ADMIN).token();
        UUID run = startAndCompute(admin);
        UUID line = UUID.fromString((String) allLines(admin, run).get(0).get("id"));
        Map<Role, String> tokens = new java.util.EnumMap<>(Role.class);
        for (Role role : EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            tokens.put(role, signInAs(role).token());
        }

        for (Endpoint endpoint : endpoints(run, line)) {
            for (Map.Entry<Role, String> caller : tokens.entrySet()) {
                Resp resp = send(endpoint.method(), endpoint.path(), caller.getValue(), endpoint.body());
                String what = endpoint.name() + " as " + caller.getKey() + ": " + resp.status() + " " + resp.body();
                if (endpoint.allowed().contains(caller.getKey())) {
                    assertThat(resp.status()).as(what).isNotIn(401, 403);
                } else {
                    assertThat(resp.status()).as(what).isEqualTo(403);
                    assertThat(resp.body() == null ? "" : resp.body())
                            .as("no figures for " + what)
                            .doesNotContain("earned")
                            .doesNotContain("net")
                            .doesNotContain("totals");
                }
            }
            Resp anonymous = send(endpoint.method(), endpoint.path(), null, endpoint.body());
            assertThat(anonymous.status()).as(endpoint.name() + " without a token").isEqualTo(401);
        }
    }

    @Test
    void aDirectorCanReadButNeitherStartNorComputeNorCancel() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID run = startAndCompute(admin);

        assertThat(get(RUNS + "/" + run, director).status()).isEqualTo(200);
        assertThat(startRun(director).status()).isEqualTo(403);
        assertThat(post(RUNS + "/" + run + "/recompute", director, Map.of()).status()).isEqualTo(403);
        assertThat(post(RUNS + "/" + run + "/cancel", director, Map.of("reason", "no")).status()).isEqualTo(403);
        assertThat(get(RUNS + "/" + run, admin).map().get("status")).isEqualTo("DRAFT");
    }
}
