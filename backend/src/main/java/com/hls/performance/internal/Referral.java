package com.hls.performance.internal;

import com.hls.performance.internal.PerformanceTypes.Outcome;
import com.hls.performance.internal.PerformanceTypes.Recommendation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** The recommendation to transfer or replace a Teacher; the decision itself is made in spec 027. */
@Entity
@Table(name = "referral")
public class Referral {

    @Id
    UUID id;

    @Column(name = "issue_id", nullable = false)
    UUID issueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", nullable = false)
    Recommendation recommendation;

    @Column(name = "reason", nullable = false)
    String reason;

    @Column(name = "referred_by", nullable = false)
    UUID referredBy;

    @Column(name = "referred_on", nullable = false)
    LocalDate referredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_outcome")
    Outcome decisionOutcome;

    @Column(name = "decision_reported_on")
    LocalDate decisionReportedOn;

    protected Referral() {
        // JPA
    }

    Referral(UUID issueId, Recommendation recommendation, String reason, UUID referredBy, LocalDate referredOn) {
        this.id = UUID.randomUUID();
        this.issueId = issueId;
        this.recommendation = recommendation;
        this.reason = reason;
        this.referredBy = referredBy;
        this.referredOn = referredOn;
    }
}
