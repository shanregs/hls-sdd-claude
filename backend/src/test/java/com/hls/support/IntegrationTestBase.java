package com.hls.support;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import com.hls.identity.user.UserAdminService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared harness for integration tests that talk real HTTP to a Testcontainers Postgres. One
 * container is shared by every subclass (singleton pattern): a per-class {@code @Container} in an
 * abstract base is stopped after the first subclass while Spring's cached context for later
 * subclasses still points at the stopped container's port.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "hls.notification.retention.enabled=false")
public abstract class IntegrationTestBase {

    public static final String PASSWORD = "correct-horse-5";
    private static final AtomicLong PHONE_SEQUENCE = new AtomicLong(9_500_000_000L);

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected UserAdminService userAdminService;

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected RoleAssignmentRepository roleAssignmentRepository;

    protected RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    public record Signed(String token, UUID userId, String phone) {}

    public record Resp(int status, String body) {
        @SuppressWarnings("unchecked")
        public Map<String, Object> map() {
            return JsonParserFactory.getJsonParser().parseMap(body);
        }

        public UUID id() {
            return UUID.fromString((String) map().get("id"));
        }
    }

    protected static String nextPhone() {
        return Long.toString(PHONE_SEQUENCE.incrementAndGet());
    }

    /** Creates a user directly and signs them in. */
    protected Signed signInAs(Role... roles) {
        String phone = nextPhone();
        var user = userAdminService.createUser("Tester " + phone, phone, Set.of(roles), null, PASSWORD);
        return signIn(user.getId(), phone, PASSWORD);
    }

    protected Signed signIn(UUID userId, String phone, String password) {
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

    protected int loginStatus(String phone, String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, password))
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value();
    }

    protected Resp send(HttpMethod method, String uri, String token, Object body) {
        RestTestClient.RequestBodySpec spec = client.method(method).uri(uri);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        RestTestClient.RequestHeadersSpec<?> ready = body != null ? spec.body(body) : spec;
        var result = ready.exchange().returnResult(String.class);
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }

    protected Resp get(String uri, String token) {
        return send(HttpMethod.GET, uri, token, null);
    }

    protected Resp post(String uri, String token, Object body) {
        return send(HttpMethod.POST, uri, token, body);
    }

    protected Resp put(String uri, String token, Object body) {
        return send(HttpMethod.PUT, uri, token, body);
    }

    protected Resp delete(String uri, String token) {
        return send(HttpMethod.DELETE, uri, token, null);
    }

    /** Content array of a list response. */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> content(Resp resp) {
        return (List<Map<String, Object>>) resp.map().get("content");
    }

    protected static long total(Resp resp) {
        return ((Number) resp.map().get("totalElements")).longValue();
    }
}
