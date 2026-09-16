package com.hms.cashier.dto;

import com.hms.entity.Payment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Mirrors PaymentForm's field set (hospital/forms.py). The ">0" rule lives
 * here as bean validation; the "<= invoice.balance_due" rule needs the
 * invoice's current state, so it stays a runtime check in CashierService,
 * same split Django's clean_amount_paid makes within one method.
 */
public record PaymentRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amountPaid,
        @NotNull Payment.PaymentMethod method,
        String reference
) {
}
