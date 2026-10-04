package com.hls.audit.logs;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.audit.changehistory.ChangeHistoryEntry;
import com.hls.audit.changehistory.ChangeHistoryEntryRepository;
import com.hls.audit.loginhistory.LoginHistoryEntry;
import com.hls.audit.loginhistory.LoginHistoryEntryRepository;
import com.hls.audit.useractivity.UserActivityEntry;
import com.hls.audit.useractivity.UserActivityEntryRepository;
import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * SC-004 ("remains responsive when paginating through at least 10,000 entries") — untested by any
 * of T010-T051's functional tests, which only ever seed a handful of rows each. Bulk-inserts
 * directly through the repositories (bypassing the event-publishing path, which is already proven
 * by the per-story consumer tests) to reach 10,000+ rows across the three audit tables quickly,
 * then asserts a paginated {@code GET /api/v1/audit/logs} call still completes promptly rather
 * than timing out or degrading.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuditLogPaginationLoadTest {

    private static final int ROWS_PER_TABLE = 3_400; // > 10,000 combined across the three tables
    private static final long RESPONSIVE_BOUND_MILLIS = 2_000;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private LoginHistoryEntryRepository loginHistoryEntryRepository;

    @Autowired
    private ChangeHistoryEntryRepository changeHistoryEntryRepository;

    @Autowired
    private UserActivityEntryRepository userActivityEntryRepository;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void paginatedQueryStaysResponsiveAcrossTenThousandPlusRows() {
        seedBulkRows();

        userAdminService.createUser("Audit Load Tester", "9876600100", Set.of(Role.ADMIN), null, "correct-horse-5");
        String accessToken = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("9876600100", "correct-horse-5"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();

        long start = System.currentTimeMillis();
        client.get()
                .uri("/api/v1/audit/logs?page=50&size=25")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"totalElements\""));
        long elapsedMillis = System.currentTimeMillis() - start;

        assertThat(elapsedMillis)
                .as("GET /api/v1/audit/logs must stay responsive past 10,000 rows (SC-004)")
                .isLessThan(RESPONSIVE_BOUND_MILLIS);
    }

    private void seedBulkRows() {
        Instant base = Instant.parse("2020-01-01T00:00:00Z");

        List<LoginHistoryEntry> loginRows = new ArrayList<>(ROWS_PER_TABLE);
        List<ChangeHistoryEntry> changeRows = new ArrayList<>(ROWS_PER_TABLE);
        List<UserActivityEntry> activityRows = new ArrayList<>(ROWS_PER_TABLE);
        for (int i = 0; i < ROWS_PER_TABLE; i++) {
            Instant occurredAt = base.plusSeconds(i);
            UUID actor = UUID.randomUUID();
            loginRows.add(new LoginHistoryEntry(
                    occurredAt, UUID.randomUUID(), actor, "98XXXXX" + (i % 1000), "PASSWORD", "SIGN_IN_SUCCESS", "SUCCESS"));
            changeRows.add(new ChangeHistoryEntry(
                    occurredAt,
                    UUID.randomUUID(),
                    actor,
                    "PERMISSION_MATRIX",
                    "MANAGER.ATTENDANCE.EDIT",
                    "granted",
                    "false",
                    "true"));
            activityRows.add(
                    new UserActivityEntry(occurredAt, UUID.randomUUID(), actor, actor, "SESSION_ENDED", null));
        }
        loginHistoryEntryRepository.saveAll(loginRows);
        changeHistoryEntryRepository.saveAll(changeRows);
        userActivityEntryRepository.saveAll(activityRows);
    }
}
