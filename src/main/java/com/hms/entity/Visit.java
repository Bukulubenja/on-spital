package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Maps to Django's existing hospital_visit table. Only Phase 2's
 * check-in path writes here for now (visitType=OPD, status=WAITING_DOCTOR)
 * — the full state machine (visit_status_after_consultation/_after_lab in
 * Django's services.py) is exercised starting the Doctor workflow phase.
 */
@Entity
@Table(name = "hospital_visit")
public class Visit extends TenantEntity {

    public enum Status {
        REGISTERED, WAITING_DOCTOR, IN_CONSULTATION, WAITING_LAB, WAITING_PHARMACY, COMPLETED
    }

    public enum VisitType {
        OPD, EMERGENCY, INPATIENT
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = true)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = true)
    private User doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = true)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 20)
    private VisitType visitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "visit_date", nullable = false)
    private OffsetDateTime visitDate;

    @Column(nullable = false)
    private String symptoms;

    @Column(name = "diagnosis_summary", nullable = false)
    private String diagnosisSummary;

    protected Visit() {
        // JPA
    }

    public Visit(Appointment appointment, Patient patient, User doctor, Department department,
                 VisitType visitType, Status status, String symptoms) {
        this.appointment = appointment;
        this.patient = patient;
        this.doctor = doctor;
        this.department = department;
        this.visitType = visitType;
        this.status = status;
        this.symptoms = symptoms == null ? "" : symptoms;
        this.diagnosisSummary = "";
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public Patient getPatient() {
        return patient;
    }

    public User getDoctor() {
        return doctor;
    }

    public Department getDepartment() {
        return department;
    }

    public VisitType getVisitType() {
        return visitType;
    }

    public Status getStatus() {
        return status;
    }

    public OffsetDateTime getVisitDate() {
        return visitDate;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public String getDiagnosisSummary() {
        return diagnosisSummary;
    }

    @PrePersist
    protected void onCreate() {
        if (visitDate == null) {
            visitDate = OffsetDateTime.now();
        }
    }
}
