package com.hls.substitution.internal;

import com.hls.school.api.ChangeRecorder;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Publishes requirement changes to the shared Change History (spec 017a FR-014). The entity type is mapped to the VIEW
 * grant that may read it in {@code AuditVisibility}.
 */
@Component
public class SubstitutionAudit {

    public static final String REQUIREMENT = "SUBSTITUTION_REQUIREMENT";

    private final ChangeRecorder recorder;

    public SubstitutionAudit(ChangeRecorder recorder) {
        this.recorder = recorder;
    }

    public void created(UUID actor, SubstitutionRequirement r, String detail) {
        recorder.recordLifecycle(actor, REQUIREMENT, r.getId(), "created", detail);
    }

    public void status(UUID actor, UUID requirementId, RequirementStatus before, RequirementStatus after, String detail) {
        recorder.record(actor, REQUIREMENT, requirementId, "status", before, after + (detail == null ? "" : " (" + detail + ")"));
    }

    public void substitute(UUID actor, UUID requirementId, UUID before, UUID after) {
        recorder.record(actor, REQUIREMENT, requirementId, "substitute", before, after);
    }

    public void informed(UUID actor, UUID requirementId, String before, String after) {
        recorder.record(actor, REQUIREMENT, requirementId, "schoolInformed", before, after);
    }

    public void dayValue(UUID actor, UUID requirementId, Object before, Object after) {
        recorder.record(actor, REQUIREMENT, requirementId, "dayValue", before, after);
    }
}
