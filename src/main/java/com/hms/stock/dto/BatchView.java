package com.hms.stock.dto;

import com.hms.entity.Stock;

import java.time.LocalDate;

/** The "Status" column of drug_stock_detail.html, ported as a computed enum rather than left for the client to derive from dates. */
public record BatchView(Long stockId, String batchNumber, int quantity, LocalDate expiryDate, Status status) {

    public enum Status {
        EXPIRED, EXPIRING_SOON, FRESH
    }

    public static BatchView from(Stock stock, LocalDate today, LocalDate expiryWarningDate) {
        Status status;
        if (stock.getExpiryDate().isBefore(today)) {
            status = Status.EXPIRED;
        } else if (!stock.getExpiryDate().isAfter(expiryWarningDate)) {
            status = Status.EXPIRING_SOON;
        } else {
            status = Status.FRESH;
        }
        return new BatchView(stock.getId(), stock.getBatchNumber(), stock.getQuantity(), stock.getExpiryDate(), status);
    }
}
