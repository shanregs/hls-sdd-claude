package com.hls.performance.internal;

import com.hls.school.api.ChangeRecorder;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes the two audit trails of a performance change: the module's own append-only history (what the Teacher page shows)
 * and Change History (the organization-wide audit). Call inside the transaction of the change.
 */
@Component
class PerformanceRecorder {

    /** Changes made by the system itself (the daily check, other modules) are attributed to this id in Change History. */
    static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    static final String OBSERVATION = "OBSERVATION";
    static final String FEEDBACK = "PRINCIPAL_FEEDBACK";
    static final String ISSUE = "PERFORMANCE_ISSUE";
    static final String PLAN = "OBSERVATION_PLAN";

    private final HistoryRepository history;
    private final ChangeRecorder changes;
    private final TeacherFacts facts;
    private final Clock clock;

    PerformanceRecorder(HistoryRepository history, ChangeRecorder changes, TeacherFacts facts, Clock clock) {
        this.history = history;
        this.changes = changes;
        this.facts = facts;
        this.clock = clock;
    }

    /** A history line with before and after values; also a Change History entry for {@code field} when the value moved. */
    void change(String recordType, UUID recordId, UUID teacherId, String event, UUID actor, String field, Object before, Object after, String note) {
        history.save(line(recordType, recordId, teacherId, event, actor, before, after, note));
        changes.record(actor == null ? SYSTEM_ACTOR : actor, recordType, recordId, field, before, after);
    }

    /** A creation or other event with no single field: one history line and one Change History lifecycle entry. */
    void event(String recordType, UUID recordId, UUID teacherId, String event, UUID actor, String detail) {
        history.save(line(recordType, recordId, teacherId, event, actor, null, null, detail));
        changes.recordLifecycle(actor == null ? SYSTEM_ACTOR : actor, recordType, recordId, event.toLowerCase(), detail);
    }

    private PerformanceHistory line(String recordType, UUID recordId, UUID teacherId, String event, UUID actor, Object before, Object after, String note) {
        return new PerformanceHistory(
                recordType,
                recordId,
                teacherId,
                event,
                actor,
                actor == null ? "System" : facts.userName(actor),
                clock.instant(),
                before == null ? null : String.valueOf(before),
                after == null ? null : String.valueOf(after),
                note);
    }
}
