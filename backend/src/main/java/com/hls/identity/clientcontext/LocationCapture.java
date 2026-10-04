package com.hls.identity.clientcontext;

import java.time.Instant;

/**
 * The device position sent with one request, or the reason there is none (spec 018 FR-020, FR-021,
 * FR-024). Invariant: the four values are present exactly when {@code status} is {@code AVAILABLE}.
 */
public record LocationCapture(
        LocationStatus status, Double latitude, Double longitude, Double accuracyMeters, Instant capturedAt) {

    public LocationCapture {
        boolean available = status == LocationStatus.AVAILABLE;
        boolean allPresent = latitude != null && longitude != null && accuracyMeters != null && capturedAt != null;
        boolean nonePresent = latitude == null && longitude == null && accuracyMeters == null && capturedAt == null;
        if (available && !allPresent) {
            throw new IllegalArgumentException("AVAILABLE location needs latitude, longitude, accuracy and time");
        }
        if (!available && !nonePresent) {
            throw new IllegalArgumentException("A location with status " + status + " must carry no coordinates");
        }
    }

    public static LocationCapture available(double latitude, double longitude, double accuracyMeters, Instant at) {
        return new LocationCapture(LocationStatus.AVAILABLE, latitude, longitude, accuracyMeters, at);
    }

    public static LocationCapture unavailable(LocationStatus status) {
        if (status == LocationStatus.AVAILABLE) {
            throw new IllegalArgumentException("Use available(...) for an AVAILABLE location");
        }
        return new LocationCapture(status, null, null, null, null);
    }

    public boolean isAvailable() {
        return status == LocationStatus.AVAILABLE;
    }
}
