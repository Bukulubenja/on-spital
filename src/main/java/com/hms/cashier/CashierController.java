package com.hms.cashier;

import com.hms.cashier.dto.InvoiceItemRequest;
import com.hms.cashier.dto.InvoiceView;
import com.hms.cashier.dto.PaymentRequest;
import com.hms.cashier.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Direct analogue of the CASHIER-only views in hospital/views.py acting on a Visit's invoice. */
@RestController
public class CashierController {

    private final CashierService cashierService;

    public CashierController(CashierService cashierService) {
        this.cashierService = cashierService;
    }

    @PreAuthorize("hasRole('CASHIER')")
    @GetMapping("/api/visits/{id}/invoice")
    public InvoiceView viewInvoice(@PathVariable("id") Long visitId) {
        return cashierService.viewInvoice(visitId);
    }

    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/api/visits/{id}/invoice-items")
    public ResponseEntity<Void> addInvoiceItem(@PathVariable("id") Long visitId, @Valid @RequestBody InvoiceItemRequest request) {
        cashierService.addInvoiceItem(visitId, request);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('CASHIER')")
    @PostMapping("/api/visits/{id}/payments")
    public PaymentResponse recordPayment(@PathVariable("id") Long visitId, @Valid @RequestBody PaymentRequest request) {
        return cashierService.recordPayment(visitId, request);
    }
}
