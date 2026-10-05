package com.hls.school.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** The principal or accountant of a School: one row per School and role. */
@Entity
@Table(name = "school_contact")
@IdClass(SchoolContact.Key.class)
public class SchoolContact {

    public record Key(UUID schoolId, String role) implements Serializable {}

    @Id
    @Column(name = "school_id")
    private UUID schoolId;

    @Id
    @Column(name = "role")
    private String role;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    protected SchoolContact() {}

    public SchoolContact(UUID schoolId, String role, String name, String phone, String email) {
        this.schoolId = Objects.requireNonNull(schoolId);
        this.role = Objects.requireNonNull(role);
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getRole() {
        return role;
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

    public void change(String name, String phone, String email) {
        this.name = name;
        this.phone = phone;
        this.email = email;
    }
}
