package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Maps to Django's existing hospital_payment table. receiptNumber is
 * unique + NOT NULL with no DB default — mirrors Django's two-step save
 * (insert with a placeholder, then set the real value once the row has an
 * id) via {@link #assignReceiptNumber()}, called after the first save.
 */
@Entity
@Table(name = "hospital_payment")
public class Payment extends TenantEntity {

    public enum PaymentMethod {
        CASH, MOBILE_MONEY, INSURANCE
    }

    @Column(name = "receipt_number", nullable = false, unique = true, length = 30)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private VisitInvoice invoice;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, length = 200)
    private String reference;

    @Column(name = "payment_date", nullable = false)
    private OffsetDateTime paymentDate;

    protected Payment() {
        // JPA
    }

    public Payment(VisitInvoice invoice, BigDecimal amountPaid, PaymentMethod method, String reference) {
        this.receiptNumber = "";
        this.invoice = invoice;
        this.amountPaid = amountPaid;
        this.method = method;
        this.reference = reference == null ? "" : reference;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    /** Called once this row has a generated id — mirrors Django's f"RCPT-{payment.pk:06d}". */
    public void assignReceiptNumber() {
        this.receiptNumber = String.format("RCPT-%06d", getId());
    }

    public VisitInvoice getInvoice() {
        return invoice;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getReference() {
        return reference;
    }

    public OffsetDateTime getPaymentDate() {
        return paymentDate;
    }

    @PrePersist
    protected void onCreate() {
        if (paymentDate == null) {
            paymentDate = OffsetDateTime.now();
        }
    }
}
