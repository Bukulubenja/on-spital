package com.hms.repository;

import com.hms.entity.Drug;
import com.hms.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * Locks every in-stock batch of a drug, earliest expiry first — direct
     * analogue of Django's Stock.objects.select_for_update().filter(drug=...,
     * quantity__gt=0).order_by("expiry_date") in dispense_prescription_item,
     * serializing concurrent dispenses of the same drug against each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Stock> findByDrugAndQuantityGreaterThanOrderByExpiryDateAsc(Drug drug, int quantity);

    @Query("select coalesce(sum(s.quantity), 0) from Stock s where s.drug = :drug")
    int sumQuantityByDrug(@Param("drug") Drug drug);

    List<Stock> findByDrugOrderByExpiryDateAsc(Drug drug);

    /**
     * Locked lookup of a drug's existing batch by batch number — analogue of
     * Django's Stock.objects.select_for_update().get_or_create(drug=...,
     * batch_number=...) in receive_stock (the "found" half; a miss falls
     * through to a plain insert, same as Django's create()).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Stock> findByDrugAndBatchNumber(Drug drug, String batchNumber);

    /** Locked lookup by id — analogue of Django's select_for_update().get(pk=...) in adjust_stock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id = :id")
    Optional<Stock> findByIdForUpdate(@Param("id") Long id);

    long countByQuantityGreaterThanAndExpiryDateBetween(int quantity, LocalDate start, LocalDate end);

    long countByQuantityGreaterThanAndExpiryDateLessThan(int quantity, LocalDate date);
}
