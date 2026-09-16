package com.hms.repository;

import com.hms.entity.LabOrder;
import com.hms.entity.Visit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {

    Optional<LabOrder> findByVisit(Visit visit);

    boolean existsByVisit(Visit visit);

    /**
     * Locks the visit's LabOrder for the rest of the caller's transaction —
     * direct analogue of Django's LabOrder.objects.select_for_update() in
     * record_lab_result, serializing concurrent result submissions against
     * the same order's fully-resulted completion check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LabOrder l where l.visit = :visit")
    Optional<LabOrder> findByVisitForUpdate(@Param("visit") Visit visit);
}
