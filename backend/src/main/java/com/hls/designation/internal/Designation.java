package com.hls.designation.internal;

import com.hls.designation.api.DesignationDirectory.Kind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A named job title for Teachers or Managers. Never deleted; the kind is fixed once a person has held it. */
@Entity
@Table(name = "designation")
public class Designation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private Kind kind;

    @Column(name = "retired", nullable = false)
    private boolean retired;

    @Column(name = "held_ever", nullable = false)
    private boolean heldEver;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Designation() {
        // JPA
    }

    public Designation(String name, Kind kind, UUID createdBy, Instant now) {
        this.name = name;
        this.kind = kind;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isRetired() {
        return retired;
    }

    public boolean isHeldEver() {
        return heldEver;
    }

    public Long getVersion() {
        return version;
    }

    public void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    public void setKind(Kind kind, Instant now) {
        this.kind = kind;
        this.updatedAt = now;
    }

    public void setRetired(boolean retired, Instant now) {
        this.retired = retired;
        this.updatedAt = now;
    }
}
