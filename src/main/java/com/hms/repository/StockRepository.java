package com.hms.repository;

import com.hms.entity.Drug;
import com.hms.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
