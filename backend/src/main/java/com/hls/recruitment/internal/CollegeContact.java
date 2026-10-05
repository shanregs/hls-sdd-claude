package com.hls.recruitment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/** The placement officer or the principal of a college: one row per college and role. */
@Entity
@Table(name = "college_contact")
@IdClass(CollegeContact.Key.class)
public class CollegeContact {

    public static final String PLACEMENT_OFFICER = "PLACEMENT_OFFICER";
    public static final String PRINCIPAL = "PRINCIPAL";

    public record Key(UUID collegeId, String role) implements Serializable {}

    @Id
    @Column(name = "college_id")
    private UUID collegeId;

    @Id
    @Column(name = "role")
    private String role;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    protected CollegeContact() {}

    public CollegeContact(UUID collegeId, String role, String name, String phone, String email) {
        this.collegeId = collegeId;
        this.role = role;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public UUID getCollegeId() {
        return collegeId;
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
