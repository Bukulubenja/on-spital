package com.hms.repository;

import com.hms.entity.Prescription;
import com.hms.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Optional<Prescription> findByVisit(Visit visit);

    boolean existsByVisit(Visit visit);
}
