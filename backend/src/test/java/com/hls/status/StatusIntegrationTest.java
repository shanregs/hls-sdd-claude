package com.hls.status;

import com.github.dockerjava.api.DockerClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the real Degraded path (User Story 2) against a real Postgres via
 * Testcontainers, per the constitution's explicit Testcontainers mandate — not
 * just a mocked DataSource.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatusIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("hls")
            .withUsername("hls")
            .withPassword("hls");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void returnsOkWhenPostgresIsReachable() {
        ResponseEntity<StatusResponse> response = restTemplate.getForEntity(statusUrl(), StatusResponse.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(StatusResponse.Status.OK);
        assertThat(response.getBody().dataStoreReachable()).isTrue();
    }

    @Test
    void returnsDegradedWhenPostgresIsUnreachableThenRecoversToOkWithoutRestart() {
        // Acceptance Scenario 1 (User Story 2): make the data store unreachable mid-test.
        // Pausing (not stopping) the SAME container preserves its port mapping — calling
        // Testcontainers' own stop()/start() on a container recreates a brand-new
        // container with a different mapped port, which would silently break the
        // app's already-resolved connection properties instead of simulating recovery.
        DockerClient dockerClient = DockerClientFactory.instance().client();
        String containerId = POSTGRES.getContainerId();
        dockerClient.pauseContainerCmd(containerId).exec();
        try {
            ResponseEntity<StatusResponse> degraded = restTemplate.getForEntity(statusUrl(), StatusResponse.class);

            assertThat(degraded.getStatusCode().value()).isEqualTo(200); // FR-007: page still loads
            assertThat(degraded.getBody()).isNotNull();
            assertThat(degraded.getBody().status()).isEqualTo(StatusResponse.Status.DEGRADED);
            assertThat(degraded.getBody().dataStoreReachable()).isFalse();
        } finally {
            // Acceptance Scenario 2: unpause (same container/port) and confirm recovery
            // without an app restart.
            dockerClient.unpauseContainerCmd(containerId).exec();
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<StatusResponse> recovered = restTemplate.getForEntity(statusUrl(), StatusResponse.class);
            assertThat(recovered.getBody()).isNotNull();
            assertThat(recovered.getBody().status()).isEqualTo(StatusResponse.Status.OK);
        });
    }

    private String statusUrl() {
        return "http://localhost:" + port + "/api/status";
    }
}
