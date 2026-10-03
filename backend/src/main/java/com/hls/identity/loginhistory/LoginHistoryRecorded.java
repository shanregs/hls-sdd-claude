package com.hls.identity.loginhistory;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on every authentication outcome, for spec 003's Audit module to consume
 * (research.md §9 / spec 003's research.md §1) — {@code identity} does not depend on spec 003 and,
 * as of spec 003, keeps no login-history copy of its own (Constitution Principle VII).
 */
public record LoginHistoryRecorded(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        String phoneMasked,
        LoginMethod method,
        LoginEventType eventType,
        String outcome) {}
