package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Maps to Django's existing hospital_drug table. Read-only lookup for now — Stock Manager phase will add writes. */
@Entity
@Table(name = "hospital_drug")
public class Drug extends TenantEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 100)
    private String strength;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 200)
    private String manufacturer;

    protected Drug() {
        // JPA
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getStrength() {
        return strength;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getManufacturer() {
        return manufacturer;
    }
}
