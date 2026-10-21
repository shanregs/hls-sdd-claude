package com.hls.performance.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** One round of correction on a performance issue: what was discussed, what must change, and the follow-up date. */
@Entity
@Table(name = "corrective_action")
public class CorrectiveAction {

    @Id
    UUID id;

    @Column(name = "issue_id", nullable = false)
    UUID issueId;

    @Column(name = "round_no", nullable = false)
    int roundNo;

    @Column(name = "discussed", nullable = false)
    String discussed;

    @Column(name = "required_change", nullable = false)
    String requiredChange;

    @Column(name = "follow_up_on", nullable = false)
    LocalDate followUpOn;

    @Column(name = "recorded_by", nullable = false)
    UUID recordedBy;

    @Column(name = "recorded_on", nullable = false)
    LocalDate recordedOn;

    @Column(name = "follow_up_source_key", nullable = false)
    String followUpSourceKey;

    @Column(name = "follow_up_observation_root_id")
    UUID followUpObservationRootId;

    protected CorrectiveAction() {
        // JPA
    }

    CorrectiveAction(UUID issueId, int roundNo, String discussed, String requiredChange, LocalDate followUpOn, UUID recordedBy,
            LocalDate recordedOn, String followUpSourceKey) {
        this.id = UUID.randomUUID();
        this.issueId = issueId;
        this.roundNo = roundNo;
        this.discussed = discussed;
        this.requiredChange = requiredChange;
        this.followUpOn = followUpOn;
        this.recordedBy = recordedBy;
        this.recordedOn = recordedOn;
        this.followUpSourceKey = followUpSourceKey;
    }
}
