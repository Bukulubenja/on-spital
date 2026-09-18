package com.hms.cashier;

import com.hms.audit.AuditService;
import com.hms.cashier.dto.InvoiceItemRequest;
import com.hms.cashier.dto.InvoiceView;
import com.hms.cashier.dto.PaymentRequest;
import com.hms.cashier.dto.PaymentResponse;
import com.hms.cashier.dto.ServiceSummary;
import com.hms.entity.BillableService;
import com.hms.entity.InvoiceItem;
import com.hms.entity.Payment;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.entity.VisitInvoice;
import com.hms.repository.BillableServiceRepository;
import com.hms.repository.InvoiceItemRepository;
import com.hms.repository.PaymentRepository;
import com.hms.repository.VisitInvoiceRepository;
import com.hms.security.HmsUserPrincipal;
import com.hms.tenancy.TenantScoping;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mirrors hospital/views.py's visit_invoice_detail/add_invoice_item/
 * record_payment plus hospital/services.py's refresh_invoice_totals.
 * Like Lab and Pharmacy, Cashier works off the visit directly — no
 * per-user ownership check, and (unlike Lab/Pharmacy) no visit-status
 * gate either: a visit can be billed at any point in its lifecycle,
 * matching Django's cashier_dashboard listing every visit, not a
 * status-filtered queue.
 */
@Service
public class CashierService {

    private final VisitInvoiceRepository visitInvoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final PaymentRepository paymentRepository;
    private final BillableServiceRepository billableServiceRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;

    public CashierService(
            VisitInvoiceRepository visitInvoiceRepository,
            InvoiceItemRepository invoiceItemRepository,
            PaymentRepository paymentRepository,
            BillableServiceRepository billableServiceRepository,
            EntityManager entityManager,
            AuditService auditService
    ) {
        this.visitInvoiceRepository = visitInvoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.paymentRepository = paymentRepository;
        this.billableServiceRepository = billableServiceRepository;
        this.entityManager = entityManager;
        this.auditService = auditService;
    }

    private static User currentCashier() {
        var principal = (HmsUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getUser();
    }

    private Visit requireVisit(Long visitId) {
        return TenantScoping.findByIdTenantScoped(entityManager, Visit.class, visitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
    }

    private VisitInvoice getOrCreateInvoice(Visit visit) {
        return visitInvoiceRepository.findByVisit(visit).orElseGet(() -> {
            VisitInvoice created = new VisitInvoice(visit, visit.getPatient(), BigDecimal.ZERO);
            created.setHospital(TenantScoping.currentHospitalReference(entityManager));
            return visitInvoiceRepository.save(created);
        });
    }

    /**
     * Recomputes total_amount from line items and status from payments made
     * so far — call after adding a charge or recording a payment, either of
     * which can change what's owed or whether it's settled.
     */
    private BigDecimal refreshInvoiceTotals(VisitInvoice invoice) {
        BigDecimal totalAmount = invoiceItemRepository.sumSubtotalByInvoice(invoice);
        BigDecimal amountPaid = paymentRepository.sumAmountPaidByInvoice(invoice);
        invoice.setTotalAmount(totalAmount);

        if (totalAmount.signum() <= 0 || amountPaid.signum() <= 0) {
            invoice.setStatus(VisitInvoice.Status.UNPAID);
        } else if (amountPaid.compareTo(totalAmount) >= 0) {
            invoice.setStatus(VisitInvoice.Status.PAID);
        } else {
            invoice.setStatus(VisitInvoice.Status.PARTIAL);
        }
        return amountPaid;
    }

    @Transactional
    public InvoiceView viewInvoice(Long visitId) {
        Visit visit = requireVisit(visitId);
        VisitInvoice invoice = getOrCreateInvoice(visit);
        return toView(invoice, paymentRepository.sumAmountPaidByInvoice(invoice));
    }

    @Transactional
    public void addInvoiceItem(Long visitId, InvoiceItemRequest request) {
        Visit visit = requireVisit(visitId);
        VisitInvoice invoice = getOrCreateInvoice(visit);

        BillableService service = TenantScoping.findByIdTenantScoped(entityManager, BillableService.class, request.serviceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        InvoiceItem item = new InvoiceItem(invoice, service, request.quantity(), service.getPrice());
        item.setHospital(TenantScoping.currentHospitalReference(entityManager));
        invoiceItemRepository.save(item);

        refreshInvoiceTotals(invoice);
    }

    @Transactional
    public PaymentResponse recordPayment(Long visitId, PaymentRequest request, String ipAddress) {
        Visit visit = requireVisit(visitId);
        VisitInvoice invoice = visitInvoiceRepository.findByVisitForUpdate(visit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        BigDecimal amountPaidSoFar = paymentRepository.sumAmountPaidByInvoice(invoice);
        BigDecimal balanceDue = invoice.getTotalAmount().subtract(amountPaidSoFar);
        if (balanceDue.signum() <= 0) {
            return new PaymentResponse(null, BigDecimal.ZERO, true, invoice.getStatus().name());
        }

        if (request.amountPaid().compareTo(balanceDue) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Amount exceeds the outstanding balance of " + balanceDue);
        }

        Payment payment = new Payment(invoice, request.amountPaid(), request.method(), request.reference());
        payment.setHospital(TenantScoping.currentHospitalReference(entityManager));
        paymentRepository.save(payment);
        payment.assignReceiptNumber();
        auditService.record(currentCashier(), "RECORD_PAYMENT", "hospital_payment", payment.getId(), ipAddress);

        refreshInvoiceTotals(invoice);

        return new PaymentResponse(payment.getReceiptNumber(), payment.getAmountPaid(), false, invoice.getStatus().name());
    }

    private InvoiceView toView(VisitInvoice invoice, BigDecimal amountPaid) {
        var items = invoiceItemRepository.findByInvoice(invoice).stream()
                .map(item -> new InvoiceView.Item(
                        item.getId(), item.getService().getName(), item.getQuantity(), item.getPrice(), item.getSubtotal()))
                .toList();
        var payments = paymentRepository.findByInvoice(invoice).stream()
                .map(payment -> new InvoiceView.PaymentRecord(
                        payment.getReceiptNumber(), payment.getAmountPaid(), payment.getMethod().name(), payment.getReference()))
                .toList();

        BigDecimal balanceDue = invoice.getTotalAmount().subtract(amountPaid);
        return new InvoiceView(
                invoice.getId(), invoice.getTotalAmount(), amountPaid, balanceDue, invoice.getStatus().name(), items, payments);
    }

    @Transactional(readOnly = true)
    public List<ServiceSummary> listServices() {
        return billableServiceRepository.findAllByOrderByNameAsc().stream().map(ServiceSummary::from).toList();
    }
}
