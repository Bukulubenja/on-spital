package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Maps to Django's existing hospital_service table — a billable charge
 * type. Named BillableService, not Service, to avoid colliding with
 * Spring's own @Service stereotype annotation in every class that would
 * otherwise need to import both. Read-only lookup, like Drug.
 */
@Entity
@Table(name = "hospital_service")
public class BillableService extends TenantEntity {

    public enum ServiceType {
        APPOINTMENT, LAB, PHARMACY
    }

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 30)
    private ServiceType serviceType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    protected BillableService() {
        // JPA
    }

    public String getName() {
        return name;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
