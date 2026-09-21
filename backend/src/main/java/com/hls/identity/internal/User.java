package com.hls.identity.internal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * FR-002/FR-017. {@code passwordHash} is null for users who only ever use OTP login
 * (pure Teachers). {@code linkedTeacherId}/{@code linkedManagerId} are opaque forward
 * references to modules that don't exist as tables yet (data-model.md).
 */
@Entity
@Table(name = "identity_user")
public class User {

    @Id
    private UUID id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "identity_user_role", joinColumns = @jakarta.persistence.JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "mfa_method")
    private MfaMethod mfaMethod;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "linked_teacher_id")
    private UUID linkedTeacherId;

    @Column(name = "linked_manager_id")
    private UUID linkedManagerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
        // JPA
    }

    public User(UUID id, String displayName, String phoneNumber, Set<Role> roles, Instant createdAt) {
        this.id = id;
        this.displayName = displayName;
        this.phoneNumber = phoneNumber;
        this.roles = new HashSet<>(roles);
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public MfaMethod getMfaMethod() {
        return mfaMethod;
    }

    public void setMfaMethod(MfaMethod mfaMethod) {
        this.mfaMethod = mfaMethod;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedAttemptCount() {
        return failedAttemptCount;
    }

    /** FR-010: increments on a failed login attempt. */
    public void incrementFailedAttempts() {
        this.failedAttemptCount++;
    }

    public void resetFailedAttempts() {
        this.failedAttemptCount = 0;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    /** FR-010/FR-015: a locked account must deny login identically to a wrong-password attempt. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public UUID getLinkedTeacherId() {
        return linkedTeacherId;
    }

    public void setLinkedTeacherId(UUID linkedTeacherId) {
        this.linkedTeacherId = linkedTeacherId;
    }

    public UUID getLinkedManagerId() {
        return linkedManagerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
