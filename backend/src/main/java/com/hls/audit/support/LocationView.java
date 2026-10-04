package com.hls.audit.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The location part of an audit row as returned to Admin and System (spec 018 contracts/mobile-api.md).
 * When {@code status} is not {@code AVAILABLE} the four values are null and the status is the reason.
 */
public record LocationView(
        String status, BigDecimal latitude, BigDecimal longitude, Float accuracyMeters, Instant capturedAt) {}
