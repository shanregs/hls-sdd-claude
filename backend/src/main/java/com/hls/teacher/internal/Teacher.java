package com.hls.teacher.internal;

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
import java.time.LocalDate;
import java.util.UUID;

/** A person placed with Schools. Never deleted; an exited Teacher keeps their record. */
@Entity
@Table(name = "teacher")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "address")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TeacherStatus status;

    @Column(name = "status_effective_on", nullable = false)
    private LocalDate statusEffectiveOn;

    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Teacher() {
        // JPA
    }

    public Teacher(
            String name,
            String phone,
            String email,
            String address,
            TeacherStatus status,
            LocalDate statusEffectiveOn,
            Instant now) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.status = status;
        this.statusEffectiveOn = statusEffectiveOn;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public TeacherStatus getStatus() {
        return status;
    }

    public LocalDate getStatusEffectiveOn() {
        return statusEffectiveOn;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getVersion() {
        return version;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void moveTo(TeacherStatus next, LocalDate effectiveOn) {
        this.status = next;
        this.statusEffectiveOn = effectiveOn;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
