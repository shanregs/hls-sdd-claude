package com.hls.audit.api;

import java.util.UUID;

/**
 * The audit detail text every module writes for a same-day record (spec 040 FR-014), so the Change History search finds
 * all of them with one phrase. Pure; it only builds the text.
 */
public final class SameDayDetail {

    public static final String MARK = "sameDay=true";

    private SameDayDetail() {}

    /** For example {@code sameDay=true; recordedBy=u1; for=t1; auto-accepted}; {@code forId} may be null (own work). */
    public static String of(UUID recordedBy, UUID forId) {
        return MARK + "; recordedBy=" + recordedBy + "; for=" + (forId == null ? recordedBy : forId) + "; auto-accepted";
    }
}
