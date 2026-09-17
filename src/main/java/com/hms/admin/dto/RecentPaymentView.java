package com.hms.admin.dto;

import com.hms.entity.Payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RecentPaymentView(Long id, String receiptNumber, String patientName, BigDecimal amountPaid, OffsetDateTime paymentDate) {

    public static RecentPaymentView from(Payment payment) {
        return new RecentPaymentView(
                payment.getId(),
                payment.getReceiptNumber(),
                payment.getInvoice().getPatient().getFullName(),
                payment.getAmountPaid(),
                payment.getPaymentDate()
        );
    }
}
