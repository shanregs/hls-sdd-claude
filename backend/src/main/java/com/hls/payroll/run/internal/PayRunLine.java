package com.hls.payroll.run.internal;

import com.hls.payroll.api.PayRules.Kind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One person in a run, payable or not (spec 013b data-model.md). The frozen working is stored with the line, so an
 * approved line never needs the calendar, attendance, salaries or policy again (FR-016). A trigger refuses any change
 * once the run is approved.
 */
@Entity
@Table(name = "payroll_run_line")
public class PayRunLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_kind", nullable = false)
    private Kind personKind;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "person_name", nullable = false, length = 200)
    private String personName;

    @Column(name = "person_label", length = 200)
    private String personLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private PersonResult.State state;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "payable_days")
    private Integer payableDays;

    @Column(name = "working_days", precision = 6, scale = 2)
    private BigDecimal workingDays;

    @Column(name = "unpaid_days", precision = 6, scale = 2)
    private BigDecimal unpaidDays;

    @Column(name = "salary", precision = 14, scale = 2)
    private BigDecimal salary;

    @Column(name = "earned", precision = 14, scale = 2)
    private BigDecimal earned;

    @Column(name = "deduction", precision = 14, scale = 2)
    private BigDecimal deduction;

    @Column(name = "adjustments", precision = 14, scale = 2)
    private BigDecimal adjustments;

    @Column(name = "net", precision = 14, scale = 2)
    private BigDecimal net;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working")
    private String working;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "warning", length = 500)
    private String warning;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected PayRunLine() {
        // JPA
    }

    PayRunLine(UUID runId, PersonResult result, Instant now) {
        this.runId = runId;
        this.personKind = result.person().kind();
        this.personId = result.person().id();
        apply(result, now);
    }

    /** Replaces everything the computation decides, for a recompute of a draft. */
    final void apply(PersonResult result, Instant now) {
        this.personName = result.name();
        this.personLabel = result.label();
        this.state = result.state();
        this.reason = result.reason();
        this.payableDays = result.payableDays();
        this.workingDays = result.workingDays();
        this.unpaidDays = result.unpaidDays();
        this.salary = result.salary();
        this.earned = result.earned();
        this.deduction = result.deduction();
        this.adjustments = result.adjustments();
        this.net = result.net();
        this.working = result.workingJson();
        this.fingerprint = result.fingerprint();
        this.warning = result.warning();
        this.computedAt = now;
    }

    /** Takes a waiting adjustment out of a draft line without recomputing it (FR-020). */
    void removeAdjustment(BigDecimal amount, String workingJson, String newFingerprint) {
        this.adjustments = this.adjustments.subtract(amount);
        this.net = this.net.subtract(amount);
        this.working = workingJson;
        this.fingerprint = newFingerprint;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public Kind getPersonKind() {
        return personKind;
    }

    public UUID getPersonId() {
        return personId;
    }

    public String getPersonName() {
        return personName;
    }

    public String getPersonLabel() {
        return personLabel;
    }

    public PersonResult.State getState() {
        return state;
    }

    public String getReason() {
        return reason;
    }

    public Integer getPayableDays() {
        return payableDays;
    }

    public BigDecimal getWorkingDays() {
        return workingDays;
    }

    public BigDecimal getUnpaidDays() {
        return unpaidDays;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public BigDecimal getEarned() {
        return earned;
    }

    public BigDecimal getDeduction() {
        return deduction;
    }

    public BigDecimal getAdjustments() {
        return adjustments;
    }

    public BigDecimal getNet() {
        return net;
    }

    public String getWorking() {
        return working;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getWarning() {
        return warning;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
