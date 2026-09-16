package com.hms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One row per hospital organization — the tenant boundary every
 * {@link com.hms.tenancy.TenantEntity} is scoped to. Maps to Django's
 * existing hospital_hospital table (default db_table naming, no override
 * in the Django model).
 */
@Entity
@Table(name = "hospital_hospital")
public class Hospital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 63)
    private String subdomain;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Hospital() {
        // JPA
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
