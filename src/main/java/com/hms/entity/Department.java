package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Maps to Django's existing hospital_department table. */
@Entity
@Table(
        name = "hospital_department",
        uniqueConstraints = @UniqueConstraint(name = "unique_department_name_per_hospital", columnNames = {"hospital_id", "name"})
)
public class Department extends TenantEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String description;

    protected Department() {
        // JPA
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
