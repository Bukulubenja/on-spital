package com.hms.repository;

import com.hms.entity.LabOrder;
import com.hms.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {

    Optional<LabOrder> findByVisit(Visit visit);

    boolean existsByVisit(Visit visit);
}
