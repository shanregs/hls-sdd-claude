package com.hls.identity.clientcontext;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns the {@code X-HLS-*} request headers into a {@link ClientContext} (spec 018
 * contracts/mobile-api.md, research.md §5, §8). It never throws and never rejects a request: a
 * malformed or implausible location becomes {@code INVALID} with the submitted values discarded
 * (FR-024), because location must never get in the way of signing in or working (FR-021, FR-025).
 */
@Component
public class ClientContextParser {

    public static final String CLIENT_HEADER = "X-HLS-Client";
    public static final String LOCATION_HEADER = "X-HLS-Location";
    public static final String LOCATION_STATUS_HEADER = "X-HLS-Location-Status";
    public static final String DEVICE_INTEGRITY_HEADER = "X-HLS-Device-Integrity";
    public static final String ROOTED_SUSPECTED = "ROOTED_SUSPECTED";

    static final double MAX_ACCURACY_METERS = 5_000;
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private static final Pattern CLIENT_PATTERN = Pattern.compile("^android/(\\d{1,4}\\.\\d{1,4}\\.\\d{1,4})$");

    private final Clock clock;

    public ClientContextParser(Clock clock) {
        this.clock = clock;
    }

    public ClientContext parse(HttpServletRequest request) {
        return parse(
                request.getHeader(CLIENT_HEADER),
                request.getHeader(LOCATION_HEADER),
                request.getHeader(LOCATION_STATUS_HEADER),
                request.getHeader(DEVICE_INTEGRITY_HEADER));
    }

    public ClientContext parse(String clientHeader, String locationHeader, String statusHeader, String integrityHeader) {
        if (clientHeader == null) {
            return ClientContext.web();
        }
        Matcher client = CLIENT_PATTERN.matcher(clientHeader.trim().toLowerCase(Locale.ROOT));
        if (!client.matches()) {
            // A malformed client header is treated as an ordinary web request, never an error.
            return ClientContext.web();
        }
        String appVersion = client.group(1);
        boolean rooted = ROOTED_SUSPECTED.equals(integrityHeader == null ? null : integrityHeader.trim());
        return new ClientContext(ClientSource.ANDROID, appVersion, parseLocation(locationHeader, statusHeader), rooted);
    }

    private LocationCapture parseLocation(String locationHeader, String statusHeader) {
        if (locationHeader != null && !locationHeader.isBlank()) {
            return parseCoordinates(locationHeader);
        }
        if (statusHeader != null && !statusHeader.isBlank()) {
            return LocationCapture.unavailable(reasonFrom(statusHeader.trim()));
        }
        return LocationCapture.unavailable(LocationStatus.OTHER);
    }

    private static LocationStatus reasonFrom(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "PERMISSION_DENIED" -> LocationStatus.PERMISSION_DENIED;
            case "SERVICES_OFF" -> LocationStatus.SERVICES_OFF;
            case "NO_FIX" -> LocationStatus.NO_FIX;
            default -> LocationStatus.OTHER;
        };
    }

    private LocationCapture parseCoordinates(String header) {
        Map<String, String> parts = new HashMap<>();
        for (String pair : header.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                return invalid();
            }
            parts.put(pair.substring(0, eq).trim().toLowerCase(Locale.ROOT), pair.substring(eq + 1).trim());
        }
        try {
            double lat = parseFinite(parts.get("lat"));
            double lng = parseFinite(parts.get("lng"));
            double acc = parseFinite(parts.get("acc"));
            long tsMillis = Long.parseLong(parts.get("ts"));
            Instant capturedAt = Instant.ofEpochMilli(tsMillis);
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                return invalid();
            }
            if (acc < 0 || acc > MAX_ACCURACY_METERS) {
                return invalid();
            }
            if (Duration.between(capturedAt, clock.instant()).abs().compareTo(MAX_CLOCK_SKEW) > 0) {
                return invalid();
            }
            return LocationCapture.available(lat, lng, acc, capturedAt);
        } catch (RuntimeException e) {
            // Missing key (NullPointerException), bad number (NumberFormatException), or an
            // out-of-range timestamp (DateTimeException): all just "invalid", never an error.
            return invalid();
        }
    }

    private static double parseFinite(String value) {
        double parsed = Double.parseDouble(value);
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
            throw new NumberFormatException("not finite");
        }
        return parsed;
    }

    private static LocationCapture invalid() {
        return LocationCapture.unavailable(LocationStatus.INVALID);
    }
}
