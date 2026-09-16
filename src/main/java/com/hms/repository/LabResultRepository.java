package com.hms.repository;

import com.hms.entity.LabOrder;
import com.hms.entity.LabResult;
import com.hms.entity.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LabResultRepository extends JpaRepository<LabResult, Long> {

    boolean existsByLabOrderAndTest(LabOrder labOrder, LabTest test);

    Optional<LabResult> findByLabOrderAndTest(LabOrder labOrder, LabTest test);

    long countByLabOrder(LabOrder labOrder);
}
