package com.hls.audit.apiaccess;

import com.hls.audit.support.ClientOrigin;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable API Access entry (spec 018 FR-023a, data-model.md): one request made by the Android
 * app, with who, when, the method, the matched route pattern, the response status and where it came
 * from. It holds no request or response content, header, query string, or id from the path. Never
 * updated or deleted (Constitution Principle I).
 */
@Entity
@Table(name = "api_access_entry")
public class ApiAccessEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Column(name = "route_template", nullable = false)
    private String routeTemplate;

    @Column(name = "status_code", nullable = false)
    private short statusCode;

    @Embedded
    private ClientOrigin origin;

    protected ApiAccessEntry() {
        // JPA
    }

    public ApiAccessEntry(
            Instant occurredAt,
            UUID sourceEventId,
            UUID userId,
            UUID sessionId,
            String httpMethod,
            String routeTemplate,
            int statusCode,
            ClientOrigin origin) {
        this.occurredAt = occurredAt;
        this.sourceEventId = sourceEventId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.httpMethod = httpMethod;
        this.routeTemplate = routeTemplate;
        this.statusCode = (short) statusCode;
        this.origin = origin;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRouteTemplate() {
        return routeTemplate;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public ClientOrigin getOrigin() {
        return origin;
    }
}
