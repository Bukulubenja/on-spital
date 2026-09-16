package com.hms.cashier;

import com.hms.cashier.dto.InvoiceItemRequest;
import com.hms.cashier.dto.PaymentRequest;
import com.hms.entity.BillableService;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.entity.Payment;
import com.hms.entity.Visit;
import com.hms.entity.VisitInvoice;
import com.hms.repository.BillableServiceRepository;
import com.hms.repository.InvoiceItemRepository;
import com.hms.repository.PaymentRepository;
import com.hms.repository.VisitInvoiceRepository;
import com.hms.repository.VisitRepository;
import com.hms.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.hms.testsupport.EntityTestSupport.setField;
import static com.hms.testsupport.EntityTestSupport.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashierServiceTests {

    private final VisitRepository visitRepository = mock(VisitRepository.class);
    private final VisitInvoiceRepository visitInvoiceRepository = mock(VisitInvoiceRepository.class);
    private final InvoiceItemRepository invoiceItemRepository = mock(InvoiceItemRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final BillableServiceRepository billableServiceRepository = mock(BillableServiceRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final CashierService service = new CashierService(
            visitRepository, visitInvoiceRepository, invoiceItemRepository,
            paymentRepository, billableServiceRepository, entityManager
    );

    @BeforeEach
    void setUpTenantContext() throws Exception {
        TenantContext.set(1L);
        var constructor = Hospital.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Hospital hospital = constructor.newInstance();
        setField(hospital, Hospital.class, "id", 1L);
        when(entityManager.getReference(Hospital.class, 1L)).thenReturn(hospital);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private Visit newVisit() {
        Patient patient = new Patient("Jane Doe", Patient.Gender.FEMALE, LocalDate.of(1990, 1, 1),
                "555-0100", "", "", "", "");
        return withId(new Visit(null, patient, null, null, Visit.VisitType.OPD, Visit.Status.WAITING_DOCTOR, ""), 20L);
    }

    private static VisitInvoice newInvoice(Long id, Visit visit, BigDecimal totalAmount) {
        return withId(new VisitInvoice(visit, visit.getPatient(), totalAmount), id);
    }

    private static BillableService newBillableService(Long id, String name, BigDecimal price) throws Exception {
        var constructor = BillableService.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        BillableService billableService = constructor.newInstance();
        setField(billableService, BillableService.class, "name", name);
        setField(billableService, BillableService.class, "price", price);
        return withId(billableService, id);
    }

    @Test
    void viewInvoiceCreatesOneWhenNoneExistsYet() {
        Visit visit = newVisit();
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));
        when(visitInvoiceRepository.findByVisit(visit)).thenReturn(Optional.empty());
        when(visitInvoiceRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 30L));
        when(invoiceItemRepository.findByInvoice(any())).thenReturn(List.of());
        when(paymentRepository.findByInvoice(any())).thenReturn(List.of());
        when(paymentRepository.sumAmountPaidByInvoice(any())).thenReturn(BigDecimal.ZERO);

        var view = service.viewInvoice(20L);

        assertThat(view.invoiceId()).isEqualTo(30L);
        assertThat(view.totalAmount()).isEqualByComparingTo("0");
        assertThat(view.balanceDue()).isEqualByComparingTo("0");
        assertThat(view.status()).isEqualTo("UNPAID");
    }

    @Test
    void addInvoiceItemCopiesTheServicePriceAndRefreshesTotals() throws Exception {
        Visit visit = newVisit();
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));
        VisitInvoice invoice = newInvoice(30L, visit, BigDecimal.ZERO);
        when(visitInvoiceRepository.findByVisit(visit)).thenReturn(Optional.of(invoice));

        BillableService consultationFee = newBillableService(5L, "Consultation Fee", new BigDecimal("20.00"));
        when(billableServiceRepository.findById(5L)).thenReturn(Optional.of(consultationFee));
        when(invoiceItemRepository.sumSubtotalByInvoice(invoice)).thenReturn(new BigDecimal("40.00"));
        when(paymentRepository.sumAmountPaidByInvoice(invoice)).thenReturn(BigDecimal.ZERO);

        service.addInvoiceItem(20L, new InvoiceItemRequest(5L, 2));

        verify(invoiceItemRepository).save(any());
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("40.00");
        assertThat(invoice.getStatus()).isEqualTo(VisitInvoice.Status.UNPAID);
    }

    @Test
    void addInvoiceItemRejectsAnUnknownService() {
        Visit visit = newVisit();
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));
        when(visitInvoiceRepository.findByVisit(visit)).thenReturn(Optional.of(newInvoice(30L, visit, BigDecimal.ZERO)));
        when(billableServiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addInvoiceItem(20L, new InvoiceItemRequest(99L, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void recordPaymentRejectsWhenNoInvoiceExistsYet() {
        Visit visit = newVisit();
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));
        when(visitInvoiceRepository.findByVisitForUpdate(visit)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordPayment(20L, new PaymentRequest(BigDecimal.TEN, Payment.PaymentMethod.CASH, "")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invoice not found");
    }

    @Test
    void recordPaymentIsIdempotentOnAnAlreadySettledInvoice() {
        Visit visit = newVisit();
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));
        VisitInvoice invoice = newInvoice(30L, visit, new BigDecimal("50.00"));
        setField(invoice, VisitInvoice.class, "status", VisitInvoice.Status.PAID);
        when(visitInvoiceRepository.findByVisitForUpdate(visit)).thenReturn(Optional.of(invoice));
        when(paymentRepository.sumAmountPaidByInvoice(invoice)).thenReturn(new BigDecimal("50.00"));

        var response = service.recordPayment(20L, new PaymentRequest(BigDecimal.TEN, Payment.PaymentMethod.CASH, ""));

        assertThat(response.alreadySettled()).isTrue();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void recordPaymentRejectsAnAmountExceedingTheBalanceDue() {
        Visit visit = newVisit();
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));
        VisitInvoice invoice = newInvoice(30L, visit, new BigDecimal("50.00"));
        when(visitInvoiceRepository.findByVisitForUpdate(visit)).thenReturn(Optional.of(invoice));
        when(paymentRepository.sumAmountPaidByInvoice(invoice)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.recordPayment(20L, new PaymentRequest(new BigDecimal("100.00"), Payment.PaymentMethod.CASH, "")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exceeds the outstanding balance");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void recordPaymentAssignsAReceiptNumberAndAdvancesInvoiceStatus() {
        Visit visit = newVisit();
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));
        VisitInvoice invoice = newInvoice(30L, visit, new BigDecimal("50.00"));
        when(visitInvoiceRepository.findByVisitForUpdate(visit)).thenReturn(Optional.of(invoice));
        when(paymentRepository.sumAmountPaidByInvoice(invoice))
                .thenReturn(BigDecimal.ZERO)   // balance check before saving
                .thenReturn(new BigDecimal("50.00")); // refreshInvoiceTotals after saving
        when(invoiceItemRepository.sumSubtotalByInvoice(invoice)).thenReturn(new BigDecimal("50.00"));
        when(paymentRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 42L));

        var response = service.recordPayment(20L, new PaymentRequest(new BigDecimal("50.00"), Payment.PaymentMethod.CASH, "ref-1"));

        assertThat(response.alreadySettled()).isFalse();
        assertThat(response.receiptNumber()).isEqualTo("RCPT-000042");
        assertThat(invoice.getStatus()).isEqualTo(VisitInvoice.Status.PAID);
    }
}
