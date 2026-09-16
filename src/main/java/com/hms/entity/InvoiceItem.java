package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;

/** Maps to Django's existing hospital_invoiceitem table — one billed charge line. */
@Entity
@Table(name = "hospital_invoiceitem")
public class InvoiceItem extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private VisitInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private BillableService service;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    protected InvoiceItem() {
        // JPA
    }

    public InvoiceItem(VisitInvoice invoice, BillableService service, int quantity, BigDecimal price) {
        this.invoice = invoice;
        this.service = service;
        this.quantity = quantity;
        this.price = price;
    }

    public VisitInvoice getInvoice() {
        return invoice;
    }

    public BillableService getService() {
        return service;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Transient
    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
