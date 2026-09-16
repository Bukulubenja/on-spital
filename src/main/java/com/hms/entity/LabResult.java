package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Maps to Django's existing hospital_labresult table. */
@Entity
@Table(name = "hospital_labresult")
public class LabResult extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_order_id", nullable = false)
    private LabOrder labOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private LabTest test;

    @Column(name = "result_value", nullable = false, length = 200)
    private String resultValue;

    @Column(name = "normal_range", nullable = false, length = 200)
    private String normalRange;

    @Column(nullable = false)
    private String remarks;

    @Column(name = "result_date", nullable = false)
    private OffsetDateTime resultDate;

    protected LabResult() {
        // JPA
    }

    public LabResult(LabOrder labOrder, LabTest test, String resultValue, String normalRange, String remarks) {
        this.labOrder = labOrder;
        this.test = test;
        this.resultValue = resultValue;
        this.normalRange = normalRange == null ? "" : normalRange;
        this.remarks = remarks == null ? "" : remarks;
    }

    public LabOrder getLabOrder() {
        return labOrder;
    }

    public LabTest getTest() {
        return test;
    }

    public String getResultValue() {
        return resultValue;
    }

    public String getNormalRange() {
        return normalRange;
    }

    public String getRemarks() {
        return remarks;
    }

    public OffsetDateTime getResultDate() {
        return resultDate;
    }

    @PrePersist
    protected void onCreate() {
        if (resultDate == null) {
            resultDate = OffsetDateTime.now();
        }
    }
}
