package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Maps to Django's existing hospital_labtest table. Read-only lookup for now. */
@Entity
@Table(name = "hospital_labtest")
public class LabTest extends TenantEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    protected LabTest() {
        // JPA
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
