package com.hls.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusServiceTest {

    private static final int TIMEOUT_SECONDS = 3;
    private static final String VERSION = "0.1.0-warmup";

    private DataSource dataSource;
    private Connection connection;
    private StatusService statusService;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statusService = new StatusService(dataSource, VERSION, TIMEOUT_SECONDS, new SimpleMeterRegistry());
    }

    @Test
    void returnsOkWhenConnectionIsValid() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(TIMEOUT_SECONDS)).thenReturn(true);

        StatusResponse response = statusService.checkStatus();

        assertThat(response.status()).isEqualTo(StatusResponse.Status.OK);
        assertThat(response.dataStoreReachable()).isTrue();
        assertThat(response.version()).isEqualTo(VERSION);
        assertThat(response.serverTime()).isNotNull();
    }

    @Test
    void returnsDegradedWhenConnectionIsInvalid() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(TIMEOUT_SECONDS)).thenReturn(false);

        StatusResponse response = statusService.checkStatus();

        assertThat(response.status()).isEqualTo(StatusResponse.Status.DEGRADED);
        assertThat(response.dataStoreReachable()).isFalse();
    }

    @Test
    void returnsDegradedWhenGetConnectionThrows() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        StatusResponse response = statusService.checkStatus();

        assertThat(response.status()).isEqualTo(StatusResponse.Status.DEGRADED);
        assertThat(response.dataStoreReachable()).isFalse();
    }

    @Test
    void returnsDegradedWhenReachabilityCheckDoesNotCompleteWithinBound() throws SQLException {
        // Simulates a slow-but-eventually-failing driver call: isValid still applies
        // its own bounded wait (FR-006) and reports false once that wait is exhausted.
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenAnswer(invocation -> {
            Thread.sleep(50); // representative delay, well under the test's own budget
            return false;
        });

        StatusResponse response = statusService.checkStatus();

        assertThat(response.status()).isEqualTo(StatusResponse.Status.DEGRADED);
        assertThat(response.checkDurationMs()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void checkDurationStaysUnderThreeSecondBoundUnderNormalConditions() throws SQLException {
        // SC-004: the check completes within 3s under normal conditions.
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(TIMEOUT_SECONDS)).thenReturn(true);

        StatusResponse response = statusService.checkStatus();

        assertThat(response.checkDurationMs()).isLessThan(3000);
    }
}
