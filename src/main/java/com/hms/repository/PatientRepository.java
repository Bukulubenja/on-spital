package com.hms.repository;

import com.hms.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    List<Patient> findTop5ByOrderByCreatedAtDesc();

    List<Patient> findTop50ByOrderByFullNameAsc();

    List<Patient> findTop50ByFullNameContainingIgnoreCaseOrPatientNumberContainingIgnoreCaseOrderByFullNameAsc(
            String fullNameQuery, String patientNumberQuery
    );
}
