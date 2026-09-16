package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Maps to Django's existing hospital_visitinvoice table. amountPaid/
 * balanceDue are Django @property values derived from the payments
 * relation — computed by CashierService from a repository sum query
 * rather than mapped here, same choice LabOrder/PrescriptionItem made
 * for their own derived counts.
 */
@Entity
@Table(name = "hospital_visitinvoice")
public class VisitInvoice extends TenantEntity {

    public enum Status {
        UNPAID, PARTIAL, PAID
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected VisitInvoice() {
        // JPA
    }

    public VisitInvoice(Visit visit, Patient patient, BigDecimal totalAmount) {
        this.visit = visit;
        this.patient = patient;
        this.totalAmount = totalAmount;
        this.status = Status.UNPAID;
    }

    public Visit getVisit() {
        return visit;
    }

    public Patient getPatient() {
        return patient;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
