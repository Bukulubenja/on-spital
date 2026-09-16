package com.hms.reception.dto;

import com.hms.entity.Patient;

import java.time.LocalDate;

public record PatientResponse(Long id, String patientNumber, String fullName, Patient.Gender gender, LocalDate dateOfBirth) {

    public static PatientResponse from(Patient patient) {
        return new PatientResponse(
                patient.getId(), patient.getPatientNumber(), patient.getFullName(),
                patient.getGender(), patient.getDateOfBirth()
        );
    }
}
