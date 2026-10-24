package com.hls.schoolbilling.internal;

import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes the approval history rows and the Change History entries of spec 031 together. */
@Component
class MouRecorder {

    private final MouApprovalEventRepository events;
    private final ChangeRecorder changes;
    private final Clock clock;

    MouRecorder(MouApprovalEventRepository events, ChangeRecorder changes, Clock clock) {
        this.events = events;
        this.changes = changes;
        this.clock = clock;
    }

    void event(
            MouApproval approval,
            String action,
            MouApprovalStatus from,
            MouApprovalStatus to,
            UUID actor,
            Set<Role> roles,
            Integer documentVersion,
            String note,
            String detail) {
        events.save(new MouApprovalEvent(
                approval.getContractId(),
                approval.getRound(),
                action,
                from == null ? null : from.name(),
                to == null ? null : to.name(),
                actor,
                MouAccess.actorRole(roles),
                documentVersion,
                note,
                detail,
                clock.instant()));
    }

    /** One Change History entry for the approval of {@code contractId}. */
    void approvalChanged(UUID actor, UUID contractId, String field, Object before, Object after) {
        changes.record(actor, "MOU_APPROVAL", contractId, field, before, after);
    }

    void documentChanged(UUID actor, UUID contractId, String field, Object detail) {
        changes.recordLifecycle(actor, "MOU_DOCUMENT", contractId, field, detail);
    }

    void contractChanged(UUID actor, UUID contractId, String field, Object before, Object after) {
        changes.record(actor, "CONTRACT", contractId, field, before, after);
    }
}
