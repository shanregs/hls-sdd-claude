package com.hls.audit.support;

import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.clientcontext.LocationCapture;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Where an audited action came from (spec 018 data-model.md): the client (web or Android app), the
 * app version, and the device location or the reason there is none. Embedded in Login History, User
 * Activity and API Access entries so all three carry the same columns. Immutable once stored, like
 * the entries themselves (Constitution Principle I).
 *
 * <p>Latitude and longitude are kept to six decimal places (about 0.1 metre), the precision a later
 * heat map needs (FR-026a); the capture is never rounded further or masked.
 */
@Embeddable
public class ClientOrigin {

    @Column(name = "source", nullable = false)
    private String source = "WEB";

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "location_status", nullable = false)
    private String locationStatus = "NOT_APPLICABLE";

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy_meters")
    private Float accuracyMeters;

    @Column(name = "location_captured_at")
    private Instant locationCapturedAt;

    protected ClientOrigin() {
        // JPA
    }

    public static ClientOrigin from(ClientContext context) {
        ClientContext safe = context == null ? ClientContext.web() : context;
        ClientOrigin origin = new ClientOrigin();
        origin.source = safe.source().name();
        origin.appVersion = safe.appVersion();
        LocationCapture location = safe.location();
        origin.locationStatus = location.status().name();
        if (location.isAvailable()) {
            origin.latitude = BigDecimal.valueOf(location.latitude()).setScale(6, RoundingMode.HALF_UP);
            origin.longitude = BigDecimal.valueOf(location.longitude()).setScale(6, RoundingMode.HALF_UP);
            origin.accuracyMeters = location.accuracyMeters().floatValue();
            origin.locationCapturedAt = location.capturedAt();
        }
        return origin;
    }

    public String getSource() {
        return source;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getLocationStatus() {
        return locationStatus;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public Float getAccuracyMeters() {
        return accuracyMeters;
    }

    public Instant getLocationCapturedAt() {
        return locationCapturedAt;
    }

    public LocationView location() {
        return new LocationView(locationStatus, latitude, longitude, accuracyMeters, locationCapturedAt);
    }
}
