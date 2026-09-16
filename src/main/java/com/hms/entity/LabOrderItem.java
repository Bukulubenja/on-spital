package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Maps to Django's existing hospital_laborderitem table. */
@Entity
@Table(
        name = "hospital_laborderitem",
        uniqueConstraints = @UniqueConstraint(name = "unique_test_per_order", columnNames = {"lab_order_id", "test_id"})
)
public class LabOrderItem extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_order_id", nullable = false)
    private LabOrder labOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private LabTest test;

    protected LabOrderItem() {
        // JPA
    }

    public LabOrderItem(LabOrder labOrder, LabTest test) {
        this.labOrder = labOrder;
        this.test = test;
    }

    public LabOrder getLabOrder() {
        return labOrder;
    }

    public LabTest getTest() {
        return test;
    }
}
