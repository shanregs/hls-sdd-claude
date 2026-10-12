package com.hls.attendance.manager.internal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One Manager's record for one date (spec 032): the check-in, the check-out and, when leave is approved, the leave on
 * that date. At most one per Manager per date. Changes are kept in {@link ManagerDayHistory}.
 */
@Entity
@Table(name = "manager_day")
public class ManagerDay {

    /** What kind of day the Manager worked. A Leave day is never chosen at check-in; it comes from approved leave. */
    public enum DayType {
        OFFICE,
        FIELD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type")
    private DayType dayType;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "unlinked", nullable = false)
    private boolean unlinked;

    @Column(name = "check_in_at")
    private Instant checkInAt;

    @Column(name = "check_in_lat", precision = 9, scale = 6)
    private BigDecimal checkInLat;

    @Column(name = "check_in_lng", precision = 9, scale = 6)
    private BigDecimal checkInLng;

    @Column(name = "check_in_accuracy_m", precision = 8, scale = 2)
    private BigDecimal checkInAccuracyM;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "check_out_lat", precision = 9, scale = 6)
    private BigDecimal checkOutLat;

    @Column(name = "check_out_lng", precision = 9, scale = 6)
    private BigDecimal checkOutLng;

    @Column(name = "check_out_accuracy_m", precision = 8, scale = 2)
    private BigDecimal checkOutAccuracyM;

    @Column(name = "photo_file_id")
    private UUID photoFileId;

    @Column(name = "late", nullable = false)
    private boolean late;

    @Column(name = "added_later", nullable = false)
    private boolean addedLater;

    @Column(name = "corrected", nullable = false)
    private boolean corrected;

    @Column(name = "leave_request_id")
    private UUID leaveRequestId;

    @Column(name = "leave_fraction", precision = 2, scale = 1)
    private BigDecimal leaveFraction;

    @Column(name = "leave_unpaid", nullable = false)
    private boolean leaveUnpaid;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "manager_day_activity", joinColumns = @JoinColumn(name = "day_id"))
    private Set<ActivityLink> activities = new HashSet<>();

    protected ManagerDay() {
        // JPA
    }

    public ManagerDay(UUID managerId, LocalDate dayDate) {
        this.managerId = managerId;
        this.dayDate = dayDate;
    }

    // ------------------------------------------------------------------ state changes

    public void checkIn(DayType type, String purpose, boolean unlinked, Instant at, Location where, boolean late) {
        this.dayType = type;
        this.purpose = purpose;
        this.unlinked = unlinked;
        this.checkInAt = at;
        this.checkInLat = where.latitude();
        this.checkInLng = where.longitude();
        this.checkInAccuracyM = where.accuracyMeters();
        this.late = late;
    }

    public void checkOut(Instant at, Location where) {
        this.checkOutAt = at;
        this.checkOutLat = where == null ? null : where.latitude();
        this.checkOutLng = where == null ? null : where.longitude();
        this.checkOutAccuracyM = where == null ? null : where.accuracyMeters();
    }

    public void clearAttendance() {
        this.dayType = null;
        this.purpose = null;
        this.unlinked = false;
        this.checkInAt = null;
        this.checkInLat = null;
        this.checkInLng = null;
        this.checkInAccuracyM = null;
        this.checkOutAt = null;
        this.checkOutLat = null;
        this.checkOutLng = null;
        this.checkOutAccuracyM = null;
        this.late = false;
        this.activities.clear();
    }

    public void setPurpose(String purpose, boolean unlinked) {
        this.purpose = purpose;
        this.unlinked = unlinked;
    }

    public void setDayType(DayType dayType) {
        this.dayType = dayType;
    }

    public void setCheckInAt(Instant at, boolean late) {
        this.checkInAt = at;
        this.late = late;
    }

    public void markAddedLater() {
        this.addedLater = true;
    }

    public void markCorrected() {
        this.corrected = true;
    }

    public void setPhotoFileId(UUID photoFileId) {
        this.photoFileId = photoFileId;
    }

    public void applyLeave(UUID requestId, BigDecimal fraction, boolean unpaid) {
        this.leaveRequestId = requestId;
        this.leaveFraction = fraction;
        this.leaveUnpaid = unpaid;
    }

    public void clearLeave() {
        this.leaveRequestId = null;
        this.leaveFraction = null;
        this.leaveUnpaid = false;
    }

    public void replaceActivities(Set<ActivityLink> links) {
        this.activities.clear();
        this.activities.addAll(links);
    }

    // ------------------------------------------------------------------ reads

    public boolean hasCheckIn() {
        return checkInAt != null;
    }

    public boolean hasLeave() {
        return leaveRequestId != null;
    }

    public boolean hasFullLeave() {
        return leaveFraction != null && leaveFraction.compareTo(BigDecimal.ONE) >= 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public LocalDate getDayDate() {
        return dayDate;
    }

    public DayType getDayType() {
        return dayType;
    }

    public String getPurpose() {
        return purpose;
    }

    public boolean isUnlinked() {
        return unlinked;
    }

    public Instant getCheckInAt() {
        return checkInAt;
    }

    public BigDecimal getCheckInLat() {
        return checkInLat;
    }

    public BigDecimal getCheckInLng() {
        return checkInLng;
    }

    public BigDecimal getCheckInAccuracyM() {
        return checkInAccuracyM;
    }

    public Instant getCheckOutAt() {
        return checkOutAt;
    }

    public BigDecimal getCheckOutLat() {
        return checkOutLat;
    }

    public BigDecimal getCheckOutLng() {
        return checkOutLng;
    }

    public BigDecimal getCheckOutAccuracyM() {
        return checkOutAccuracyM;
    }

    public UUID getPhotoFileId() {
        return photoFileId;
    }

    public boolean isLate() {
        return late;
    }

    public boolean isAddedLater() {
        return addedLater;
    }

    public boolean isCorrected() {
        return corrected;
    }

    public UUID getLeaveRequestId() {
        return leaveRequestId;
    }

    public BigDecimal getLeaveFraction() {
        return leaveFraction;
    }

    public boolean isLeaveUnpaid() {
        return leaveUnpaid;
    }

    public Long getVersion() {
        return version;
    }

    public Set<ActivityLink> getActivities() {
        return activities;
    }

    /** A device location: latitude -90 to 90, longitude -180 to 180, an optional accuracy in metres. */
    public record Location(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {}
}
