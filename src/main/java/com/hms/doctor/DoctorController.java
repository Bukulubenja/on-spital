package com.hms.doctor;

import com.hms.audit.ClientIp;
import com.hms.doctor.dto.DiagnosisRequest;
import com.hms.doctor.dto.DrugLookup;
import com.hms.doctor.dto.LabTestOrderRequest;
import com.hms.doctor.dto.LabTestOrderResponse;
import com.hms.doctor.dto.LabTestSummary;
import com.hms.doctor.dto.PrescriptionItemRequest;
import com.hms.doctor.dto.VisitStatusResponse;
import com.hms.doctor.dto.VitalsRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Direct analogue of the DOCTOR-only views in hospital/views.py acting on a
 * Visit. @PreAuthorize is repeated per method — see ReceptionController for
 * why.
 */
@RestController
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/api/visits/{id}/start")
    public VisitStatusResponse startConsultation(@PathVariable("id") Long visitId) {
        return doctorService.startConsultation(visitId);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/api/visits/{id}/vitals")
    public ResponseEntity<Void> recordVitals(@PathVariable("id") Long visitId, @Valid @RequestBody VitalsRequest request) {
        doctorService.recordVitals(visitId, request);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/api/visits/{id}/diagnosis")
    public ResponseEntity<Void> recordDiagnosis(
            @PathVariable("id") Long visitId, @Valid @RequestBody DiagnosisRequest request, HttpServletRequest httpRequest
    ) {
        doctorService.recordDiagnosis(visitId, request, ClientIp.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/api/visits/{id}/prescriptions")
    public ResponseEntity<Void> addPrescriptionItem(@PathVariable("id") Long visitId, @Valid @RequestBody PrescriptionItemRequest request) {
        doctorService.addPrescriptionItem(visitId, request);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/api/visits/{id}/lab-tests")
    public LabTestOrderResponse addLabTest(@PathVariable("id") Long visitId, @Valid @RequestBody LabTestOrderRequest request) {
        return doctorService.addLabTest(visitId, request.testId());
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/api/visits/{id}/complete")
    public VisitStatusResponse completeVisit(@PathVariable("id") Long visitId) {
        return doctorService.completeVisit(visitId);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @GetMapping("/api/drugs")
    public List<DrugLookup> listDrugs() {
        return doctorService.listDrugs();
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @GetMapping("/api/lab-tests")
    public List<LabTestSummary> listLabTests() {
        return doctorService.listLabTests();
    }
}
