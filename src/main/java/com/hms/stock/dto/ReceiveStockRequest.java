package com.hms.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Mirrors ReceiveStockForm's field set (hospital/forms.py). The "must be in
 * the future" expiry rule needs today's date, so it stays a runtime check in
 * StockManagerService, same split PaymentRequest/CashierService make for
 * their own date/balance-dependent rule.
 */
public record ReceiveStockRequest(
        @NotBlank @Size(max = 100) String batchNumber,
        @NotNull @Positive Integer quantity,
        @NotNull LocalDate expiryDate
) {
}
