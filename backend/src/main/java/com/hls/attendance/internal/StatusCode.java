package com.hls.attendance.internal;

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
import java.util.UUID;

@Entity
@Table(name = "attendance_status_code")
public class StatusCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "short_code", nullable = false)
    private String shortCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private StatusCategory category;

    @Column(name = "weight", nullable = false)
    private BigDecimal weight;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "system", nullable = false)
    private boolean system;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected StatusCode() {
        // JPA
    }

    public StatusCode(String shortCode, String name, StatusCategory category, BigDecimal weight, int sortOrder) {
        this.shortCode = shortCode;
        this.name = name;
        this.category = category;
        this.weight = weight;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getName() {
        return name;
    }

    public StatusCategory getCategory() {
        return category;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSystem() {
        return system;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Long getVersion() {
        return version;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
