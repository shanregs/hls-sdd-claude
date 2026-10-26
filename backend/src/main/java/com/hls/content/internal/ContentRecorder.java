package com.hls.content.internal;

import com.hls.school.api.ChangeRecorder;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes the two audit trails of a content change: its own append-only history (what the screen shows) and Change
 * History (the organization-wide audit). Call inside the transaction of the change.
 */
@Component
class ContentRecorder {

    private final ContentHistoryRepository history;
    private final ChangeRecorder changes;
    private final Clock clock;

    ContentRecorder(ContentHistoryRepository history, ChangeRecorder changes, Clock clock) {
        this.history = history;
        this.changes = changes;
        this.clock = clock;
    }

    /** One history line; also one Change History entry for the field when the value moved. */
    void change(Content content, String event, UUID actor, String field, Object before, Object after, String note) {
        history.save(new ContentHistory(
                content.getId(),
                event,
                actor,
                clock.instant(),
                before == null ? null : String.valueOf(before),
                after == null ? null : String.valueOf(after),
                note));
        changes.record(actor, "CONTENT", content.getId(), field, before, after);
    }

    void created(Content content, UUID actor, String detail) {
        history.save(new ContentHistory(content.getId(), "CREATED", actor, clock.instant(), null, content.getStatus().name(), detail));
        changes.recordLifecycle(actor, "CONTENT", content.getId(), "created", detail);
    }

    /** A line that is not a field change (a version, a decision, a file removal). */
    void note(Content content, String event, UUID actor, String entityType, Object entityId, String detail) {
        history.save(new ContentHistory(content.getId(), event, actor, clock.instant(), null, null, detail));
        changes.recordLifecycle(actor, entityType, entityId, event.toLowerCase(), detail);
    }
}
