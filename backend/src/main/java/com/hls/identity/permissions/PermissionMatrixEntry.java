package com.hls.identity.permissions;

import com.hls.identity.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One grant of one action on one module to one role (data-model.md's "Permission Matrix Entry").
 * The only persisted entity this feature adds; {@code NavigationCatalog} (the set of possible
 * screens) is a code constant, not a table (research.md §5).
 */
@Entity
@Table(name = "permission_matrix")
public class PermissionMatrixEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false)
    private PermissionModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private PermissionAction action;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PermissionMatrixEntry() {
        // JPA
    }

    public PermissionMatrixEntry(
            Role role, PermissionModule module, PermissionAction action, boolean granted, Instant updatedAt) {
        this.role = role;
        this.module = module;
        this.action = action;
        this.granted = granted;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public PermissionModule getModule() {
        return module;
    }

    public PermissionAction getAction() {
        return action;
    }

    public boolean isGranted() {
        return granted;
    }

    public void setGranted(boolean granted) {
        this.granted = granted;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
