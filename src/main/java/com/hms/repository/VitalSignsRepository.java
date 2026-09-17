package com.hms.repository;

import com.hms.entity.VitalSigns;
import com.hms.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VitalSignsRepository extends JpaRepository<VitalSigns, Long> {

    boolean existsByVisit(Visit visit);
}
