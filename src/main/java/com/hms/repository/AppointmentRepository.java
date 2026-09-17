package com.hms.repository;

import com.hms.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    long countByAppointmentDateBetween(OffsetDateTime start, OffsetDateTime end);

    List<Appointment> findByAppointmentDateBetween(OffsetDateTime start, OffsetDateTime end);
}
