package com.hls.status;

import java.time.OffsetDateTime;

/**
 * Transient result of a single status check (data-model.md: StatusCheckResult).
 * Never persisted; a fresh instance is built on every {@code GET /api/status} call.
 */
public record StatusResponse(
        Status status,
        String version,
        OffsetDateTime serverTime,
        boolean dataStoreReachable,
        long checkDurationMs) {

    public enum Status {
        OK,
        DEGRADED
    }

    /**
     * Derivation rule (data-model.md): status = OK if and only if dataStoreReachable = true;
     * otherwise DEGRADED.
     */
    public static StatusResponse of(String version, OffsetDateTime serverTime, boolean dataStoreReachable, long checkDurationMs) {
        Status status = dataStoreReachable ? Status.OK : Status.DEGRADED;
        return new StatusResponse(status, version, serverTime, dataStoreReachable, checkDurationMs);
    }
}
