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

/** Maps to Django's existing hospital_prescription table. */
@Entity
@Table(name = "hospital_prescription")
public class Prescription extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = true)
    private User doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Prescription() {
        // JPA
    }

    public Prescription(Visit visit, User doctor, Patient patient) {
        this.visit = visit;
        this.doctor = doctor;
        this.patient = patient;
    }

    public Visit getVisit() {
        return visit;
    }

    public User getDoctor() {
        return doctor;
    }

    public Patient getPatient() {
        return patient;
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
