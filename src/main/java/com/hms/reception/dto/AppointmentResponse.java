package com.hms.reception.dto;

import com.hms.entity.Appointment;

import java.time.OffsetDateTime;

public record AppointmentResponse(
        Long id, Long patientId, String doctorUsername, String departmentName,
        OffsetDateTime appointmentDate, Appointment.Status status, Appointment.ConsultationType consultationType
) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getDoctor() == null ? null : appointment.getDoctor().getUsername(),
                appointment.getDepartment() == null ? null : appointment.getDepartment().getName(),
                appointment.getAppointmentDate(),
                appointment.getStatus(),
                appointment.getConsultationType()
        );
    }
}
