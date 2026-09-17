package com.hms.repository;

import com.hms.entity.Payment;
import com.hms.entity.VisitInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoice(VisitInvoice invoice);

    /** Mirrors sum(p.amount_paid for p in self.payments.all()) — VisitInvoice.amount_paid (models.py). */
    @Query("select coalesce(sum(p.amountPaid), 0) from Payment p where p.invoice = :invoice")
    BigDecimal sumAmountPaidByInvoice(@Param("invoice") VisitInvoice invoice);

    @Query("select coalesce(sum(p.amountPaid), 0) from Payment p where p.paymentDate >= :start and p.paymentDate < :end")
    BigDecimal sumAmountPaidBetween(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query("select coalesce(sum(p.amountPaid), 0) from Payment p")
    BigDecimal sumAmountPaidTotal();

    List<Payment> findByPaymentDateBetween(OffsetDateTime start, OffsetDateTime end);

    List<Payment> findTop5ByOrderByPaymentDateDesc();
}
