package com.hls.identity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A person who can sign in (spec 001-identity-access, data-model.md). The only sign-in identifier
 * is the normalized phone number; a Teacher-only user may have no password.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "phone", nullable = false, unique = true)
    private String phone;

    @Column(name = "username")
    private String username;

    @Column(name = "username_lower")
    private String usernameLower;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "linked_teacher_id")
    private UUID linkedTeacherId;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    @Column(name = "lock_until")
    private Instant lockUntil;

    protected AppUser() {
        // JPA
    }

    public AppUser(String displayName, String phone, String passwordHash, UUID linkedTeacherId) {
        this.displayName = displayName;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.linkedTeacherId = linkedTeacherId;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhone() {
        return phone;
    }

    public String getUsername() {
        return username;
    }

    /** Also maintains {@code usernameLower} for case-insensitive lookup (research.md §13). */
    public void setUsername(String username) {
        this.username = username;
        this.usernameLower = username != null ? username.toLowerCase(java.util.Locale.ROOT) : null;
    }

    public String getUsernameLower() {
        return usernameLower;
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

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public UUID getLinkedTeacherId() {
        return linkedTeacherId;
    }

    public int getFailedAttemptCount() {
        return failedAttemptCount;
    }

    public void setFailedAttemptCount(int failedAttemptCount) {
        this.failedAttemptCount = failedAttemptCount;
    }

    public Instant getLockUntil() {
        return lockUntil;
    }

    public void setLockUntil(Instant lockUntil) {
        this.lockUntil = lockUntil;
    }
}
