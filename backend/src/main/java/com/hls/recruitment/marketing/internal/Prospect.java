package com.hls.recruitment.marketing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A School HLS is trying to win. A won prospect has {@code wonAt} set and, later, the School that was created. */
@Entity
@Table(name = "marketing_prospect")
public class Prospect {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "board")
    private String board;

    @Column(name = "address")
    private String address;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "name_key", nullable = false)
    private String nameKey;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(name = "designation")
    private String designation;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "expected_teachers")
    private Integer expectedTeachers;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private ProspectStage stage = ProspectStage.PROSPECT;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_before_hold")
    private ProspectStage stageBeforeHold;

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(name = "won_at")
    private Instant wonAt;

    @Column(name = "won_by")
    private UUID wonBy;

    @Column(name = "place_id")
    private UUID placeId;

    @Column(name = "school_id")
    private UUID schoolId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Prospect() {}

    public Prospect(
            String name,
            String board,
            String address,
            UUID zoneId,
            String nameKey,
            String contactPerson,
            String designation,
            String phone,
            String email,
            Integer expectedTeachers,
            UUID ownerUserId,
            UUID createdBy,
            Instant now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.board = board;
        this.address = address;
        this.zoneId = zoneId;
        this.nameKey = nameKey;
        this.contactPerson = contactPerson;
        this.designation = designation;
        this.phone = phone;
        this.email = email;
        this.expectedTeachers = expectedTeachers;
        this.ownerUserId = ownerUserId;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBoard() {
        return board;
    }

    public String getAddress() {
        return address;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getDesignation() {
        return designation;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public Integer getExpectedTeachers() {
        return expectedTeachers;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public ProspectStage getStage() {
        return stage;
    }

    public ProspectStage getStageBeforeHold() {
        return stageBeforeHold;
    }

    public String getLostReason() {
        return lostReason;
    }

    public Instant getWonAt() {
        return wonAt;
    }

    public UUID getWonBy() {
        return wonBy;
    }

    public UUID getPlaceId() {
        return placeId;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public Long getVersion() {
        return version;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public boolean isWon() {
        return wonAt != null;
    }

    public void change(
            String name,
            String board,
            String address,
            String nameKey,
            String contactPerson,
            String designation,
            String phone,
            String email,
            Integer expectedTeachers) {
        this.name = name;
        this.board = board;
        this.address = address;
        this.nameKey = nameKey;
        this.contactPerson = contactPerson;
        this.designation = designation;
        this.phone = phone;
        this.email = email;
        this.expectedTeachers = expectedTeachers;
    }

    public void assignTo(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    /** Moves between the active stages. */
    public void moveTo(ProspectStage next) {
        this.stage = next;
        this.stageBeforeHold = null;
        this.lostReason = null;
    }

    public void hold() {
        this.stageBeforeHold = this.stage;
        this.stage = ProspectStage.ON_HOLD;
    }

    public void lose(String reason) {
        if (stage.isActive()) {
            this.stageBeforeHold = this.stage;
        }
        this.stage = ProspectStage.LOST;
        this.lostReason = reason;
    }

    /** Resumes from hold or reopens from lost, to the stage the prospect left. */
    public void returnToPrevious() {
        this.stage = stageBeforeHold == null ? ProspectStage.PROSPECT : stageBeforeHold;
        this.stageBeforeHold = null;
        this.lostReason = null;
    }

    public void win(UUID by, Instant at) {
        this.wonBy = by;
        this.wonAt = at;
    }

    public void linkSchool(UUID placeId, UUID schoolId) {
        this.placeId = placeId;
        this.schoolId = schoolId;
    }
}
