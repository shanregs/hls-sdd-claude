package com.hls.identity.clientcontext;

import java.time.Instant;
import java.util.UUID;

/**
 * Published for every request made by the Android app (spec 018 FR-023a), for the audit module's API
 * Access trail. It carries facts about the request only: who (if signed in), when, the method, the
 * matched route pattern and the response status, plus the client context with its location. It
 * never carries a body, a header, a query string or an identifier taken from the path.
 */
public record ApiAccessRecorded(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        UUID sessionId,
        String httpMethod,
        String routeTemplate,
        int statusCode,
        ClientContext clientContext) {}
