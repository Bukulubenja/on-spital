package com.hms.nurse;

import com.hms.doctor.dto.VitalsRequest;
import com.hms.nurse.dto.NurseQueueEntry;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Direct analogue of the NURSE-only views in hospital/views.py. */
@RestController
public class NurseController {

    private final NurseService nurseService;

    public NurseController(NurseService nurseService) {
        this.nurseService = nurseService;
    }

    @PreAuthorize("hasRole('NURSE')")
    @GetMapping("/api/nurse/queue")
    public List<NurseQueueEntry> triageQueue() {
        return nurseService.triageQueue();
    }

    @PreAuthorize("hasRole('NURSE')")
    @PostMapping("/api/visits/{id}/nurse-vitals")
    public ResponseEntity<Void> recordVitals(@PathVariable("id") Long visitId, @Valid @RequestBody VitalsRequest request) {
        nurseService.recordVitals(visitId, request);
        return ResponseEntity.noContent().build();
    }
}
