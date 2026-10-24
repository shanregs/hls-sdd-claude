package com.hls.expenses.internal;

import com.hls.school.api.ChangeRecorder;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes the two trails of a claim change: the claim own append-only history (what the screen shows) and Change History
 * (the organization-wide audit, spec 003). Call inside the transaction of the change.
 */
@Component
class ClaimRecorder {

    static final String ENTITY = "EXPENSE_CLAIM";

    /** Changes made by the system itself (payroll settlement) are attributed to this id in Change History. */
    static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final ClaimEventRepository history;
    private final ChangeRecorder changes;
    private final Clock clock;

    ClaimRecorder(ClaimEventRepository history, ChangeRecorder changes, Clock clock) {
        this.history = history;
        this.changes = changes;
        this.clock = clock;
    }

    /** One history line and one Change History entry (a lifecycle entry named after the event). */
    void event(UUID claimId, UUID actor, String event, String reason, String detail) {
        history.save(new ClaimEvent(claimId, clock.instant(), actor, event, reason, detail));
        String note = reason != null ? reason : detail;
        changes.recordLifecycle(actor == null ? SYSTEM_ACTOR : actor, ENTITY, claimId, event.toLowerCase(), note);
    }

    /** One Change History entry for a field that moved, with its before and after values. */
    void field(UUID actor, UUID claimId, String field, Object before, Object after) {
        changes.record(actor == null ? SYSTEM_ACTOR : actor, ENTITY, claimId, field, before, after);
    }

    /** A history line with no Change History entry (the field changes carry that). */
    void historyOnly(UUID claimId, UUID actor, String event, String reason, String detail) {
        history.save(new ClaimEvent(claimId, clock.instant(), actor, event, reason, detail));
    }
}
