package com.hls.recruitment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One criterion score of one assessment of a candidate; insert-only, the highest assessment number is current. */
@Entity
@Table(name = "assessment_score")
public class AssessmentScore {

    public static final List<String> CRITERIA = List.of("SPEAKING", "ENGLISH", "COMMUNICATION");

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "assessment_no", nullable = false)
    private int assessmentNo;

    @Column(name = "criterion", nullable = false)
    private String criterion;

    @Column(name = "score", nullable = false)
    private short score;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "assessed_by", nullable = false)
    private UUID assessedBy;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    protected AssessmentScore() {}

    public AssessmentScore(
            UUID candidateId, int assessmentNo, String criterion, int score, String remarks, UUID assessedBy, Instant at) {
        this.id = UUID.randomUUID();
        this.candidateId = candidateId;
        this.assessmentNo = assessmentNo;
        this.criterion = criterion;
        this.score = (short) score;
        this.remarks = remarks;
        this.assessedBy = assessedBy;
        this.assessedAt = at;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public int getAssessmentNo() {
        return assessmentNo;
    }

    public String getCriterion() {
        return criterion;
    }

    public int getScore() {
        return score;
    }

    public String getRemarks() {
        return remarks;
    }

    public UUID getAssessedBy() {
        return assessedBy;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }
}
