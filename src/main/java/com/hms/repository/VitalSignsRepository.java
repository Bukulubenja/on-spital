package com.hms.repository;

import com.hms.entity.VitalSigns;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VitalSignsRepository extends JpaRepository<VitalSigns, Long> {
}
