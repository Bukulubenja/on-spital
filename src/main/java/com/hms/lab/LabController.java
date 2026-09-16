package com.hms.lab;

import com.hms.lab.dto.LabOrderView;
import com.hms.lab.dto.LabResultRequest;
import com.hms.lab.dto.LabResultResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Direct analogue of the LAB-only views in hospital/views.py acting on a Visit's lab order. */
@RestController
public class LabController {

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    @PreAuthorize("hasRole('LAB')")
    @PostMapping("/api/visits/{id}/lab-order-items/{itemId}/result")
    public LabResultResponse recordResult(
            @PathVariable("id") Long visitId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody LabResultRequest request
    ) {
        return labService.recordLabResult(visitId, itemId, request);
    }

    @PreAuthorize("hasRole('LAB')")
    @GetMapping("/api/visits/{id}/lab-order")
    public LabOrderView viewLabOrder(@PathVariable("id") Long visitId) {
        return labService.viewLabOrder(visitId);
    }
}
