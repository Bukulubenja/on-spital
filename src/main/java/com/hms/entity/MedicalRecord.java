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

/** Maps to Django's existing hospital_medicalrecord table. */
@Entity
@Table(name = "hospital_medicalrecord")
public class MedicalRecord extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = true)
    private User doctor;

    @Column(nullable = false)
    private String diagnosis;

    @Column(nullable = false)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MedicalRecord() {
        // JPA
    }

    public MedicalRecord(Visit visit, Patient patient, User doctor, String diagnosis, String notes) {
        this.visit = visit;
        this.patient = patient;
        this.doctor = doctor;
        this.diagnosis = diagnosis;
        this.notes = notes == null ? "" : notes;
    }

    public Visit getVisit() {
        return visit;
    }

    public Patient getPatient() {
        return patient;
    }

    public User getDoctor() {
        return doctor;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getNotes() {
        return notes;
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
