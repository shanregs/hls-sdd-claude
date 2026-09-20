package com.hls.status;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Computes the current {@link StatusResponse} on demand — never cached (SC-002) —
 * by checking core data store reachability via {@link Connection#isValid(int)},
 * which applies the bounded wait itself (research.md §1, §3: 3-second timeout).
 */
@Service
public class StatusService {

    private static final Logger log = LoggerFactory.getLogger(StatusService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DataSource dataSource;
    private final String version;
    private final int timeoutSeconds;
    private final MeterRegistry meterRegistry;

    public StatusService(
            DataSource dataSource,
            @Value("${hls.app.version}") String version,
            @Value("${status.check.timeout-seconds:3}") int timeoutSeconds,
            MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.version = version;
        this.timeoutSeconds = timeoutSeconds;
        this.meterRegistry = meterRegistry;
    }

    public StatusResponse checkStatus() {
        long startNanos = System.nanoTime();
        boolean dataStoreReachable = isDataStoreReachable();
        long checkDurationMs = (System.nanoTime() - startNanos) / 1_000_000;

        StatusResponse response = StatusResponse.of(version, OffsetDateTime.now(IST), dataStoreReachable, checkDurationMs);
        meterRegistry.counter("status.check.total", "result", response.status().name()).increment();
        return response;
    }

    private boolean isDataStoreReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(timeoutSeconds);
        } catch (SQLException e) {
            log.warn("Core data store reachability check failed: {}", e.getMessage());
            return false;
        }
    }
}
