package com.hls.performance.internal;

import com.hls.performance.internal.PerformanceTypes.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** An observation or Principal feedback that belongs to a performance issue (the issue's supporting records). */
@Entity
@Table(name = "issue_link")
public class IssueLink {

    @Id
    UUID id;

    @Column(name = "issue_id", nullable = false)
    UUID issueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false)
    SourceType recordType;

    @Column(name = "record_root_id", nullable = false)
    UUID recordRootId;

    @Column(name = "linked_on", nullable = false)
    LocalDate linkedOn;

    protected IssueLink() {
        // JPA
    }

    IssueLink(UUID issueId, SourceType recordType, UUID recordRootId, LocalDate linkedOn) {
        this.id = UUID.randomUUID();
        this.issueId = issueId;
        this.recordType = recordType;
        this.recordRootId = recordRootId;
        this.linkedOn = linkedOn;
    }
}
