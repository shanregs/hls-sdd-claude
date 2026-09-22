package com.hls.organization.api;

/** FR-002: raised when a {@code zoneId} does not correspond to any existing Zone (validated live through {@code school.api.ZoneQueries}). */
public class ZoneNotFoundException extends RuntimeException {

    public ZoneNotFoundException(String message) {
        super(message);
    }
}
