package com.hms.stock.dto;

import com.hms.entity.StockTransaction;

import java.time.OffsetDateTime;

public record TransactionView(Long id, String drugName, String type, int quantity, String reason, OffsetDateTime date) {

    public static TransactionView from(StockTransaction transaction) {
        return new TransactionView(
                transaction.getId(),
                transaction.getDrug().getName(),
                transaction.getType().name(),
                transaction.getQuantity(),
                transaction.getReason(),
                transaction.getDate()
        );
    }
}
