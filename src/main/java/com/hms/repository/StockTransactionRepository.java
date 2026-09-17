package com.hms.repository;

import com.hms.entity.Drug;
import com.hms.entity.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    List<StockTransaction> findTop20ByOrderByDateDesc();

    List<StockTransaction> findTop20ByDrugOrderByDateDesc(Drug drug);
}
