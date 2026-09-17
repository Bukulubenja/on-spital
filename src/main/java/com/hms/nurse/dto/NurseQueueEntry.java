package com.hms.nurse.dto;

import com.hms.entity.Visit;

/** Mirrors nurse_dashboard's queryset (hospital/views.py) — one row per visit still awaiting a doctor. */
public record NurseQueueEntry(Long visitId, String patientName, String doctorUsername, boolean hasVitals) {

    public static NurseQueueEntry from(Visit visit, boolean hasVitals) {
        return new NurseQueueEntry(
                visit.getId(),
                visit.getPatient().getFullName(),
                visit.getDoctor() == null ? null : visit.getDoctor().getUsername(),
                hasVitals
        );
    }
}
