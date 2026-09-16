package com.hms.repository;

import com.hms.entity.Visit;
import com.hms.entity.VisitInvoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VisitInvoiceRepository extends JpaRepository<VisitInvoice, Long> {

    Optional<VisitInvoice> findByVisit(Visit visit);

    /**
     * Locks the visit's invoice for the rest of the caller's transaction —
     * direct analogue of Django's VisitInvoice.objects.select_for_update()
     * in record_payment, serializing concurrent payments against the same
     * invoice's balance check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from VisitInvoice i where i.visit = :visit")
    Optional<VisitInvoice> findByVisitForUpdate(@Param("visit") Visit visit);
}
