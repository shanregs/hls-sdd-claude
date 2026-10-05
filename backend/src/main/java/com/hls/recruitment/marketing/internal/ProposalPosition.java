package com.hls.recruitment.marketing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One position of a different-salary proposal revision; insert-only. */
@Entity
@Table(name = "proposal_position")
public class ProposalPosition {

    @Id
    private UUID id;

    @Column(name = "revision_id", nullable = false)
    private UUID revisionId;

    @Column(name = "number", nullable = false)
    private int number;

    @Column(name = "title")
    private String title;

    @Column(name = "salary", nullable = false)
    private BigDecimal salary;

    protected ProposalPosition() {}

    public ProposalPosition(UUID revisionId, int number, String title, BigDecimal salary) {
        this.id = UUID.randomUUID();
        this.revisionId = revisionId;
        this.number = number;
        this.title = title;
        this.salary = salary;
    }

    public UUID getRevisionId() {
        return revisionId;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getSalary() {
        return salary;
    }
}
