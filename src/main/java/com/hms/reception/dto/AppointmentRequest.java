package com.hms.reception.dto;

import com.hms.entity.Appointment;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** Mirrors AppointmentForm's field set (hospital/forms.py). */
public record AppointmentRequest(
        @NotNull Long patientId,
        @NotNull Long doctorId,
        @NotNull Long departmentId,
        @NotNull @Future OffsetDateTime appointmentDate,
        String reason,
        Appointment.ConsultationType consultationType,
        String meetingLink
) {
}
