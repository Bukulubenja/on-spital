package com.hms.stock.dto;

import java.util.List;

/** Mirrors drug_stock_detail's context (hospital/views.py). */
public record DrugStockDetailView(
        Long drugId,
        String drugName,
        String strength,
        List<BatchView> batches,
        List<TransactionView> transactions
) {
}
