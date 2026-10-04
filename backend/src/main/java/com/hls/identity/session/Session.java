package com.hls.identity.session;

import com.hls.identity.clientcontext.ClientSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One signed-in device for a user (data-model.md's "Session"). */
@Entity
@Table(name = "session")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_description")
    private String deviceDescription;

    @Column(name = "signed_in_at", nullable = false)
    private Instant signedInAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    /** Which client signed in (spec 018 FR-007): the web app or the Android app. */
    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false)
    private ClientSource clientType = ClientSource.WEB;

    @Column(name = "app_version")
    private String appVersion;

    protected Session() {
        // JPA
    }

    public Session(UUID userId, String deviceDescription, Instant now) {
        this(userId, deviceDescription, now, ClientSource.WEB, null);
    }

    public Session(UUID userId, String deviceDescription, Instant now, ClientSource clientType, String appVersion) {
        this.clientType = clientType;
        this.appVersion = appVersion;
        this.userId = userId;
        this.deviceDescription = deviceDescription;
        this.signedInAt = now;
        this.lastActivityAt = now;
        this.status = SessionStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDeviceDescription() {
        return deviceDescription;
    }

    public Instant getSignedInAt() {
        return signedInAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public ClientSource getClientType() {
        return clientType;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void touch(Instant now) {
        this.lastActivityAt = now;
    }

    public void end() {
        this.status = SessionStatus.ENDED;
    }

    public void revoke() {
        this.status = SessionStatus.REVOKED;
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }
}
