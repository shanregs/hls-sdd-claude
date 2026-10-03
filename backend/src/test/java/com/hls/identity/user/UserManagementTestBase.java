package com.hls.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.auth.AuthDtos;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

/** Shared harness for spec 004's integration tests: real HTTP against a Testcontainers Postgres. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class UserManagementTestBase {

    static final String PASSWORD = "correct-horse-5";
    private static final AtomicLong PHONE_SEQUENCE = new AtomicLong(9_300_000_000L);

    /**
     * One container shared by every subclass (singleton pattern): a per-class {@code @Container} in
     * an abstract base is stopped after the first subclass, while Spring's cached context for later
     * subclasses still points at the stopped container's port.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAdminService userAdminService;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    record Signed(String token, UUID userId, String phone) {}

    record Resp(int status, String body) {}

    static String nextPhone() {
        return Long.toString(PHONE_SEQUENCE.incrementAndGet());
    }

    /** Creates a user directly (bypassing the endpoint under test) and signs them in. */
    Signed signInAs(Role... roles) {
        String phone = nextPhone();
        AppUser user = userAdminService.createUser("Tester " + phone, phone, Set.of(roles), null, PASSWORD);
        return signIn(user.getId(), phone, PASSWORD);
    }

    Signed signIn(UUID userId, String phone, String password) {
        AuthDtos.AuthResponse response = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, password))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody();
        return new Signed(response.accessToken(), userId, phone);
    }

    int loginStatus(String phone, String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, password))
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value();
    }

    Resp send(HttpMethod method, String uri, String token, Object body) {
        RestTestClient.RequestBodySpec spec = client.method(method).uri(uri);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        RestTestClient.RequestHeadersSpec<?> ready = body != null ? spec.body(body) : spec;
        var result = ready.exchange().returnResult(String.class);
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }

    Resp get(String uri, String token) {
        return send(HttpMethod.GET, uri, token, null);
    }

    Resp post(String uri, String token, Object body) {
        return send(HttpMethod.POST, uri, token, body);
    }

    Resp put(String uri, String token, Object body) {
        return send(HttpMethod.PUT, uri, token, body);
    }

    Map<String, Object> createUserViaApi(String adminToken, String name, Set<Role> roles) {
        String phone = nextPhone();
        var created = client.post()
                .uri("/api/v1/identity/users")
                .header("Authorization", "Bearer " + adminToken)
                .body(new UserManagementController.CreateUserRequest(name, phone, roles, null, null, PASSWORD))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = created;
        return result;
    }

    /** Makes the "exactly one / exactly two active Admins" tests deterministic. */
    void deactivateEveryExistingAdmin() {
        for (RoleAssignment assignment : roleAssignmentRepository.findAll()) {
            if (assignment.getRole() != Role.ADMIN) {
                continue;
            }
            appUserRepository.findById(assignment.getUserId()).ifPresent(u -> {
                u.deactivate();
                appUserRepository.save(u);
            });
        }
    }

    /** FR-008/SC-005: the entry reaches User Activity through the real path within 5 seconds. */
    void assertAuditEntry(String adminToken, UUID affectedUserId, String action) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Resp resp = get(
                    "/api/v1/audit/user-activity?affectedUserId=" + affectedUserId + "&action=" + action, adminToken);
            assertThat(resp.status()).isEqualTo(200);
            assertThat(resp.body()).contains("\"action\":\"" + action + "\"");
        });
    }

    static List<Role> rolesOf(Map<String, Object> user) {
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) user.get("roles");
        return names.stream().map(Role::valueOf).toList();
    }
}
