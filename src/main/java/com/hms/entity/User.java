package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.AssociationOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

/**
 * Maps to Django's existing hospital_user table (AUTH_USER_MODEL). Every
 * phase before Phase 10's TenantIsolationTests only ever *read* pre-existing
 * Django-created rows, so only the columns each needed were mapped — this
 * phase is the first to actually INSERT a User via Hibernate (creating
 * cross-hospital test fixtures), which surfaced the gap: `is_superuser`,
 * `is_staff`, `first_name`, `last_name`, `email`, and `date_joined` are all
 * NOT NULL in Postgres with no DB-level default (Django enforces the
 * `False`/`""`/`now()` defaults at the app layer only), so an insert that
 * omits them fails outright rather than silently taking a wrong value —
 * caught live, not guessed. Mapped here with the same Django defaults
 * (`AbstractUser`'s `is_staff`/`is_superuser=False`, `first_name`/
 * `last_name`/`email=""`, `date_joined=now()`), still with no public
 * setters — nothing outside test fixtures needs to change them.
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

    @Column(name = "is_superuser", nullable = false)
    private boolean superuser;

    @Column(name = "is_staff", nullable = false)
    private boolean staff;

    @Column(name = "first_name", nullable = false, length = 150)
    private String firstName = "";

    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName = "";

    @Column(nullable = false, length = 254)
    private String email = "";

    @Column(name = "date_joined", nullable = false)
    private OffsetDateTime dateJoined;

    protected User() {
        // JPA
    }

    @PrePersist
    protected void onCreate() {
        if (dateJoined == null) {
            dateJoined = OffsetDateTime.now();
        }
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

    public boolean isSuperuser() {
        return superuser;
    }

    public boolean isStaff() {
        return staff;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public OffsetDateTime getDateJoined() {
        return dateJoined;
    }
}
