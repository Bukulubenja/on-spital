package com.hms.repository;

import com.hms.entity.LabOrder;
import com.hms.entity.LabOrderItem;
import com.hms.entity.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderItemRepository extends JpaRepository<LabOrderItem, Long> {

    boolean existsByLabOrderAndTest(LabOrder labOrder, LabTest test);
}
