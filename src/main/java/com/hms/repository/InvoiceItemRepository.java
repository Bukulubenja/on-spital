package com.hms.repository;

import com.hms.entity.InvoiceItem;
import com.hms.entity.VisitInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    List<InvoiceItem> findByInvoice(VisitInvoice invoice);

    /** Mirrors sum(item.subtotal for item in invoice.items.all()) in refresh_invoice_totals (services.py). */
    @Query("select coalesce(sum(i.quantity * i.price), 0) from InvoiceItem i where i.invoice = :invoice")
    BigDecimal sumSubtotalByInvoice(@Param("invoice") VisitInvoice invoice);
}
