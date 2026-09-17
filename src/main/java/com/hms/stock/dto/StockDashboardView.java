package com.hms.stock.dto;

import java.util.List;

/** Mirrors stock_dashboard's context (hospital/views.py). */
public record StockDashboardView(
        List<DrugSummary> drugs,
        long expiringSoonCount,
        long expiredCount,
        List<TransactionView> recentTransactions
) {
}
