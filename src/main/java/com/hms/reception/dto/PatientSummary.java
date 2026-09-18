package com.hms.reception.dto;

import com.hms.entity.Patient;

/** Lightweight lookup row for the appointment-booking patient picker — not PatientResponse's registration-confirmation shape. */
public record PatientSummary(Long id, String patientNumber, String fullName, String phone) {

    public static PatientSummary from(Patient patient) {
        return new PatientSummary(patient.getId(), patient.getPatientNumber(), patient.getFullName(), patient.getPhone());
    }
}
