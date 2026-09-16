package com.hms.cashier.dto;

import java.math.BigDecimal;

/** alreadySettled mirrors Django's messages.info(...) "already fully paid" path instead of erroring. */
public record PaymentResponse(String receiptNumber, BigDecimal amountPaid, boolean alreadySettled, String invoiceStatus) {
}
