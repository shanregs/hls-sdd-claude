package com.hls.organization.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** A dated, never-overwritten Zone-Manager link; current while {@code endsOn} is null. */
@Entity
@Table(name = "zone_manager_assignment")
public class ZoneManagerAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    protected ZoneManagerAssignment() {
        // JPA
    }

    public ZoneManagerAssignment(UUID zoneId, UUID managerId, LocalDate startsOn) {
        this.zoneId = zoneId;
        this.managerId = managerId;
        this.startsOn = startsOn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public void end(LocalDate on) {
        this.endsOn = on;
    }
}
