package com.hls.schoolbilling.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The MoU between HLS and one School for a period (spec 012). Terms are never edited: a change is a new
 * contract that ends this one. Only the end date, the cancellation and the one-time recording of the MoU on
 * a pending contract change a row. The responsible Manager is not stored; it is the School's Zone Manager.
 */
@Entity
@Table(name = "contract")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ContractState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_mode")
    private SalaryMode salaryMode;

    @Column(name = "teacher_count")
    private Integer teacherCount;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "signed_on")
    private LocalDate signedOn;

    @Column(name = "cycle", nullable = false)
    private String cycle = "MONTHLY";

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Contract() {
        // JPA
    }

    /** A pending contract that holds assignments made before an MoU is recorded. */
    public static Contract pending(UUID schoolId, LocalDate startsOn, UUID actor, Instant now) {
        Contract c = new Contract();
        c.schoolId = schoolId;
        c.state = ContractState.RATE_PENDING;
        c.startsOn = startsOn;
        c.createdBy = actor;
        c.createdAt = now;
        return c;
    }

    /** A signed, active contract. */
    public static Contract signed(
            UUID schoolId,
            SalaryMode mode,
            int teacherCount,
            BigDecimal rate,
            LocalDate signedOn,
            LocalDate startsOn,
            LocalDate endsOn,
            UUID actor,
            Instant now) {
        Contract c = new Contract();
        c.schoolId = schoolId;
        c.state = ContractState.ACTIVE;
        c.salaryMode = mode;
        c.teacherCount = teacherCount;
        c.rate = rate;
        c.signedOn = signedOn;
        c.startsOn = startsOn;
        c.endsOn = endsOn;
        c.createdBy = actor;
        c.createdAt = now;
        return c;
    }

    /** Records the MoU on a pending contract, once. */
    public void recordMou(SalaryMode mode, int teacherCount, BigDecimal rate, LocalDate signedOn) {
        this.state = ContractState.ACTIVE;
        this.salaryMode = mode;
        this.teacherCount = teacherCount;
        this.rate = rate;
        this.signedOn = signedOn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public ContractState getState() {
        return state;
    }

    public SalaryMode getSalaryMode() {
        return salaryMode;
    }

    public Integer getTeacherCount() {
        return teacherCount;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public LocalDate getSignedOn() {
        return signedOn;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public long getVersion() {
        return version;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setEndsOn(LocalDate endsOn) {
        this.endsOn = endsOn;
    }

    public void setState(ContractState state) {
        this.state = state;
    }

    public boolean isPending() {
        return state == ContractState.RATE_PENDING;
    }

    /** True when the contract is live (not cancelled) and its dates include {@code date}. */
    public boolean covers(LocalDate date) {
        return state != ContractState.CANCELLED
                && !startsOn.isAfter(date)
                && (endsOn == null || !endsOn.isBefore(date));
    }
}
