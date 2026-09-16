package com.hms.repository;

import com.hms.entity.Appointment;
import com.hms.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    boolean existsByAppointment(Appointment appointment);
}
