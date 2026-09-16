package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.AssociationOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Maps to Django's existing hospital_user table (AUTH_USER_MODEL). Only the
 * columns this phase actually needs are mapped — see hospital/models.py's
 * User for the full column set (first/last name, email, is_staff, etc.),
 * which later phases can add as they're needed.
 *
 * <p>Unlike every other tenant entity, {@code hospital} is nullable — a
 * null-hospital user is a platform-operator account (superuser on the bare
 * BASE_DOMAIN). This overrides the NOT NULL join column inherited from
 * {@link TenantEntity}, exactly mirroring how the Django User model
 * redeclares {@code hospital} with {@code null=True} to override the
 * abstract TenantModel field.
 *
 * <p>{@code username} is deliberately not globally unique — see the
 * composite constraint below, mirroring Django's
 * {@code UniqueConstraint(["hospital", "username"])}: two hospitals can
 * each have a "doctor1"/"admin" login.
 */
@Entity
@Table(
        name = "hospital_user",
        uniqueConstraints = @UniqueConstraint(name = "unique_username_per_hospital", columnNames = {"hospital_id", "username"})
)
@AssociationOverride(name = "hospital", joinColumns = @JoinColumn(name = "hospital_id", nullable = true))
public class User extends TenantEntity {

    public enum Role {
        ADMIN, DOCTOR, NURSE, RECEPTIONIST, LAB, PHARMACIST, CASHIER, PATIENT, STOCK_MANAGER
    }

    @Column(nullable = false, length = 150)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected User() {
        // JPA
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}
