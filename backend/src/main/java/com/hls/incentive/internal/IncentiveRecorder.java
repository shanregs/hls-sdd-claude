package com.hls.incentive.internal;

import com.hls.school.api.ChangeRecorder;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes the two audit records of every incentive change: a row of the append-only {@code incentive_event} history and a
 * Change History entry (Constitution Principle I). A null actor is the system (the job or a listener).
 */
@Component
class IncentiveRecorder {

    static final String ENTITY = "INCENTIVE";
    static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(BUSINESS_ZONE);

    private final IncentiveEventRepository events;
    private final ChangeRecorder changes;
    private final Clock clock;

    IncentiveRecorder(IncentiveEventRepository events, ChangeRecorder changes, Clock clock) {
        this.events = events;
        this.changes = changes;
        this.clock = clock;
    }

    /** One history row and one Change History entry (field is the lower-case kind). */
    void record(UUID actor, UUID incentiveId, String kind, String detail) {
        String shortened = detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail);
        events.save(new IncentiveEvent(incentiveId, kind, actor, shortened, clock.instant()));
        changes.recordLifecycle(actor == null ? SYSTEM_ACTOR : actor, ENTITY, incentiveId, kind.toLowerCase(), shortened);
    }

    /** A change of one field, with its old and new values, beside the history row. */
    void field(UUID actor, UUID incentiveId, String field, Object before, Object after) {
        changes.record(actor == null ? SYSTEM_ACTOR : actor, ENTITY, incentiveId, field, before, after);
    }
}
