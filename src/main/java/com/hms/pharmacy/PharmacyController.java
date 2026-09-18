package com.hms.pharmacy;

import com.hms.audit.ClientIp;
import com.hms.pharmacy.dto.DispenseResponse;
import com.hms.pharmacy.dto.PrescriptionView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Direct analogue of the PHARMACIST-only views in hospital/views.py acting on a Visit's prescription. */
@RestController
public class PharmacyController {

    private final PharmacyService pharmacyService;

    public PharmacyController(PharmacyService pharmacyService) {
        this.pharmacyService = pharmacyService;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @PostMapping("/api/visits/{id}/prescription-items/{itemId}/dispense")
    public DispenseResponse dispense(
            @PathVariable("id") Long visitId, @PathVariable("itemId") Long itemId, HttpServletRequest httpRequest
    ) {
        return pharmacyService.dispensePrescriptionItem(visitId, itemId, ClientIp.from(httpRequest));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @GetMapping("/api/visits/{id}/prescription")
    public PrescriptionView viewPrescription(@PathVariable("id") Long visitId) {
        return pharmacyService.viewPrescription(visitId);
    }
}
