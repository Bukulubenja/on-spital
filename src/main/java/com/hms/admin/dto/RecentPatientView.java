package com.hms.admin.dto;

import com.hms.entity.Patient;

import java.time.OffsetDateTime;

public record RecentPatientView(Long id, String patientNumber, String fullName, OffsetDateTime createdAt) {

    public static RecentPatientView from(Patient patient) {
        return new RecentPatientView(patient.getId(), patient.getPatientNumber(), patient.getFullName(), patient.getCreatedAt());
    }
}
