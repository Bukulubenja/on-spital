package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Maps to Django's existing hospital_appointment table. */
@Entity
@Table(name = "hospital_appointment")
public class Appointment extends TenantEntity {

    public enum Status {
        SCHEDULED, COMPLETED, CANCELLED, NO_SHOW
    }

    public enum ConsultationType {
        IN_PERSON, TELEMEDICINE
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    // Nullable at the DB level (Django's on_delete=SET_NULL requires
    // null=True) even though the booking flow always requires one.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = true)
    private User doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = true)
    private Department department;

    @Column(name = "appointment_date", nullable = false)
    private OffsetDateTime appointmentDate;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "consultation_type", nullable = false, length = 20)
    private ConsultationType consultationType;

    @Column(name = "meeting_link", nullable = false)
    private String meetingLink;

    protected Appointment() {
        // JPA
    }

    public Appointment(Patient patient, User doctor, Department department, OffsetDateTime appointmentDate,
                        String reason, ConsultationType consultationType, String meetingLink) {
        this.patient = patient;
        this.doctor = doctor;
        this.department = department;
        this.appointmentDate = appointmentDate;
        this.reason = reason == null ? "" : reason;
        this.status = Status.SCHEDULED;
        this.consultationType = consultationType == null ? ConsultationType.IN_PERSON : consultationType;
        this.meetingLink = meetingLink == null ? "" : meetingLink;
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

    public OffsetDateTime getAppointmentDate() {
        return appointmentDate;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public ConsultationType getConsultationType() {
        return consultationType;
    }

    public String getMeetingLink() {
        return meetingLink;
    }
}
