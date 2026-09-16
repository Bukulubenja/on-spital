package com.hms.cashier.dto;

import java.math.BigDecimal;
import java.util.List;

/** Read-only parity endpoint mirroring the read half of visit_invoice_detail (hospital/views.py). */
public record InvoiceView(
        Long invoiceId,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        String status,
        List<Item> items,
        List<PaymentRecord> payments
) {

    public record Item(Long itemId, String serviceName, int quantity, BigDecimal price, BigDecimal subtotal) {
    }

    public record PaymentRecord(String receiptNumber, BigDecimal amountPaid, String method, String reference) {
    }
}
